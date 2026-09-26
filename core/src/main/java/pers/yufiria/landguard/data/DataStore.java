package pers.yufiria.landguard.data;

import crypticlib.CrypticLib;
import crypticlib.CrypticLibPlugin;
import crypticlib.database.connection.ConnectionSource;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.database.DataSourceManager;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.*;
import pers.yufiria.landguard.owner.OwnerRef;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存数据仓库。
 * 读路径：volatile {@link DataSnapshot} 无锁访问，永不为 null。
 * 写路径：所有变更通过单写线程串行执行（DAO 持久化 + 生成新快照），杜绝丢失更新。
 * 生命周期优先级 0：在 DataSourceManager(-2) 建连、LandDaoManager(-1) 建表之后运行。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, isAsync = true, priority = 0),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, isAsync = true, priority = 0),
        @LifecycleSchedule(phase = LifecyclePhase.DISABLE, priority = 0)
    }
)
public enum DataStore implements LifecycleTask {

    INSTANCE;

    private static final AtomicInteger WRITER_THREAD_INDEX = new AtomicInteger();

    private volatile DataSnapshot snapshot = DataSnapshot.empty();
    private volatile CompletableFuture<DataSnapshot> reloadFuture = CompletableFuture.completedFuture(DataSnapshot.empty());
    private ExecutorService writeExecutor;

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase phase) {
        switch (phase) {
            case ACTIVE, RELOAD -> {
                ensureExecutor();
                reloadFrom(DataSourceManager.INSTANCE.databaseConnection()).join();
            }
            case DISABLE -> shutdown();
        }
    }

    public @NotNull DataSnapshot snapshot() {
        return snapshot;
    }

    /**
     * 从当前全局 DAO 全量重建快照。供写线程内的业务 mutation 在落库后发布新快照使用；
     * 不经过 DataSourceManager 生命周期，因此也可在直接注入连接的测试夹具中使用。
     */
    public static DataSnapshot rebuildSnapshot() throws SQLException {
        return SnapshotLoader.load(null);
    }

    public CompletableFuture<DataSnapshot> reloadFuture() {
        return reloadFuture;
    }

    public void joinReload() {
        reloadFuture.join();
    }

    /**
     * 从数据库全量重建快照并原子发布。可在写线程之外（仅重载）调用；
     * 并发调用时每次发布的都是完整不可变快照，读侧不会观察到中间态。
     */
    public CompletableFuture<DataSnapshot> reloadFrom(ConnectionSource connectionSource) {
        CompletableFuture<DataSnapshot> future = CompletableFuture.supplyAsync(() -> {
            try {
                DataSnapshot loaded = SnapshotLoader.load(connectionSource);
                snapshot = loaded;
                return loaded;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load LandGuard data snapshot", e);
            }
        }, ensureExecutor());
        reloadFuture = future;
        return future;
    }

    /**
     * 串行提交一次数据变更：mutation 在单写线程内完成 DAO 写入并返回新快照。
     */
    public CompletableFuture<DataSnapshot> mutate(DataMutation mutation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DataSnapshot next = mutation.mutate(snapshot);
                snapshot = next;
                return next;
            } catch (Exception e) {
                try {
                    CrypticLib.info("&cFailed to mutate LandGuard data: " + e.getMessage());
                } catch (Throwable platformUnavailable) {
                    // 单元测试等无平台环境下 CrypticLib 无法初始化；回退标准错误流，绝不掩盖根因
                    System.err.println("[LandGuard] Failed to mutate data: " + e.getMessage());
                    e.printStackTrace();
                }
                throw new RuntimeException(e);
            }
        }, ensureExecutor());
    }

    private ExecutorService ensureExecutor() {
        ExecutorService executor = writeExecutor;
        if (executor == null || executor.isShutdown()) {
            synchronized (this) {
                executor = writeExecutor;
                if (executor == null || executor.isShutdown()) {
                    executor = Executors.newSingleThreadExecutor(r -> {
                        Thread thread = new Thread(r, "LandGuard-DB-Writer-" + WRITER_THREAD_INDEX.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    });
                    writeExecutor = executor;
                }
            }
        }
        return executor;
    }

    private void shutdown() {
        ExecutorService executor = writeExecutor;
        if (executor != null) {
            executor.shutdown();
        }
        writeExecutor = null;
    }

    @FunctionalInterface
    public interface DataMutation {

        DataSnapshot mutate(DataSnapshot current) throws Exception;

    }

    /**
     * 全量读取所有表并组装不可变快照。仅在写/重载线程调用。
     */
    public static final class SnapshotLoader {

        private SnapshotLoader() {
        }

        public static DataSnapshot load(ConnectionSource ignored) throws SQLException {
            LandDaoManager daos = LandDaoManager.INSTANCE;

            Map<String, ClaimData> claimsById = new LinkedHashMap<>();
            for (ClaimData claim : daos.claimDao().queryForAll()) {
                claimsById.put(claim.getClaimId(), claim);
            }

            Map<ChunkLoc, String> claimIdByChunk = new LinkedHashMap<>();
            Map<String, Set<ChunkLoc>> chunksByClaim = new LinkedHashMap<>();
            for (ClaimChunkData chunk : daos.claimChunkDao().queryForAll()) {
                ChunkLoc loc = ChunkLoc.of(chunk.getWorldUuid(), chunk.getChunkX(), chunk.getChunkZ());
                claimIdByChunk.put(loc, chunk.getClaimId());
                chunksByClaim.computeIfAbsent(chunk.getClaimId(), k -> new LinkedHashSet<>()).add(loc);
            }

            Map<OwnerRef, Set<String>> claimsByOwner = new LinkedHashMap<>();
            for (ClaimData claim : claimsById.values()) {
                claimsByOwner
                    .computeIfAbsent(OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()), k -> new LinkedHashSet<>())
                    .add(claim.getClaimId());
            }

            Map<String, Map<String, Map<String, Boolean>>> roleFlagsByClaim = new LinkedHashMap<>();
            for (ClaimRoleFlagData row : daos.roleFlagDao().queryForAll()) {
                roleFlagsByClaim
                    .computeIfAbsent(row.getClaimId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getRoleId(), k -> new LinkedHashMap<>())
                    .put(row.getFlagKey(), row.isValue());
            }

            Map<String, Map<String, String>> settingsByClaim = new LinkedHashMap<>();
            for (ClaimSettingData row : daos.claimSettingDao().queryForAll()) {
                settingsByClaim
                    .computeIfAbsent(row.getClaimId(), k -> new LinkedHashMap<>())
                    .put(row.getSettingKey(), row.getSettingValue());
            }

            Map<UUID, PlayerData> players = new LinkedHashMap<>();
            for (PlayerData player : daos.playerDao().queryForAll()) {
                players.put(player.getPlayerUuid(), player);
            }

            Map<String, GroupData> groups = new LinkedHashMap<>();
            for (GroupData group : daos.groupDao().queryForAll()) {
                groups.put(group.getGroupId(), group);
            }

            Map<String, Map<UUID, String>> groupMembers = new LinkedHashMap<>();
            for (GroupMemberData row : daos.groupMemberDao().queryForAll()) {
                groupMembers
                    .computeIfAbsent(row.getGroupId(), k -> new LinkedHashMap<>())
                    .put(row.getMemberUuid(), row.getRoleId());
            }

            Map<String, Map<String, GroupRoleData>> groupRoles = new LinkedHashMap<>();
            for (GroupRoleData row : daos.groupRoleDao().queryForAll()) {
                groupRoles
                    .computeIfAbsent(row.getGroupId(), k -> new LinkedHashMap<>())
                    .put(row.getRoleId(), row);
            }

            return new DataSnapshot(
                claimsById,
                claimIdByChunk,
                chunksByClaim,
                claimsByOwner,
                roleFlagsByClaim,
                settingsByClaim,
                players,
                groups,
                groupMembers,
                groupRoles
            );
        }

    }

}
