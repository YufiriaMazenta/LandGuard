package pers.yufiria.landguard.claim;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.database.entity.PlayerQuotaData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AC-4 / AC-7 / TR-4.1：重叠与非相邻拒绝、认领扣额度、放弃按比例返还、内存索引与 DAO 双向一致。
 */
public class ClaimLifecycleIntegrationTest {

    @TempDir
    Path tempDir;

    static UUID world = UUID.randomUUID();
    UUID playerA = UUID.randomUUID();
    UUID playerB = UUID.randomUUID();
    OwnerRef ownerA;
    OwnerRef ownerB;
    private ConnectionSource connection;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("claim-lifecycle.db");
        connection = new JdbcConnectionSource("jdbc:sqlite:" + dbFile);
        LandDaoManager.INSTANCE.init(connection);
        long now = System.currentTimeMillis();
        // 低额度便于构造额度不足：A 总容量 10
        LandDaoManager.INSTANCE.playerDao().create(new PlayerData(playerA, 10, 0, now));
        LandDaoManager.INSTANCE.playerDao().create(new PlayerData(playerB, 64, 0, now));
        DataStore.INSTANCE.reloadFrom(connection).join();
        ownerA = OwnerRef.of(BuiltinOwnerTypes.PLAYER, playerA.toString());
        ownerB = OwnerRef.of(BuiltinOwnerTypes.PLAYER, playerB.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        DataStore.INSTANCE.joinReload();
        connection.close();
    }

    @Test
    void claimOverlapAdjacencyQuotaAndRefund() throws Exception {
        // 1. 首个领地可认领任意未占用区块
        ClaimOpResult first = ClaimService.INSTANCE.claim(
            ownerA, world, List.of(loc(0, 0)), "home", false).join();
        assertTrue(first.success());
        assertEquals(1, first.affectedChunks());
        assertEquals(9, first.availableChunks());
        DataSnapshot snap = DataStore.INSTANCE.snapshot();
        assertEquals(1, snap.claimIdByChunk().size());
        assertEquals(1, LandDaoManager.INSTANCE.claimChunkDao().queryForAll().size());
        assertNotNull(snap.claimsByOwner().get(ownerA));

        // 2. 非相邻认领拒绝，数据不变
        ClaimOpResult far = ClaimService.INSTANCE.claim(
            ownerA, world, List.of(loc(10, 10)), "home", false).join();
        assertFalse(far.success());
        assertEquals(ClaimFailureReason.NOT_ADJACENT, far.failureReason());
        assertEquals(1, DataStore.INSTANCE.snapshot().claimIdByChunk().size());

        // 3. 重叠认领拒绝
        ClaimOpResult overlap = ClaimService.INSTANCE.claim(
            ownerA, world, List.of(loc(0, 0)), "home", false).join();
        assertEquals(ClaimFailureReason.OVERLAP, overlap.failureReason());

        // 4. 对角相接在默认配置下不算相邻
        ClaimOpResult diagonal = ClaimService.INSTANCE.claim(
            ownerA, world, List.of(loc(1, 1)), "home", false).join();
        assertEquals(ClaimFailureReason.NOT_ADJACENT, diagonal.failureReason());

        // 5. 四方向相邻扩容成功，额度扣减
        ClaimOpResult adjacent = ClaimService.INSTANCE.claim(
            ownerA, world, List.of(loc(1, 0)), "home", false).join();
        assertTrue(adjacent.success());
        assertEquals(8, adjacent.availableChunks());
        PlayerQuotaData quota = LandDaoManager.INSTANCE.playerQuotaDao().queryForId(playerA);
        assertEquals(2, quota.getUsedChunks());

        // 6. 整领地方块批量放弃：2 个、返还比例 0.5 → 返还 1；领地清空被删除
        ClaimOpResult abandon = ClaimService.INSTANCE.unclaim(
            ownerA, List.of(loc(0, 0), loc(1, 0))).join();
        assertTrue(abandon.success());
        assertEquals(2, abandon.affectedChunks());
        assertEquals(1, abandon.refundedChunks());
        assertEquals(9, abandon.availableChunks());
        snap = DataStore.INSTANCE.snapshot();
        assertTrue(snap.claimIdByChunk().isEmpty());
        assertTrue(snap.claimsById().isEmpty());
        assertTrue(snap.claimsByOwner().getOrDefault(ownerA, Set.of()).isEmpty());
        assertEquals(0, LandDaoManager.INSTANCE.claimChunkDao().queryForAll().size());
        assertEquals(0, LandDaoManager.INSTANCE.claimDao().queryForAll().size());

        // 7. 额度归零后认领被拒（QUOTA_EXCEEDED 优先于相邻规则）
        PlayerData dataA = LandDaoManager.INSTANCE.playerDao().queryForId(playerA);
        dataA.setAccruedChunks(0);
        LandDaoManager.INSTANCE.playerDao().update(dataA);
        ClaimOpResult noQuota = ClaimService.INSTANCE.claim(
            ownerA, world, List.of(loc(0, 0)), "home", false).join();
        assertEquals(ClaimFailureReason.QUOTA_EXCEEDED, noQuota.failureReason());
        assertNull(DataStore.INSTANCE.snapshot().claimIdByChunk().get(loc(0, 0)));
    }

