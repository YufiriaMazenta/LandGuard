package pers.yufiria.landguard.claim;

import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.PlayerQuotaData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;

import java.sql.SQLException;
import java.util.UUID;

/**
 * 玩家已用额度（{@code lg_player_quota.used_chunks}）的唯一读写入口，只能在 DataStore 的写线程内调用。
 * 该列是一份「已消耗额度」的持久账本：放弃领地只按 {@code UNCLAIM_RETURN_RATIO} 比例返还，
 * 因此它可能高于当前实际持有量，不能由快照派生。快照里的实际持有量是它的下界，
 * 「两者取大」这一不变量只在本类实现一次；写策略按语义分成 charge / refund / raiseToActual / trimToActual。
 */
public final class PlayerQuotaLedger {

    private PlayerQuotaLedger() {
    }

    /** 取账本行，不存在则建行（初始已用 0）。 */
    public static PlayerQuotaData ensure(UUID player) throws SQLException {
        DataStore.assertWriteContext();
        var quotaDao = LandDaoManager.INSTANCE.playerQuotaDao();
        PlayerQuotaData quota = quotaDao.queryForId(player);
        if (quota == null) {
            quota = new PlayerQuotaData(player, 0);
            quotaDao.create(quota);
        }
        return quota;
    }

    /** 额度计算用的已用量 = max(持久账本, 快照中的实际持有)；账本行不存在按 0 计，不建行。 */
    public static int effectiveUsed(DataSnapshot snapshot, UUID player) throws SQLException {
        DataStore.assertWriteContext();
        PlayerQuotaData quota = LandDaoManager.INSTANCE.playerQuotaDao().queryForId(player);
        long ledger = quota == null ? 0L : quota.getUsedChunks();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(ledger, actualHeld(snapshot, player)));
    }

    /** 认领：账本取「已用量」后加上本次落库的区块数。 */
    public static void charge(UUID player, DataSnapshot before, int claimedChunks) throws SQLException {
        PlayerQuotaData quota = ensure(player);
        long used = Math.max(quota.getUsedChunks(), actualHeld(before, player)) + claimedChunks;
        quota.setUsedChunks((int) Math.min(Integer.MAX_VALUE, used));
        LandDaoManager.INSTANCE.playerQuotaDao().update(quota);
    }

    /** 放弃：账本扣除返还部分，但不得低于放弃后的实际持有量。 */
    public static void refund(UUID player, DataSnapshot before, int releasedChunks, int refundedChunks)
        throws SQLException {
        PlayerQuotaData quota = ensure(player);
        long remainingActual = Math.max(0L, actualHeld(before, player) - releasedChunks);
        long remainingLedger = Math.max(0L, (long) quota.getUsedChunks() - refundedChunks);
        quota.setUsedChunks((int) Math.min(Integer.MAX_VALUE, Math.max(remainingActual, remainingLedger)));
        LandDaoManager.INSTANCE.playerQuotaDao().update(quota);
    }

    /** 受让方：账本至少覆盖转让后的实际持有量（保留历史消耗，不赠送额度）。 */
    public static void raiseToActual(UUID player, DataSnapshot after) throws SQLException {
        PlayerQuotaData quota = ensure(player);
        quota.setUsedChunks((int) Math.min(Integer.MAX_VALUE,
            Math.max(quota.getUsedChunks(), actualHeld(after, player))));
        LandDaoManager.INSTANCE.playerQuotaDao().update(quota);
    }

    /** 系统回收 / 转让原主 / 管理员强制收回：账本按实际持有量校正（丢弃历史消耗）。 */
    public static void trimToActual(UUID player, DataSnapshot after) throws SQLException {
        DataStore.assertWriteContext();
        PlayerQuotaData quota = LandDaoManager.INSTANCE.playerQuotaDao().queryForId(player);
        if (quota == null) {
            return;
        }
        quota.setUsedChunks((int) Math.min(Integer.MAX_VALUE, actualHeld(after, player)));
        LandDaoManager.INSTANCE.playerQuotaDao().update(quota);
    }

    private static long actualHeld(DataSnapshot snapshot, UUID player) {
        return ClaimEngine.currentClaimedChunks(snapshot,
            OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.toString()));
    }

}