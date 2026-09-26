package pers.yufiria.landguard.admin;

import pers.yufiria.landguard.claim.ClaimRelease;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.claim.PlayerQuotaLedger;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.data.SnapshotPart;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.upkeep.UpkeepCycleResult;
import pers.yufiria.landguard.upkeep.UpkeepService;
import pers.yufiria.landguard.util.ConfigValues;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理员领域操作（FR-9.1）：整领强制释放、强制转让、逐领地豁免、手动触发回收周期。
 * 所有落库都在 {@link DataStore#mutate} 单写线程内原子完成；认领/放弃批量操作复用
 * {@link ClaimService} 的 admin 路径。
 */
public enum AdminService {

    INSTANCE;

    /**
     * 强制释放整个领地（任意所有者，含孤儿）。系统回收：个人所有者额度按实际持有校正，不额外扣减。
     */
    public CompletableFuture<AdminOpResult> releaseClaim(String claimId) {
        AtomicBoolean found =
            new AtomicBoolean(false);
        AtomicInteger chunksRef =
            new AtomicInteger();
        return DataStore.INSTANCE.mutate(current -> {
            ClaimRelease.Released released = ClaimRelease.release(LandDaoManager.INSTANCE, current, claimId);
            if (released == null) {
                return current;
            }
            found.set(true);
            chunksRef.set(released.chunks());
            // 该领地的区块、flag、设置与领地行全部被删，按领地范围重读
            DataSnapshot next = DataStore.reloadScoped(SnapshotPart.CLAIM, claimId);
            next = DataStore.reloadScoped(SnapshotPart.CLAIM_CHUNK, claimId);
            next = DataStore.reloadScoped(SnapshotPart.ROLE_FLAG, claimId);
            next = DataStore.reloadScoped(SnapshotPart.CLAIM_SETTING, claimId);
            return next;
        }).thenApply(next -> found.get()
            ? AdminOpResult.ok(claimId, chunksRef.get())
            : AdminOpResult.failed(AdminFailureReason.CLAIM_NOT_FOUND));
    }

    /**
     * 强制把整个领地转让给指定玩家：归属变为个人所有，admin/exempt/欠费/警告/孤儿状态全部重置，
     * 领地银行余额随领地保留；组银行余额属于组行，不随转让移动。
     * 转让后双方额度按实际持有量校正（管理操作允许目标暂时超出容量）。
     */
    public CompletableFuture<AdminOpResult> transferClaim(String claimId, UUID target) {
        AtomicReference<AdminFailureReason> earlyFailure =
            new AtomicReference<>();
        return DataStore.INSTANCE.mutate(current -> {
            LandDaoManager daos = LandDaoManager.INSTANCE;
            ClaimData claim = daos.claimDao().queryForId(claimId);
            if (claim == null) {
                return current;
            }
            if (BuiltinOwnerTypes.PLAYER.equals(claim.getOwnerType())
                && claim.getOwnerId().equals(target.toString())) {
                earlyFailure.set(AdminFailureReason.ALREADY_OWNED);
                return current;
            }
            String oldOwnerType = claim.getOwnerType();
            String oldOwnerId = claim.getOwnerId();
            long now = System.currentTimeMillis();

            PlayerData targetData = daos.playerDao().queryForId(target);
            if (targetData == null) {
                targetData = new PlayerData(target, ConfigValues.get(ClaimConfigs.START_CHUNKS), 0, now);
                daos.playerDao().create(targetData);
            }

            // 生命周期时间戳全部归零：转让后重新开始计费与活跃判定
            ClaimData transferred = ClaimData.builder(
                    claim.getClaimId(), claim.getWorldUuid(), BuiltinOwnerTypes.PLAYER, target.toString(),
                    claim.getName())
                .createdAt(claim.getCreatedAt())
                .lastActiveAt(now)
                .bankBalance(claim.getBankBalance())
                .build();
            daos.claimDao().update(transferred);

            // 领地行换了所有者，且转让目标可能新建了玩家行
            DataSnapshot next = DataStore.reload(SnapshotPart.PLAYER);
            next = DataStore.reloadScoped(SnapshotPart.CLAIM, claimId);
            // 新所有者：额度已用至少要覆盖实际持有（允许超出容量，不拒绝管理操作）
            PlayerQuotaLedger.raiseToActual(target, next);
            // 原个人所有者：额度按剩余实际持有量校正
            if (BuiltinOwnerTypes.PLAYER.equals(oldOwnerType) && !oldOwnerId.equals(target.toString())) {
                UUID oldUuid = parseUuid(oldOwnerId);
                if (oldUuid != null) {
                    PlayerQuotaLedger.trimToActual(oldUuid, next);
                }
            }
            return next;
        }).thenApply(next -> {
            if (earlyFailure.get() != null) {
                return AdminOpResult.failed(earlyFailure.get());
            }
            ClaimData claim = next.claimsById().get(claimId);
            if (claim == null) {
                return AdminOpResult.failed(AdminFailureReason.CLAIM_NOT_FOUND);
            }
            if (!BuiltinOwnerTypes.PLAYER.equals(claim.getOwnerType())
                || !claim.getOwnerId().equals(target.toString())) {
                return AdminOpResult.failed(AdminFailureReason.INVALID_ARGUMENT);
            }
            int chunks = next.chunksByClaim().getOrDefault(claimId, Set.of()).size();
            return AdminOpResult.ok(claimId, chunks);
        });
    }

    /**
     * 逐领地设置维护费/回收豁免。
     */
    public CompletableFuture<AdminOpResult> setExempt(String claimId, boolean exempt) {
        return UpkeepService.INSTANCE.setExempt(claimId, exempt).thenApply(found ->
            found ? AdminOpResult.ok(claimId, 0) : AdminOpResult.failed(AdminFailureReason.CLAIM_NOT_FOUND));
    }

    /**
     * 管理员重命名领地：不校验归属（管理员可强制操作），名字非法返回 INVALID_ARGUMENT。
     */
    public CompletableFuture<AdminOpResult> renameClaim(String claimId, String newName) {
        String name = ClaimService.normalizeClaimName(newName);
        if (!ClaimService.isValidClaimName(name)) {
            return CompletableFuture.completedFuture(AdminOpResult.failed(AdminFailureReason.INVALID_ARGUMENT));
        }
        AtomicBoolean found = new AtomicBoolean(false);
        return DataStore.INSTANCE.mutate(current -> {
            ClaimData claim = LandDaoManager.INSTANCE.claimDao().queryForId(claimId);
            if (claim == null) {
                return current;
            }
            claim.setName(name);
            LandDaoManager.INSTANCE.claimDao().update(claim);
            found.set(true);
            return DataStore.reloadScoped(SnapshotPart.CLAIM, claimId);
        }).thenApply(next -> found.get()
            ? AdminOpResult.ok(claimId, 0)
            : AdminOpResult.failed(AdminFailureReason.CLAIM_NOT_FOUND));
    }

    /**
     * 手动执行一轮维护周期（upkeep/不活跃 + 孤儿），返回合并结果。
     */
    public CompletableFuture<UpkeepCycleResult> runMaintenance(long now) {
        return UpkeepService.INSTANCE.runCycle(now)
            .thenCompose(upkeepResult -> OrphanService.INSTANCE.runCycle(now)
                .thenApply(orphanResult -> UpkeepCycleResult.merge(upkeepResult, orphanResult)));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