    @Test
    void foreignLandCannotBeUnclaimedAndSecondOwnerStartsFresh() throws Exception {
        // B 的首个领地可以建在远离 A 的位置
        ClaimOpResult bClaim = ClaimService.INSTANCE.claim(
            ownerB, world, List.of(loc(5, 5)), "castle", false).join();
        assertTrue(bClaim.success());
        String bClaimId = bClaim.claimId();

        // A 随后在 (0,0) 建立自己的首个领地
        assertTrue(ClaimService.INSTANCE.claim(
            ownerA, world, List.of(loc(0, 0)), "home", false).join().success());

        // A 不能放弃 B 的区块
        ClaimOpResult foreign = ClaimService.INSTANCE.unclaim(ownerA, List.of(loc(5, 5))).join();
        assertEquals(ClaimFailureReason.NOT_OWNER, foreign.failureReason());

        // 野外区块放弃 → NOT_CLAIMED
        ClaimOpResult wild = ClaimService.INSTANCE.unclaim(ownerA, List.of(loc(-9, -9))).join();
        assertEquals(ClaimFailureReason.NOT_CLAIMED, wild.failureReason());

        // B 的领地与索引、DAO 保持一致
        DataSnapshot snap = DataStore.INSTANCE.snapshot();
        assertEquals(bClaimId, snap.claimIdByChunk().get(loc(5, 5)));
        assertEquals(1, snap.chunksByClaim().get(bClaimId).size());
        assertEquals(2, LandDaoManager.INSTANCE.claimChunkDao().queryForAll().size());

        // B 自己可以放弃
        assertTrue(ClaimService.INSTANCE.unclaim(ownerB, List.of(loc(5, 5))).join().success());
        assertNull(DataStore.INSTANCE.snapshot().claimIdByChunk().get(loc(5, 5)));
    }

    @Test
    void radiusBatchMustConnectAndDeductsAtomically() throws Exception {
        // radius=2 → 3x3 共 9 块，以 (0,0) 为中心的首个领地，整体连通，允许
        List<ChunkLoc> nine = ClaimEngine.radiusTargets(world, 0, 0, 2);
        assertEquals(9, nine.size());
        ClaimOpResult batch = ClaimService.INSTANCE.claim(ownerB, world, nine, "estate", false).join();
        assertTrue(batch.success());
        assertEquals(9, batch.affectedChunks());
        assertEquals(55, batch.availableChunks());
        DataSnapshot snap = DataStore.INSTANCE.snapshot();
        assertEquals(9, snap.claimIdByChunk().size());
        assertEquals(9, LandDaoManager.INSTANCE.claimChunkDao().queryForAll().size());
        // 同一 claim 承载（同世界扩容而非新建）
        assertEquals(1, snap.claimsByOwner().get(ownerB).size());

        // 与已有领地隔开一圈的 3x3 批次（中心 (5,5) 最近点 (4,4)，对角相接）默认拒绝
        List<ChunkLoc> farBatch = ClaimEngine.radiusTargets(world, 5, 5, 2);
        ClaimOpResult rejected = ClaimService.INSTANCE.claim(ownerB, world, farBatch, "estate", false).join();
        assertEquals(ClaimFailureReason.NOT_ADJACENT, rejected.failureReason());
        assertEquals(9, DataStore.INSTANCE.snapshot().claimIdByChunk().size());
    }

    @Test
    void batchClaimSkipsAlreadyClaimedChunks() throws Exception {
        // A 先拥有 (0,0)
        assertTrue(ClaimService.INSTANCE.claim(ownerA, world, List.of(loc(0, 0)), "home", false).join().success());

        // 再认领以 (0,0) 为中心的 3x3：中心已被占用被跳过，其余 8 块与已有领地相邻，扩容成功
        List<ChunkLoc> nine = ClaimEngine.radiusTargets(world, 0, 0, 2);
        ClaimOpResult batch = ClaimService.INSTANCE.claim(ownerA, world, nine, "home", false).join();
        assertTrue(batch.success());
        assertEquals(8, batch.affectedChunks());
        assertEquals(1, batch.skippedChunks());
        assertEquals(1, batch.availableChunks());
        DataSnapshot snap = DataStore.INSTANCE.snapshot();
        assertEquals(9, snap.claimIdByChunk().size());
        assertEquals(9, LandDaoManager.INSTANCE.claimChunkDao().queryForAll().size());
        // 同一领地承载，未新建第二块地
        assertEquals(1, snap.claimsByOwner().get(ownerA).size());

        // 全部目标都已被占用 → OVERLAP，且不产生任何写入
        ClaimOpResult allClaimed = ClaimService.INSTANCE.claim(ownerA, world, nine, "home", false).join();
        assertEquals(ClaimFailureReason.OVERLAP, allClaimed.failureReason());
        assertEquals(9, DataStore.INSTANCE.snapshot().claimIdByChunk().size());
        assertEquals(9, LandDaoManager.INSTANCE.claimChunkDao().queryForAll().size());
    }

    private static ChunkLoc loc(int x, int z) {
        return ChunkLoc.of(world, x, z);
    }

}
