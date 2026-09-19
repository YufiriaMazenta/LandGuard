package pers.yufiria.landguard.admin;

import pers.yufiria.landguard.claim.ClaimRelease;
import pers.yufiria.landguard.config.UpkeepConfigs;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.upkeep.UpkeepCause;
import pers.yufiria.landguard.upkeep.UpkeepCycleResult;
import pers.yufiria.landguard.upkeep.UpkeepNotice;
import pers.yufiria.landguard.util.ConfigValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 孤儿领地领域服务（FR-1.6 / FR-6.4）。
 * 孤儿判定与保护链一致：管理领地之外，{@link ClaimOwnerRegistry} 无法解析所有者即为孤儿
 * （用户组解散、第三方提供方注销、类型键未知等）。
 *
 * 一轮扫描全部落库在 {@link DataStore#mutate} 单写线程内完成；
 * 首次观察落孤儿起算时间并通知控制台，越过宽限仍未恢复则整领释放；
 * 期间所有者实体恢复可解析则立即解除孤儿状态。时间由调用方注入，便于测试。
 */
public enum OrphanService {

    INSTANCE;

    /**
     * 当前快照中的全部孤儿领地（管理领地除外）。只读快照 + 注册中心解析，可在主线程调用。
     */
    public List<OrphanInfo> listOrphans() {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<OrphanInfo> result = new ArrayList<>();
        for (ClaimData claim : snapshot.claimsById().values()) {
            if (claim.isAdmin() || !isOrphan(claim)) {
                continue;
            }
            int chunks = snapshot.chunksByClaim().getOrDefault(claim.getClaimId(), Set.of()).size();
            result.add(new OrphanInfo(
                claim.getClaimId(),
                claim.getName(),
                claim.getOwnerType(),
                claim.getOwnerId(),
                chunks,
                claim.getOrphanSince()
            ));
        }
        return List.copyOf(result);
    }

    /**
     * 某领地当前是否为孤儿（所有者实体无法解析）。
     */
    public boolean isOrphan(ClaimData claim) {
        return !claim.isAdmin()
            && ClaimOwnerRegistry.INSTANCE.resolve(claim.getOwnerType(), claim.getOwnerId()) == null;
    }

    /**
     * 执行一轮孤儿扫描。
     *
     * @param now 当前时间戳（毫秒），测试可注入任意时钟
     */
    public CompletableFuture<UpkeepCycleResult> runCycle(long now) {
        if (!ConfigValues.get(UpkeepConfigs.ORPHAN_ENABLED)) {
            return CompletableFuture.completedFuture(UpkeepCycleResult.empty());
        }
        long graceMs = ConfigValues.get(UpkeepConfigs.ORPHAN_GRACE_SECONDS) * 1000L;

        List<UpkeepNotice> notices = new ArrayList<>();
        AtomicInteger releasedCount = new AtomicInteger();

        return DataStore.INSTANCE.mutate(current -> {
            LandDaoManager daos = LandDaoManager.INSTANCE;
            List<ClaimData> claims = new ArrayList<>(current.claimsById().values());
            for (ClaimData snapshotClaim : claims) {
                ClaimData claim = daos.claimDao().queryForId(snapshotClaim.getClaimId());
                if (claim == null || claim.isAdmin()) {
                    continue;
                }
                if (ClaimOwnerRegistry.INSTANCE.resolve(claim.getOwnerType(), claim.getOwnerId()) != null) {
                    // 所有者恢复（组重建/提供方重新注册）：解除孤儿状态，宽限重新起算
                    if (claim.getOrphanSince() != 0L) {
                        claim.setOrphanSince(0L);
                        daos.claimDao().update(claim);
                    }
                    continue;
                }
                if (claim.getOrphanSince() == 0L) {
                    claim.setOrphanSince(now);
                    daos.claimDao().update(claim);
                    notices.add(UpkeepNotice.warning(
                        claim.getClaimId(), claim.getName(), UpkeepCause.ORPHAN, Set.of()));
                    continue;
                }
                if (now - claim.getOrphanSince() >= graceMs) {
                    int chunks = current.chunksByClaim()
                        .getOrDefault(claim.getClaimId(), Set.of()).size();
                    ClaimRelease.release(daos, current, claim.getClaimId());
                    notices.add(UpkeepNotice.released(
                        claim.getClaimId(), claim.getName(), UpkeepCause.ORPHAN, chunks, Set.of()));
                    releasedCount.incrementAndGet();
                }
            }
            return DataStore.rebuildSnapshot();
        }).thenApply(next -> new UpkeepCycleResult(
            List.copyOf(notices), 0, 0, releasedCount.get()
        ));
    }

}
