package pers.yufiria.landguard.claim;

import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

/**
 * 系统级整领释放（欠费/不活跃回收、管理员强制放弃、孤儿清理共用）。
 * 必须在 DataStore 单写线程内调用：删除领地下全部区块、flag、设置与领地行，
 * 个人所有者按实际持有量校正已用额度（系统回收不扣额度）。
 * 银行余额的处理由调用方决定（本类不触碰经济账户）。
 */
public final class ClaimRelease {

    private ClaimRelease() {
    }

    public static final class Released {

        private final ClaimData claim;
        private final int chunks;

        Released(ClaimData claim, int chunks) {
            this.claim = claim;
            this.chunks = chunks;
        }

        public ClaimData claim() {
            return claim;
        }

        public int chunks() {
            return chunks;
        }
    }

    /**
     * 删除指定领地的全部落库数据；返回被释放的区块数。领地不存在返回 null。
     */
    public static Released release(LandDaoManager daos, DataSnapshot snapshot, String claimId) throws SQLException {
        ClaimData claim = daos.claimDao().queryForId(claimId);
        if (claim == null) {
            return null;
        }
        int chunks = snapshot.chunksByClaim().getOrDefault(claimId, Set.of()).size();

        var chunkDelete = daos.claimChunkDao().deleteBuilder();
        chunkDelete.where(w -> w.equals("claim_id", claimId));
        chunkDelete.delete();

        var flagDelete = daos.roleFlagDao().deleteBuilder();
        flagDelete.where(w -> w.equals("claim_id", claimId));
        flagDelete.delete();

        var settingDelete = daos.claimSettingDao().deleteBuilder();
        settingDelete.where(w -> w.equals("claim_id", claimId));
        settingDelete.delete();

        daos.claimDao().delete(claim);

        if (BuiltinOwnerTypes.PLAYER.equals(claim.getOwnerType())) {
            refundQuota(snapshot, claim, chunks);
        }
        return new Released(claim, chunks);
    }

    private static void refundQuota(DataSnapshot snapshot, ClaimData claim, int releasedChunks) throws SQLException {
        UUID uuid;
        try {
            uuid = UUID.fromString(claim.getOwnerId());
        } catch (IllegalArgumentException e) {
            return;
        }
        // 系统回收：被释放区块的额度全额退回，超出实际持有的历史消耗保留
        PlayerQuotaLedger.refund(uuid, snapshot, releasedChunks, releasedChunks);
    }

}
