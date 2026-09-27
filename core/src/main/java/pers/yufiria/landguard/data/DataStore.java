package pers.yufiria.landguard.data;

import crypticlib.CrypticLib;
import crypticlib.CrypticLibPlugin;
import crypticlib.database.connection.ConnectionSource;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.LifecycleOrder;
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
 * mutation 通过 {@link #reload} 声明本次真正改动的组件，只重读这些组件（大表可按领地范围重读），
 * 未迁移的写点继续调用 {@link #rebuildSnapshot()} 走全量重读，语义完全一致。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, isAsync = true, priority = LifecycleOrder.SNAPSHOT),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, isAsync = true, priority = LifecycleOrder.SNAPSHOT),
        @LifecycleSchedule(phase = LifecyclePhase.DISABLE, priority = LifecycleOrder.SNAPSHOT)
    }
)
public enum DataStore implements LifecycleTask {

    INSTANCE;

    private static final AtomicInteger WRITER_THREAD_INDEX = new AtomicInteger();

    /** 当前写线程正在执行的 mutation 上下文；写线程之外为 null。 */
    private static final ThreadLocal<WriteContext> ACTIVE_CONTEXT = new ThreadLocal<>();

    private volatile DataSnapshot snapshot = DataSnapshot.empty();
    private volatile CompletableFuture<DataSnapshot> reloadFuture = CompletableFuture.completedFuture(DataSnapshot.empty());
    private volatile boolean disabled;
    private ExecutorService writeExecutor;

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase phase) {
        switch (phase) {
            case ACTIVE, RELOAD -> {
                disabled = false;
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
     * 直接发布一个快照，语义等同写线程内的发布动作；供重载路径与测试注入使用。
     */
    public void publish(DataSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * mutation 落库后取新快照：写线程内按上下文增量重读，写线程之外（测试夹具、工具代码）全量重读。
     */
    public static DataSnapshot rebuildSnapshot() throws SQLException {
        WriteContext context = ACTIVE_CONTEXT.get();
        return context == null ? SnapshotLoader.load(null) : context.reloadAll();
    }

    /**
     * 声明本次改动涉及的组件并只重读它们，未声明的组件沿用旧值。只能在 mutation 内调用。
     */
    public static DataSnapshot reload(SnapshotPart first, SnapshotPart... more) throws SQLException {
        return context().reload(first, more);
    }

    /**
     * 只重读某个领地范围内的行。目前支持 {@link SnapshotPart#CLAIM}、{@link SnapshotPart#CLAIM_CHUNK}、
     * {@link SnapshotPart#ROLE_FLAG}、{@link SnapshotPart#CLAIM_SETTING}，用于避开大表的全表扫描。
     */
    public static DataSnapshot reloadScoped(SnapshotPart part, String claimId) throws SQLException {
        return context().reloadScoped(part, claimId);
    }

    /** 断言当前位于写线程的 mutation 内（只允许写线程访问的组件用它自检）。 */
    public static void assertWriteContext() {
        context();
    }

    private static WriteContext context() {
        WriteContext context = ACTIVE_CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("该操作只能在 DataStore.mutate 的 mutation 内（写线程）调用");
        }
        return context;
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
        if (disabled) {
            return CompletableFuture.failedFuture(new IllegalStateException("DataStore 已关闭，拒绝重载"));
        }
        CompletableFuture<DataSnapshot> future = CompletableFuture.supplyAsync(() -> {
            try {
                DataSnapshot loaded = SnapshotLoader.load(connectionSource);
                publish(loaded);
                return loaded;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load LandGuard data snapshot", e);
            }
        }, ensureExecutor());
        reloadFuture = future;
        return future;
    }

    /**
     * 串行提交一次数据变更：mutation 在单写线程内完成 DAO 写入，返回的组件重新加载后原子发布。
     */
    public CompletableFuture<DataSnapshot> mutate(DataMutation mutation) {
        if (disabled) {
            return CompletableFuture.failedFuture(new IllegalStateException("DataStore 已关闭，拒绝新的数据变更"));
        }
        return CompletableFuture.supplyAsync(() -> {
            WriteContext previous = ACTIVE_CONTEXT.get();
            WriteContext context = new WriteContext(snapshot);
            ACTIVE_CONTEXT.set(context);
            try {
                mutation.mutate(context.current());
                DataSnapshot next = context.publish();
                publish(next);
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
            } finally {
                // SPI 回调等场景可能嵌套 mutate，恢复外层上下文而不是直接清空
                if (previous == null) {
                    ACTIVE_CONTEXT.remove();
                } else {
                    ACTIVE_CONTEXT.set(previous);
                }
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
        disabled = true;
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
     * 读取数据库并组装不可变快照。集群/重载线程与写线程都可能调用。
     */
    public static final class SnapshotLoader {

        private SnapshotLoader() {
        }

        public static DataSnapshot load(ConnectionSource ignored) throws SQLException {
            return load(SnapshotPart.all(), null);
        }

        /**
         * 按组件加载：未在 parts 中的组件按引用复用 base（base 为 null 时表示全量加载）。
         */
        public static DataSnapshot load(EnumSet<SnapshotPart> parts, @Nullable DataSnapshot base) throws SQLException {
            LandDaoManager daos = LandDaoManager.INSTANCE;
            boolean full = base == null;

            Map<String, ClaimData> claimsById = full || parts.contains(SnapshotPart.CLAIM)
                ? loadClaims(daos) : base.claimsById();
            Map<ChunkLoc, String> claimIdByChunk;
            Map<String, Set<ChunkLoc>> chunksByClaim;
            if (full || parts.contains(SnapshotPart.CLAIM_CHUNK)) {
                claimIdByChunk = new LinkedHashMap<>();
                chunksByClaim = new LinkedHashMap<>();
                for (ClaimChunkData chunk : daos.claimChunkDao().queryForAll()) {
                    ChunkLoc loc = ChunkLoc.of(chunk.getWorldUuid(), chunk.getChunkX(), chunk.getChunkZ());
                    claimIdByChunk.put(loc, chunk.getClaimId());
                    chunksByClaim.computeIfAbsent(chunk.getClaimId(), k -> new LinkedHashSet<>()).add(loc);
                }
            } else {
                claimIdByChunk = base.claimIdByChunk();
                chunksByClaim = base.chunksByClaim();
            }
            Map<OwnerRef, Set<String>> claimsByOwner = full || parts.contains(SnapshotPart.CLAIM)
                ? deriveClaimsByOwner(claimsById) : base.claimsByOwner();
            Map<String, Map<String, Map<String, Boolean>>> roleFlagsByClaim =
                full || parts.contains(SnapshotPart.ROLE_FLAG) ? loadRoleFlags(daos) : base.roleFlagsByClaim();
            Map<String, Map<String, String>> settingsByClaim =
                full || parts.contains(SnapshotPart.CLAIM_SETTING) ? loadSettings(daos) : base.settingsByClaim();
            Map<UUID, PlayerData> players =
                full || parts.contains(SnapshotPart.PLAYER) ? loadPlayers(daos) : base.players();
            Map<String, GroupData> groups =
                full || parts.contains(SnapshotPart.GROUP) ? loadGroups(daos) : base.groups();
            Map<String, Map<UUID, String>> groupMembers =
                full || parts.contains(SnapshotPart.GROUP_MEMBER) ? loadGroupMembers(daos) : base.groupMembers();

            return new DataSnapshot(
                claimsById,
                claimIdByChunk,
                chunksByClaim,
                claimsByOwner,
                roleFlagsByClaim,
                settingsByClaim,
                players,
                groups,
                groupMembers
            );
        }

        /**
         * 只重读指定领地范围内的行：摘掉该领地旧值再写入新值，其余组件沿用 base。
         */
        public static DataSnapshot loadScoped(SnapshotPart part, String claimId, DataSnapshot base) throws SQLException {
            LandDaoManager daos = LandDaoManager.INSTANCE;
            switch (part) {
                case CLAIM -> {
                    ClaimData claim = daos.claimDao().queryForId(claimId);
                    Map<String, ClaimData> claims = new LinkedHashMap<>(base.claimsById());
                    if (claim == null) {
                        claims.remove(claimId);
                    } else {
                        claims.put(claimId, claim);
                    }
                    return base.withClaims(claims, deriveClaimsByOwner(claims));
                }
                case CLAIM_CHUNK -> {
                    Map<String, Set<ChunkLoc>> chunks = new LinkedHashMap<>(base.chunksByClaim());
                    Map<ChunkLoc, String> idByChunk = new LinkedHashMap<>(base.claimIdByChunk());
                    for (ChunkLoc previous : base.chunksByClaim().getOrDefault(claimId, Set.of())) {
                        idByChunk.remove(previous);
                    }
                    Set<ChunkLoc> current = new LinkedHashSet<>();
                    for (ClaimChunkData chunk : daos.claimChunkDao().queryBuilder()
                        .where(w -> w.equals("claim_id", claimId)).query()) {
                        ChunkLoc loc = ChunkLoc.of(chunk.getWorldUuid(), chunk.getChunkX(), chunk.getChunkZ());
                        current.add(loc);
                        idByChunk.put(loc, claimId);
                    }
                    if (current.isEmpty()) {
                        chunks.remove(claimId);
                    } else {
                        chunks.put(claimId, current);
                    }
                    return base.withChunkIndex(idByChunk, chunks);
                }
                case ROLE_FLAG -> {
                    Map<String, Map<String, Map<String, Boolean>>> flags = new LinkedHashMap<>(base.roleFlagsByClaim());
                    Map<String, Map<String, Boolean>> byRole = new LinkedHashMap<>();
                    for (ClaimRoleFlagData row : daos.roleFlagDao().queryBuilder()
                        .where(w -> w.equals("claim_id", claimId)).query()) {
                        byRole.computeIfAbsent(row.getRoleId(), k -> new LinkedHashMap<>())
                            .put(row.getFlagKey(), row.isValue());
                    }
                    if (byRole.isEmpty()) {
                        flags.remove(claimId);
                    } else {
                        flags.put(claimId, byRole);
                    }
                    return base.withRoleFlags(flags);
                }
                case CLAIM_SETTING -> {
                    Map<String, Map<String, String>> settings = new LinkedHashMap<>(base.settingsByClaim());
                    Map<String, String> values = new LinkedHashMap<>();
                    for (ClaimSettingData row : daos.claimSettingDao().queryBuilder()
                        .where(w -> w.equals("claim_id", claimId)).query()) {
                        values.put(row.getSettingKey(), row.getSettingValue());
                    }
                    if (values.isEmpty()) {
                        settings.remove(claimId);
                    } else {
                        settings.put(claimId, values);
                    }
                    return base.withSettings(settings);
                }
                default -> throw new UnsupportedOperationException("该组件不支持按领地范围重读: " + part);
            }
        }

        private static Map<String, ClaimData> loadClaims(LandDaoManager daos) throws SQLException {
            Map<String, ClaimData> claimsById = new LinkedHashMap<>();
            for (ClaimData claim : daos.claimDao().queryForAll()) {
                claimsById.put(claim.getClaimId(), claim);
            }
            return claimsById;
        }

        private static Map<String, Map<String, Map<String, Boolean>>> loadRoleFlags(LandDaoManager daos) throws SQLException {
            Map<String, Map<String, Map<String, Boolean>>> roleFlagsByClaim = new LinkedHashMap<>();
            for (ClaimRoleFlagData row : daos.roleFlagDao().queryForAll()) {
                roleFlagsByClaim
                    .computeIfAbsent(row.getClaimId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getRoleId(), k -> new LinkedHashMap<>())
                    .put(row.getFlagKey(), row.isValue());
            }
            return roleFlagsByClaim;
        }

        private static Map<String, Map<String, String>> loadSettings(LandDaoManager daos) throws SQLException {
            Map<String, Map<String, String>> settingsByClaim = new LinkedHashMap<>();
            for (ClaimSettingData row : daos.claimSettingDao().queryForAll()) {
                settingsByClaim
                    .computeIfAbsent(row.getClaimId(), k -> new LinkedHashMap<>())
                    .put(row.getSettingKey(), row.getSettingValue());
            }
            return settingsByClaim;
        }

        private static Map<UUID, PlayerData> loadPlayers(LandDaoManager daos) throws SQLException {
            Map<UUID, PlayerData> players = new LinkedHashMap<>();
            for (PlayerData player : daos.playerDao().queryForAll()) {
                players.put(player.getPlayerUuid(), player);
            }
            return players;
        }

        private static Map<String, GroupData> loadGroups(LandDaoManager daos) throws SQLException {
            Map<String, GroupData> groups = new LinkedHashMap<>();
            for (GroupData group : daos.groupDao().queryForAll()) {
                groups.put(group.getGroupId(), group);
            }
            return groups;
        }

        private static Map<String, Map<UUID, String>> loadGroupMembers(LandDaoManager daos) throws SQLException {
            Map<String, Map<UUID, String>> groupMembers = new LinkedHashMap<>();
            for (GroupMemberData row : daos.groupMemberDao().queryForAll()) {
                groupMembers
                    .computeIfAbsent(row.getGroupId(), k -> new LinkedHashMap<>())
                    .put(row.getMemberUuid(), row.getRoleId());
            }
            return groupMembers;
        }

        private static Map<OwnerRef, Set<String>> deriveClaimsByOwner(Map<String, ClaimData> claimsById) {
            Map<OwnerRef, Set<String>> claimsByOwner = new LinkedHashMap<>();
            for (ClaimData claim : claimsById.values()) {
                claimsByOwner
                    .computeIfAbsent(OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()), k -> new LinkedHashSet<>())
                    .add(claim.getClaimId());
            }
            return claimsByOwner;
        }

    }

}