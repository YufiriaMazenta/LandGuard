package pers.yufiria.landguard.upkeep;

import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.claim.ClaimRelease;
import pers.yufiria.landguard.config.UpkeepConfigs;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.economy.EconomyService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.util.ConfigValues;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 维护费、欠费宽限与不活跃回收领域服务（FR-8）。
 * 一轮扫描的全部落库动作都在 {@link DataStore#mutate} 单写线程内完成；
 * 时间由调用方注入（{@code runCycle(now)}），短周期测试可直接用合成时间驱动。
 *
 * 规则：
 * - 管理领地（admin）与逐领地 upkeep_exempt 豁免：两类流程全部跳过。
 * - upkeep 只扣领地银行（组领地扣组银行）；银行不足则清零抵费并起算欠费，
 *   欠费越过宽限仍未补足则整领释放；之后任意周期足额扣款即解除欠费。
 * - 不活跃：个人按所有者 last_login，组按全体成员最近 last_login；
 *   越过阈值先警告，警告后越过宽限仍不活跃则整领释放；期间成员登录即解除警告。
 * - 无法解析的所有者（已删除组/缺玩家行=孤儿，或第三方组织类型）本服务不处理，
 *   孤儿宽限流程由 Task 11 的管理模块负责。
 */
public enum UpkeepService {

    INSTANCE;

    private static final double EPS = 1e-9D;

    /**
     * 执行一轮扫描。
     *
     * @param now 当前时间戳（毫秒），测试可注入任意时钟
     */
    public CompletableFuture<UpkeepCycleResult> runCycle(long now) {
        boolean upkeepEnabled = ConfigValues.get(UpkeepConfigs.UPKEEP_ENABLED)
            && EconomyService.INSTANCE.available();
        boolean inactivityEnabled = ConfigValues.get(UpkeepConfigs.INACTIVITY_ENABLED);
        if (!upkeepEnabled && !inactivityEnabled) {
            return CompletableFuture.completedFuture(UpkeepCycleResult.empty());
        }

        long upkeepPeriodMs = ConfigValues.get(UpkeepConfigs.PERIOD_SECONDS) * 1000L;
        long upkeepGraceMs = ConfigValues.get(UpkeepConfigs.GRACE_SECONDS) * 1000L;
        long inactiveThresholdMs = ConfigValues.get(UpkeepConfigs.INACTIVITY_THRESHOLD_SECONDS) * 1000L;
        long inactiveGraceMs = ConfigValues.get(UpkeepConfigs.INACTIVITY_GRACE_SECONDS) * 1000L;
        double costPerChunk = ConfigValues.get(UpkeepConfigs.COST_PER_CHUNK);

        List<UpkeepNotice> notices = new ArrayList<>();
        AtomicInteger fullyCharged = new AtomicInteger();
        AtomicInteger partiallyCharged = new AtomicInteger();
        AtomicInteger releasedCount = new AtomicInteger();

        return DataStore.INSTANCE.mutate(current -> {
            LandDaoManager daos = LandDaoManager.INSTANCE;
            List<ClaimData> claims = new ArrayList<>(current.claimsById().values());
            for (ClaimData snapshotClaim : claims) {
                ClaimData claim = daos.claimDao().queryForId(snapshotClaim.getClaimId());
                if (claim == null) {
                    continue;
                }
                // 管理领地与逐领地豁免：upkeep 与不活跃全部跳过
                if (claim.isAdmin() || claim.isUpkeepExempt()) {
                    continue;
                }
                OwnerActivity owner = resolveOwner(daos, current, claim);
                if (owner == null) {
                    continue;
                }

                boolean exists = true;
                if (upkeepEnabled) {
                    exists = processUpkeep(daos, current, claim, owner.recipients(), now,
                        upkeepPeriodMs, upkeepGraceMs, costPerChunk, notices,
                        fullyCharged, partiallyCharged, releasedCount);
                }
                if (exists && inactivityEnabled) {
                    processInactivity(daos, current, claim, owner.lastLogin(), now,
                        inactiveThresholdMs, inactiveGraceMs, owner.recipients(), notices, releasedCount);
                }
            }
            return DataStore.rebuildSnapshot();
        }).thenApply(next -> new UpkeepCycleResult(
            List.copyOf(notices),
            fullyCharged.get(),
            partiallyCharged.get(),
            releasedCount.get()
        ));
    }

    /**
     * 管理员逐领地设置 upkeep/回收豁免（Task 11 管理命令调用）。
     */
    public CompletableFuture<Boolean> setExempt(String claimId, boolean exempt) {
        AtomicBoolean found = new AtomicBoolean(false);
        return DataStore.INSTANCE.mutate(current -> {
            ClaimData claim = LandDaoManager.INSTANCE.claimDao().queryForId(claimId);
            if (claim != null) {
                claim.setUpkeepExempt(exempt);
                LandDaoManager.INSTANCE.claimDao().update(claim);
                found.set(true);
            }
            return DataStore.rebuildSnapshot();
        }).thenApply(next -> found.get());
    }

    // ================= upkeep =================

    private boolean processUpkeep(LandDaoManager daos, DataSnapshot snapshot, ClaimData claim,
                                  Set<UUID> recipients, long now, long periodMs, long graceMs,
                                  double costPerChunk, List<UpkeepNotice> notices,
                                  AtomicInteger fullyCharged, AtomicInteger partiallyCharged,
                                  AtomicInteger releasedCount) throws Exception {
        long chargedAt = claim.getUpkeepChargedAt();
        if (chargedAt == 0L) {
            // 首次观察：仅记录起算时间，不立即扣费
            claim.setUpkeepChargedAt(now);
            daos.claimDao().update(claim);
            return true;
        }
        if (now - chargedAt >= periodMs) {
            int chunks = snapshot.chunksByClaim().getOrDefault(claim.getClaimId(), Set.of()).size();
            double due = round2(chunks * costPerChunk);
            boolean paid = chargeBank(daos, claim, due);
            if (paid) {
                fullyCharged.incrementAndGet();
                if (claim.getUpkeepUnpaidSince() != 0L) {
                    claim.setUpkeepUnpaidSince(0L);
                }
            } else {
                partiallyCharged.incrementAndGet();
                if (claim.getUpkeepUnpaidSince() == 0L) {
                    claim.setUpkeepUnpaidSince(now);
                    notices.add(UpkeepNotice.warning(
                        claim.getClaimId(), claim.getName(), UpkeepCause.UPKEEP_DEBT, recipients));
                }
            }
            claim.setUpkeepChargedAt(now);
            daos.claimDao().update(claim);
        }
        // 宽限判定独立于收费周期：即使周期比宽限长也不会延迟释放
        if (claim.getUpkeepUnpaidSince() != 0L && now - claim.getUpkeepUnpaidSince() >= graceMs) {
            release(daos, snapshot, claim, UpkeepCause.UPKEEP_DEBT, recipients, notices, releasedCount);
            return false;
        }
        return true;
    }

    /**
     * 从领地/组银行扣除 due；余额不足时清零抵费并返回 false。
     * 组领地的时间戳字段仍写在 claim 行（由调用方 update），此处只动银行余额所在行。
     */
    private boolean chargeBank(LandDaoManager daos, ClaimData claim, double due) throws SQLException {
        if (BuiltinOwnerTypes.GROUP.equals(claim.getOwnerType())) {
            GroupData group = daos.groupDao().queryForId(claim.getOwnerId());
            if (group == null) {
                throw new SQLException("group owner row missing: " + claim.getOwnerId());
            }
            double balance = group.getBankBalance();
            if (balance + EPS >= due) {
                group.setBankBalance(round2(balance - due));
                daos.groupDao().update(group);
                return true;
            }
            if (balance > 0D) {
                group.setBankBalance(0D);
                daos.groupDao().update(group);
            }
            return false;
        }
        double balance = claim.getBankBalance();
        if (balance + EPS >= due) {
            claim.setBankBalance(round2(balance - due));
            return true;
        }
        if (balance > 0D) {
            claim.setBankBalance(0D);
        }
        return false;
    }

    // ================= 不活跃 =================

    private void processInactivity(LandDaoManager daos, DataSnapshot snapshot, ClaimData claim, long lastLogin,
                                   long now, long thresholdMs, long graceMs, Set<UUID> recipients,
                                   List<UpkeepNotice> notices, AtomicInteger releasedCount) throws Exception {
        long inactiveFor = now - lastLogin;
        if (inactiveFor < thresholdMs) {
            // 重新活跃：解除既有警告
            if (claim.getInactiveWarnedAt() != 0L) {
                claim.setInactiveWarnedAt(0L);
                daos.claimDao().update(claim);
            }
            return;
        }
        if (claim.getInactiveWarnedAt() == 0L) {
            claim.setInactiveWarnedAt(now);
            daos.claimDao().update(claim);
            notices.add(UpkeepNotice.warning(
                claim.getClaimId(), claim.getName(), UpkeepCause.INACTIVITY, recipients));
            return;
        }
        if (now - claim.getInactiveWarnedAt() >= graceMs) {
            release(daos, snapshot, claim, UpkeepCause.INACTIVITY,
                recipients, notices, releasedCount);
        }
    }

    private void release(LandDaoManager daos, DataSnapshot snapshot, ClaimData claim, UpkeepCause cause,
                         Set<UUID> recipients, List<UpkeepNotice> notices, AtomicInteger releasedCount)
        throws Exception {
        int chunks = snapshot.chunksByClaim().getOrDefault(claim.getClaimId(), Set.of()).size();
        ClaimRelease.release(daos, snapshot, claim.getClaimId());
        notices.add(UpkeepNotice.released(
            claim.getClaimId(), claim.getName(), cause, chunks, recipients));
        releasedCount.incrementAndGet();
    }

    // ================= 所有者解析 =================

    private record OwnerActivity(Set<UUID> recipients, long lastLogin) {
    }

    /**
     * 解析可处理的内置所有者及其最近活跃时间；无法解析（孤儿/第三方类型）返回 null。
     */
    private @Nullable OwnerActivity resolveOwner(
        LandDaoManager daos, DataSnapshot snapshot, ClaimData claim
    ) throws SQLException {
        if (BuiltinOwnerTypes.PLAYER.equals(claim.getOwnerType())) {
            UUID uuid;
            try {
                uuid = UUID.fromString(claim.getOwnerId());
            } catch (IllegalArgumentException e) {
                return null;
            }
            PlayerData data = daos.playerDao().queryForId(uuid);
            if (data == null) {
                return null;
            }
            return new OwnerActivity(Set.of(uuid), data.getLastLogin());
        }
        if (BuiltinOwnerTypes.GROUP.equals(claim.getOwnerType())) {
            GroupData group = daos.groupDao().queryForId(claim.getOwnerId());
            if (group == null) {
                return null;
            }
            Map<UUID, String> members = snapshot.groupMembers()
                .getOrDefault(claim.getOwnerId(), Map.of());
            Set<UUID> recipients = new LinkedHashSet<>(members.keySet());
            recipients.add(group.getLeaderUuid());
            long latest = 0L;
            for (UUID member : recipients) {
                PlayerData data = daos.playerDao().queryForId(member);
                if (data != null) {
                    latest = Math.max(latest, data.getLastLogin());
                }
            }
            return new OwnerActivity(Set.copyOf(recipients), latest);
        }
        // server（管理领地 admin=true 已在上方跳过）与第三方组织类型不在本服务范围
        return null;
    }

    private static double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

}
