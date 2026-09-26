package pers.yufiria.landguard.claim;

import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.database.entity.PlayerQuotaData;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.*;
import pers.yufiria.landguard.owner.builtin.server.ServerClaimOwner;
import pers.yufiria.landguard.util.ConfigValues;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 认领/放弃领域服务。
 * 所有校验与落库都封装为 {@link DataStore#mutate} 任务，在单写线程内原子完成：
 * 调用方永远不会观察到“校验通过但写入一半”的中间态。本类不做 Bukkit 调度，可在测试中直接驱动。
 */
public enum ClaimService {

    INSTANCE;

    private static final long UNLIMITED = Long.MAX_VALUE;

    /** 领地名长度上限，与用户组名上限保持一致。 */
    private static final int MAX_CLAIM_NAME_LENGTH = 32;

    /**
     * 认领一批区块（首个领地自动创建，同世界已有领地则扩容到该领地）。
     * 已属于任何领地（含操作者本人）的目标区块会被跳过，只认领剩余部分；
     * 若全部目标都已被占用，则按 {@link ClaimFailureReason#OVERLAP} 失败。
     *
     * @param defaultName 新建领地的默认名称（调用方在主线程用玩家名生成）
     * @param admin       管理领地（server 虚拟所有者）：跳过相邻与额度限制
     */
    public CompletableFuture<ClaimOpResult> claim(OwnerRef owner, UUID world, List<ChunkLoc> rawTargets,
                                                  String defaultName, boolean admin) {
        // 管理领地统一归属虚拟 server 实体：调用方传入的 owner 仅用于普通认领
        OwnerRef effectiveOwner = admin
            ? OwnerRef.of(BuiltinOwnerTypes.SERVER, ServerClaimOwner.ID)
            : owner;
        AtomicReference<ClaimOpResult> resultRef = new AtomicReference<>();
        return DataStore.INSTANCE.mutate(current -> {
            List<ChunkLoc> raw = ClaimEngine.normalizeTargets(world, rawTargets);
            if (raw == null) {
                resultRef.set(ClaimOpResult.failed(ClaimFailureReason.INVALID_TARGETS));
                return current;
            }
            // 已被占用的区块（无论归属谁）在本批次内跳过，只对剩余区块做校验与落库
            List<ChunkLoc> targets = new ArrayList<>(raw.size());
            for (ChunkLoc target : raw) {
                if (!current.claimIdByChunk().containsKey(target)) {
                    targets.add(target);
                }
            }
            int skipped = raw.size() - targets.size();
            if (targets.isEmpty()) {
                resultRef.set(ClaimOpResult.failed(ClaimFailureReason.OVERLAP));
                return current;
            }
            Set<ChunkLoc> owned = ClaimEngine.ownedChunksInWorld(current, effectiveOwner, world);

            long capacity = UNLIMITED;
            long usedChunks = ClaimEngine.currentClaimedChunks(current, owner);
            PlayerQuotaData quota = null;
            UUID playerUuid = null;
            if (!admin && owner.typeKey().equals(BuiltinOwnerTypes.PLAYER)) {
                playerUuid = parseUuid(owner.identifier());
                if (playerUuid == null) {
                    resultRef.set(ClaimOpResult.failed(ClaimFailureReason.INVALID_TARGETS));
                    return current;
                }
                quota = loadOrCreateQuota(playerUuid);
                usedChunks = Math.max(quota.getUsedChunks(), ClaimEngine.currentClaimedChunks(current, owner));
                PlayerData playerData = LandDaoManager.INSTANCE.playerDao().queryForId(playerUuid);
                capacity = capacityOf(playerData);
            } else if (!admin && owner.typeKey().equals(BuiltinOwnerTypes.GROUP)) {
                if (!current.groups().containsKey(owner.identifier())) {
                    resultRef.set(ClaimOpResult.failed(ClaimFailureReason.INVALID_TARGETS));
                    return current;
                }
                capacity = GroupService.INSTANCE.groupCapacity(current, owner.identifier());
                // 组已用区块完全由快照派生（无独立额度表）
            }

            ClaimFailureReason reason = ClaimEngine.validateClaim(
                current,
                targets,
                owned,
                !admin && ConfigValues.get(ClaimConfigs.REQUIRE_ADJACENT),
                ConfigValues.get(ClaimConfigs.ALLOW_DIAGONAL_ADJACENT),
                capacity,
                usedChunks
            );
            if (reason != null) {
                resultRef.set(ClaimOpResult.failed(reason));
                return current;
            }

            LandDaoManager daos = LandDaoManager.INSTANCE;
            long now = System.currentTimeMillis();
            String claimId = findClaimInWorld(current, effectiveOwner, world);
            if (claimId == null) {
                claimId = UUID.randomUUID().toString();
                daos.claimDao().create(new ClaimData(claimId, world,
                    effectiveOwner.typeKey(), effectiveOwner.identifier(),
                    defaultName, admin, now, now, 0D, false));
            }
            for (ChunkLoc target : targets) {
                daos.claimChunkDao().create(new ClaimChunkData(claimId, world, target.x(), target.z()));
            }
            if (quota != null) {
                quota.setUsedChunks((int) Math.min(Integer.MAX_VALUE, usedChunks + targets.size()));
                daos.playerQuotaDao().update(quota);
            }

            DataSnapshot next = DataStore.rebuildSnapshot();
            long available;
            if (admin) {
                available = UNLIMITED;
            } else if (playerUuid != null) {
                available = availableChunks(playerUuid, next);
            } else if (owner.typeKey().equals(BuiltinOwnerTypes.GROUP)) {
                available = Math.max(0L,
                    GroupService.INSTANCE.groupCapacity(next, owner.identifier())
                        - ClaimEngine.currentClaimedChunks(next, owner));
            } else {
                available = UNLIMITED;
            }
            resultRef.set(ClaimOpResult.claimed(claimId, targets.size(), skipped, available));
            return next;
        }).thenApply(snapshot -> resultRef.get());
    }

    /**
     * 放弃区块；任一个目标不属于操作者则整批拒绝。最后一个区块被放弃时领地连同其 flag/设置一起删除。
     */
    public CompletableFuture<ClaimOpResult> unclaim(OwnerRef owner, List<ChunkLoc> rawTargets) {
        AtomicReference<ClaimOpResult> resultRef = new AtomicReference<>();
        return DataStore.INSTANCE.mutate(current -> {
            UUID world = rawTargets.isEmpty() ? null : rawTargets.get(0).worldUuid();
            List<ChunkLoc> targets = ClaimEngine.normalizeTargets(world, rawTargets);
            if (targets == null) {
                resultRef.set(ClaimOpResult.failed(ClaimFailureReason.INVALID_TARGETS));
                return current;
            }

            Map<String, List<ChunkLoc>> byClaim = new LinkedHashMap<>();
            for (ChunkLoc target : targets) {
                String claimId = current.claimIdByChunk().get(target);
                if (claimId == null) {
                    resultRef.set(ClaimOpResult.failed(ClaimFailureReason.NOT_CLAIMED));
                    return current;
                }
                ClaimData claim = current.claimsById().get(claimId);
                if (claim == null || !owner.typeKey().equals(claim.getOwnerType())
                    || !owner.identifier().equals(claim.getOwnerId())) {
                    resultRef.set(ClaimOpResult.failed(ClaimFailureReason.NOT_OWNER));
                    return current;
                }
                byClaim.computeIfAbsent(claimId, k -> new ArrayList<>()).add(target);
            }

            LandDaoManager daos = LandDaoManager.INSTANCE;
            for (Map.Entry<String, List<ChunkLoc>> entry : byClaim.entrySet()) {
                String claimId = entry.getKey();
                for (ChunkLoc target : entry.getValue()) {
                    var delete = daos.claimChunkDao().deleteBuilder();
                    delete.where(w -> w
                        .equals("claim_id", claimId)
                        .and().equals("world_uuid", target.worldUuid())
                        .and().equals("chunk_x", target.x())
                        .and().equals("chunk_z", target.z()));
                    delete.delete();
                }
                Set<ChunkLoc> remaining = current.chunksByClaim().getOrDefault(claimId, Set.of());
                boolean emptied = remaining.size() == entry.getValue().size()
                    && remaining.containsAll(entry.getValue());
                if (emptied) {
                    daos.claimDao().delete(current.claimsById().get(claimId));
                    var flagDelete = daos.roleFlagDao().deleteBuilder();
                    flagDelete.where(w -> w.equals("claim_id", claimId));
                    flagDelete.delete();
                    var settingDelete = daos.claimSettingDao().deleteBuilder();
                    settingDelete.where(w -> w.equals("claim_id", claimId));
                    settingDelete.delete();
                }
            }

            int refunded = 0;
            UUID playerUuid = owner.typeKey().equals(BuiltinOwnerTypes.PLAYER) ? parseUuid(owner.identifier()) : null;
            if (playerUuid != null) {
                PlayerQuotaData quota = loadOrCreateQuota(playerUuid);
                double ratio = Math.max(0D, Math.min(1D, ConfigValues.get(ClaimConfigs.UNCLAIM_RETURN_RATIO)));
                refunded = (int) Math.floor(targets.size() * ratio);
                int remainingClaimed = Math.max(0, ClaimEngine.currentClaimedChunks(current, owner) - targets.size());
                quota.setUsedChunks(Math.max(remainingClaimed, quota.getUsedChunks() - refunded));
                daos.playerQuotaDao().update(quota);
            }

            DataSnapshot next = DataStore.rebuildSnapshot();
            long available;
            if (playerUuid != null) {
                available = availableChunks(playerUuid, next);
            } else if (owner.typeKey().equals(BuiltinOwnerTypes.GROUP)) {
                available = Math.max(0L,
                    GroupService.INSTANCE.groupCapacity(next, owner.identifier())
                        - ClaimEngine.currentClaimedChunks(next, owner));
            } else {
                available = UNLIMITED;
            }
            String claimId = byClaim.size() == 1 ? byClaim.keySet().iterator().next() : null;
            resultRef.set(ClaimOpResult.unclaimed(claimId, targets.size(), refunded, available));
            return next;
        }).thenApply(snapshot -> resultRef.get());
    }

    /**
     * 玩家自助放弃单个区块：授权按「领地真实所有者 + 操作者在其中的角色」判定
     * （个人领地＝本人，用户组领地＝组内 {@link Roles#OWNER}），因此组领袖可直接放弃组领地。
     * 授权用已发布快照在进入写线程前判定，写线程内仍由 {@link #unclaim} 复核归属，避免竞态。
     */
    public CompletableFuture<ClaimOpResult> unclaimOwnedBy(UUID actor, ChunkLoc target) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        String claimId = snapshot.claimIdByChunk().get(target);
        ClaimData claim = claimId == null ? null : snapshot.claimsById().get(claimId);
        if (claim == null) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.NOT_CLAIMED));
        }
        if (!isOwner(claim, actor)) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.NOT_OWNER));
        }
        return unclaim(OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()), List.of(target));
    }

    /**
     * 管理员强制放弃：不校验归属，不按返还比例结算，直接删除指定区块；
     * 最后一个区块被删时整领连同 flag/设置一起删除。
     * 个人原所有者的已用额度按实际持有量校正（系统回收不占额度，也不额外赠送）。
     */
    public CompletableFuture<ClaimOpResult> adminUnclaim(List<ChunkLoc> rawTargets) {
        AtomicReference<ClaimOpResult> resultRef = new AtomicReference<>();
        return DataStore.INSTANCE.mutate(current -> {
            UUID world = rawTargets.isEmpty() ? null : rawTargets.get(0).worldUuid();
            List<ChunkLoc> targets = ClaimEngine.normalizeTargets(world, rawTargets);
            if (targets == null) {
                resultRef.set(ClaimOpResult.failed(ClaimFailureReason.INVALID_TARGETS));
                return current;
            }

            // 受影响领地 -> 该领地下被删区块；目标无领主地整批拒绝
            Map<String, List<ChunkLoc>> byClaim = new LinkedHashMap<>();
            Map<String, ClaimData> affectedOwners = new LinkedHashMap<>();
            for (ChunkLoc target : targets) {
                String claimId = current.claimIdByChunk().get(target);
                if (claimId == null) {
                    resultRef.set(ClaimOpResult.failed(ClaimFailureReason.NOT_CLAIMED));
                    return current;
                }
                byClaim.computeIfAbsent(claimId, k -> new ArrayList<>()).add(target);
                affectedOwners.putIfAbsent(claimId, current.claimsById().get(claimId));
            }

            LandDaoManager daos = LandDaoManager.INSTANCE;
            for (Map.Entry<String, List<ChunkLoc>> entry : byClaim.entrySet()) {
                String claimId = entry.getKey();
                for (ChunkLoc target : entry.getValue()) {
                    var delete = daos.claimChunkDao().deleteBuilder();
                    delete.where(w -> w
                        .equals("claim_id", claimId)
                        .and().equals("world_uuid", target.worldUuid())
                        .and().equals("chunk_x", target.x())
                        .and().equals("chunk_z", target.z()));
                    delete.delete();
                }
                Set<ChunkLoc> remaining = current.chunksByClaim().getOrDefault(claimId, Set.of());
                boolean emptied = remaining.size() == entry.getValue().size()
                    && remaining.containsAll(entry.getValue());
                if (emptied) {
                    daos.claimDao().delete(current.claimsById().get(claimId));
                    var flagDelete = daos.roleFlagDao().deleteBuilder();
                    flagDelete.where(w -> w.equals("claim_id", claimId));
                    flagDelete.delete();
                    var settingDelete = daos.claimSettingDao().deleteBuilder();
                    settingDelete.where(w -> w.equals("claim_id", claimId));
                    settingDelete.delete();
                }
            }

            DataSnapshot next = DataStore.rebuildSnapshot();
            // 校正受影响个人所有者的已用额度：按其剩余实际持有量取值，系统操作不扣减/赠送额度
            for (ClaimData claim : affectedOwners.values()) {
                if (!BuiltinOwnerTypes.PLAYER.equals(claim.getOwnerType())) {
                    continue;
                }
                UUID ownerUuid = parseUuid(claim.getOwnerId());
                if (ownerUuid == null) {
                    continue;
                }
                PlayerQuotaData quota = daos.playerQuotaDao().queryForId(ownerUuid);
                if (quota == null) {
                    continue;
                }
                int actual = ClaimEngine.currentClaimedChunks(
                    next, OwnerRef.of(BuiltinOwnerTypes.PLAYER, ownerUuid.toString()));
                quota.setUsedChunks(Math.max(actual, 0));
                daos.playerQuotaDao().update(quota);
            }

            String claimId = byClaim.size() == 1 ? byClaim.keySet().iterator().next() : null;
            resultRef.set(ClaimOpResult.unclaimed(claimId, targets.size(), 0, UNLIMITED));
            return next;
        }).thenApply(snapshot -> resultRef.get());
    }

    // ================= 领地改名 =================

    /** 去掉首尾空白；null 原样返回，合法性由 {@link #isValidClaimName} 判断。 */
    public static String normalizeClaimName(String raw) {
        return raw == null ? null : raw.trim();
    }

    /** 领地名必须非空且不超过 {@value #MAX_CLAIM_NAME_LENGTH} 字符；允许与其他领地重名。 */
    public static boolean isValidClaimName(String name) {
        return name != null && !name.isEmpty() && name.length() <= MAX_CLAIM_NAME_LENGTH;
    }

    /**
     * 判断某玩家是否是这块领地的 owner：个人领地=本人，用户组领地=owner（领袖）角色。
     * 命令层预检与 GUI 按钮显隐共用本方法。
     */
    public static boolean isOwner(ClaimData claim, UUID player) {
        if (claim == null || player == null) {
            return false;
        }
        ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(
            OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()));
        return owner != null && Roles.OWNER.equals(owner.roleOf(player));
    }

    /**
     * 重命名领地：仅该领地的 owner 可操作（授权在进入写线程前用已发布快照判定，
     * 避免在单写线程内解析用户组角色造成嵌套写入）。
     */
    public CompletableFuture<ClaimOpResult> renameClaim(UUID actor, String claimId, String newName) {
        ClaimData claim = DataStore.INSTANCE.snapshot().claimsById().get(claimId);
        if (claim == null) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.NOT_CLAIMED));
        }
        if (!isOwner(claim, actor)) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.NOT_OWNER));
        }
        String name = normalizeClaimName(newName);
        if (!isValidClaimName(name)) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.INVALID_NAME));
        }
        AtomicReference<ClaimOpResult> resultRef = new AtomicReference<>();
        return DataStore.INSTANCE.mutate(current -> {
            ClaimData fresh = LandDaoManager.INSTANCE.claimDao().queryForId(claimId);
            if (fresh == null) {
                resultRef.set(ClaimOpResult.failed(ClaimFailureReason.NOT_CLAIMED));
                return current;
            }
            fresh.setName(name);
            LandDaoManager.INSTANCE.claimDao().update(fresh);
            resultRef.set(ClaimOpResult.renamed(claimId));
            return DataStore.rebuildSnapshot();
        }).thenApply(snapshot -> resultRef.get());
    }

    // ================= 领地转让 =================

    /**
     * 玩家自助把脚下领地转让给另一个玩家：仅该领地的 owner 可操作（个人领地=本人，用户组领地=领袖）。
     * 状态处理与管理员强制转让一致：admin 标记/豁免/欠费/警告/孤儿状态重置，lastActiveAt 刷新，
     * 领地银行余额随领地保留；双方已用额度按实际持有量校正（允许目标暂时超出容量）。
     * 额外拒绝会破坏「同一所有者在每个世界最多一块领地」约束的目标玩家。
     */
    public CompletableFuture<ClaimOpResult> transferClaim(UUID actor, String claimId, UUID target) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim == null) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.NOT_CLAIMED));
        }
        if (!isOwner(claim, actor)) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.NOT_OWNER));
        }
        if (BuiltinOwnerTypes.PLAYER.equals(claim.getOwnerType())
            && claim.getOwnerId().equals(target.toString())) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.ALREADY_OWNED));
        }
        if (ownsClaimInWorld(snapshot, target, claim.getWorldUuid())) {
            return CompletableFuture.completedFuture(ClaimOpResult.failed(ClaimFailureReason.TARGET_HAS_CLAIM));
        }
        AtomicReference<ClaimOpResult> resultRef = new AtomicReference<>();
        return DataStore.INSTANCE.mutate(current -> {
            LandDaoManager daos = LandDaoManager.INSTANCE;
            ClaimData fresh = daos.claimDao().queryForId(claimId);
            if (fresh == null) {
                resultRef.set(ClaimOpResult.failed(ClaimFailureReason.NOT_CLAIMED));
                return current;
            }
            String oldOwnerType = fresh.getOwnerType();
            String oldOwnerId = fresh.getOwnerId();
            long now = System.currentTimeMillis();
            ClaimData transferred = new ClaimData(
                fresh.getClaimId(), fresh.getWorldUuid(), BuiltinOwnerTypes.PLAYER, target.toString(),
                fresh.getName(), false, fresh.getCreatedAt(), now,
                fresh.getBankBalance(), false, 0L, 0L, 0L, 0L
            );
            daos.claimDao().update(transferred);
            if (daos.playerDao().queryForId(target) == null) {
                daos.playerDao().create(new PlayerData(
                    target, ConfigValues.get(ClaimConfigs.START_CHUNKS), 0, now));
            }

            DataSnapshot next = DataStore.rebuildSnapshot();
            // 新所有者：已用额度至少覆盖实际持有（允许超出容量，不拒绝转让）
            OwnerRef targetRef = OwnerRef.of(BuiltinOwnerTypes.PLAYER, target.toString());
            PlayerQuotaData targetQuota = loadOrCreateQuota(target);
            targetQuota.setUsedChunks(Math.max(
                targetQuota.getUsedChunks(), ClaimEngine.currentClaimedChunks(next, targetRef)));
            daos.playerQuotaDao().update(targetQuota);
            // 原个人所有者：额度按剩余实际持有量校正
            UUID oldUuid = BuiltinOwnerTypes.PLAYER.equals(oldOwnerType) ? parseUuid(oldOwnerId) : null;
            if (oldUuid != null && !oldUuid.equals(target)) {
                PlayerQuotaData oldQuota = daos.playerQuotaDao().queryForId(oldUuid);
                if (oldQuota != null) {
                    oldQuota.setUsedChunks(Math.max(0, ClaimEngine.currentClaimedChunks(
                        next, OwnerRef.of(BuiltinOwnerTypes.PLAYER, oldOwnerId))));
                    daos.playerQuotaDao().update(oldQuota);
                }
            }
            resultRef.set(ClaimOpResult.transferred(claimId,
                next.chunksByClaim().getOrDefault(claimId, Set.of()).size()));
            return next;
        }).thenApply(snapshot2 -> resultRef.get());
    }

    /** 目标玩家在该世界是否已有领地；用于避免转让破坏「同一所有者每个世界最多一块领地」。 */
    private static boolean ownsClaimInWorld(DataSnapshot snapshot, UUID target, UUID worldUuid) {
        Set<String> owned = snapshot.claimsByOwner()
            .get(OwnerRef.of(BuiltinOwnerTypes.PLAYER, target.toString()));
        if (owned == null) {
            return false;
        }
        for (String ownedClaimId : owned) {
            ClaimData other = snapshot.claimsById().get(ownedClaimId);
            if (other != null && worldUuid.equals(other.getWorldUuid())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 玩家进服：确保玩家数据存在（发放初始额度）并刷新最后登录时间。
     */
    public CompletableFuture<Void> ensurePlayer(UUID uuid, long now) {
        return DataStore.INSTANCE.mutate(current -> {
            LandDaoManager daos = LandDaoManager.INSTANCE;
            PlayerData data = daos.playerDao().queryForId(uuid);
            if (data == null) {
                daos.playerDao().create(new PlayerData(uuid, ConfigValues.get(ClaimConfigs.START_CHUNKS), 0, now));
            } else {
                data.setLastLogin(now);
                daos.playerDao().update(data);
            }
            loadOrCreateQuota(uuid);
            return DataStore.rebuildSnapshot();
        }).thenApply(snapshot -> null);
    }

    /**
     * 活跃游戏时长累积额度（防挂机判定由调用方完成），不超过配置上限。返回实际增加的额度。
     */
    public CompletableFuture<Integer> accrueChunks(UUID uuid, int chunks) {
        if (chunks <= 0) {
            return CompletableFuture.completedFuture(0);
        }
        AtomicInteger grantedRef = new AtomicInteger();
        return DataStore.INSTANCE.mutate(current -> {
            LandDaoManager daos = LandDaoManager.INSTANCE;
            PlayerData data = daos.playerDao().queryForId(uuid);
            if (data == null) {
                data = new PlayerData(uuid, ConfigValues.get(ClaimConfigs.START_CHUNKS), 0, System.currentTimeMillis());
                daos.playerDao().create(data);
            }
            int cap = ConfigValues.get(ClaimConfigs.MAX_ACCRUED_CHUNKS);
            int granted = Math.max(0, Math.min(chunks, cap - data.getAccruedChunks()));
            if (granted > 0) {
                data.setAccruedChunks(data.getAccruedChunks() + granted);
                daos.playerDao().update(data);
            }
            grantedRef.set(granted);
            return DataStore.rebuildSnapshot();
        }).thenApply(snapshot -> grantedRef.get());
    }

    /**
     * 查询玩家当前可用区块额度（走写线程，主线程不得直接调用 DAO）。
     */
    public CompletableFuture<Long> availableChunks(OwnerRef owner) {
        if (owner.typeKey().equals(BuiltinOwnerTypes.GROUP)) {
            AtomicLong groupAvailable = new AtomicLong();
            return DataStore.INSTANCE.mutate(current -> {
                if (!current.groups().containsKey(owner.identifier())) {
                    groupAvailable.set(0L);
                } else {
                    groupAvailable.set(Math.max(0L,
                        GroupService.INSTANCE.groupCapacity(current, owner.identifier())
                            - ClaimEngine.currentClaimedChunks(current, owner)));
                }
                return current;
            }).thenApply(snapshot -> groupAvailable.get());
        }
        if (!owner.typeKey().equals(BuiltinOwnerTypes.PLAYER)) {
            return CompletableFuture.completedFuture(UNLIMITED);
        }
        UUID uuid = parseUuid(owner.identifier());
        if (uuid == null) {
            return CompletableFuture.completedFuture(0L);
        }
        AtomicLong availableRef = new AtomicLong();
        return DataStore.INSTANCE.mutate(current -> {
            availableRef.set(availableChunks(uuid, current));
            return current;
        }).thenApply(snapshot -> availableRef.get());
    }

    private long availableChunks(UUID uuid, DataSnapshot snapshot) throws SQLException {
        PlayerData playerData = LandDaoManager.INSTANCE.playerDao().queryForId(uuid);
        long capacity = capacityOf(playerData);
        PlayerQuotaData quota = loadOrCreateQuota(uuid);
        long used = Math.max(quota.getUsedChunks(), ClaimEngine.currentClaimedChunks(
            snapshot, OwnerRef.of(BuiltinOwnerTypes.PLAYER, uuid.toString())));
        return Math.max(0L, capacity - used);
    }

    private long capacityOf(@Nullable PlayerData playerData) {
        if (playerData == null) {
            return ConfigValues.get(ClaimConfigs.START_CHUNKS);
        }
        return (long) playerData.getAccruedChunks() + playerData.getBoughtChunks();
    }

    private PlayerQuotaData loadOrCreateQuota(UUID uuid) throws SQLException {
        var quotaDao = LandDaoManager.INSTANCE.playerQuotaDao();
        PlayerQuotaData quota = quotaDao.queryForId(uuid);
        if (quota == null) {
            quota = new PlayerQuotaData(uuid, 0);
            quotaDao.create(quota);
        }
        return quota;
    }

    private @Nullable String findClaimInWorld(DataSnapshot snapshot, OwnerRef owner, UUID world) {
        for (String claimId : snapshot.claimsByOwner().getOrDefault(owner, Set.of())) {
            ClaimData claim = snapshot.claimsById().get(claimId);
            if (claim != null && claim.getWorldUuid().equals(world)) {
                return claimId;
            }
        }
        return null;
    }

    private static @Nullable UUID parseUuid(String identifier) {
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
