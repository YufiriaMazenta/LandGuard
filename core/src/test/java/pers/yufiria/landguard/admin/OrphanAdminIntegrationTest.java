package pers.yufiria.landguard.admin;

import crypticlib.config.node.ConfigNode;
import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.claim.ClaimOpResult;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.config.UpkeepConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.GroupMemberData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.database.entity.PlayerQuotaData;
import pers.yufiria.landguard.group.GroupOpResult;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.server.ServerClaimOwner;
import pers.yufiria.landguard.protection.BuiltinFlags;
import pers.yufiria.landguard.upkeep.UpkeepCause;
import pers.yufiria.landguard.upkeep.UpkeepCycleResult;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TR-11.1：解散组织后领地进入孤儿列表，越过宽限期自动释放；管理领地永不进孤儿流程；
 * 管理员强制认领/放弃/转让/释放/豁免的额度与生命周期行为；手动回收合并两条扫描链。
 */
public class OrphanAdminIntegrationTest {

    private static final long ORPHAN_GRACE = 500L;
    private static final long BASE_MS = 1_000_000_000L;

    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();

    @TempDir
    Path tempDir;

    private ConnectionSource connection;
    private UUID world;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("admin.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(pers.yufiria.landguard.owner.builtin.server.ServerClaimOwnerProvider.INSTANCE);
        world = UUID.randomUUID();

        set(UpkeepConfigs.UPKEEP_ENABLED, false);
        set(UpkeepConfigs.INACTIVITY_ENABLED, false);
        set(UpkeepConfigs.ORPHAN_ENABLED, true);
        set(UpkeepConfigs.ORPHAN_GRACE_SECONDS, (int) ORPHAN_GRACE);
    }

    @AfterEach
    void tearDown() throws Exception {
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.SERVER));
        set(UpkeepConfigs.UPKEEP_ENABLED, null);
        set(UpkeepConfigs.INACTIVITY_ENABLED, null);
        set(UpkeepConfigs.ORPHAN_ENABLED, null);
        set(UpkeepConfigs.ORPHAN_GRACE_SECONDS, null);
        DataStore.INSTANCE.joinReload();
        connection.close();
    }

    private static void set(ConfigNode<?, ?> node, Object value) throws Exception {
        Field field = ConfigNode.class.getDeclaredField("value");
        field.setAccessible(true);
        field.set(node, value);
    }

    private long t(long seconds) {
        return BASE_MS + seconds * 1000L;
    }

    private ChunkLoc loc(int x, int z) {
        return ChunkLoc.of(world, x, z);
    }

    private void player(UUID uuid) throws Exception {
        LandDaoManager.INSTANCE.playerDao().create(new PlayerData(uuid, 64, 0, t(0)));
    }

    private void quota(UUID uuid, int used) throws Exception {
        LandDaoManager.INSTANCE.playerQuotaDao().create(new PlayerQuotaData(uuid, used));
    }

    private String createGroup(String name) {
        GroupOpResult result = GroupService.INSTANCE.createGroup(ALICE, name).join();
        assertTrue(result.success());
        return result.groupId();
    }

    private String groupClaim(String groupId, int baseX) {
        ClaimOpResult result = ClaimService.INSTANCE.claim(
            OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId), world,
            List.of(loc(baseX, 0)), nameOr(groupId), false).join();
        assertTrue(result.success());
        return result.claimId();
    }

    private String nameOr(String s) {
        return "claim-" + s.substring(0, 8);
    }

    private ClaimData fresh(String claimId) throws Exception {
        return LandDaoManager.INSTANCE.claimDao().queryForId(claimId);
    }

    // ================= 孤儿宽限（TR-11.1 主链路） =================

    @Test
    void disbandedGroupClaimOrphanListThenWarnAndReleaseAtBoundary() throws Exception {
        player(ALICE);
        String groupId = createGroup("Guild");
        String claimId = groupClaim(groupId, 0);
        assertTrue(OrphanService.INSTANCE.listOrphans().isEmpty());

        // 解散组：领地保留，立即出现在孤儿列表（尚未扫描时 orphanSince=0）
        GroupOpResult disbanded = GroupService.INSTANCE.disband(ALICE, "Guild").join();
        assertTrue(disbanded.success());
        List<OrphanInfo> orphans = OrphanService.INSTANCE.listOrphans();
        assertEquals(1, orphans.size());
        assertEquals(claimId, orphans.get(0).claimId());
        assertEquals(BuiltinOwnerTypes.GROUP, orphans.get(0).ownerType());
        assertEquals(0L, orphans.get(0).orphanSince());

        // 首次扫描：落孤儿起算时间 + 一条警告
        UpkeepCycleResult first = OrphanService.INSTANCE.runCycle(t(0)).join();
        assertEquals(1, first.notices().size());
        assertFalse(first.notices().get(0).release());
        assertEquals(UpkeepCause.ORPHAN, first.notices().get(0).cause());
        assertEquals(t(0), fresh(claimId).getOrphanSince());
        assertNotNull(fresh(claimId));

        // 宽限前 1 秒：不释放、无新通知
        UpkeepCycleResult before = OrphanService.INSTANCE.runCycle(t(ORPHAN_GRACE - 1)).join();
        assertEquals(0, before.notices().size());
        assertEquals(0, before.released());
        assertNotNull(fresh(claimId));

        // 宽限届满：整领释放 + 释放通知，孤儿列表清空
        UpkeepCycleResult expired = OrphanService.INSTANCE.runCycle(t(ORPHAN_GRACE)).join();
        assertEquals(1, expired.notices().size());
        assertTrue(expired.notices().get(0).release());
        assertEquals(1, expired.released());
        assertNull(fresh(claimId));
        assertFalse(DataStore.INSTANCE.snapshot().claimIdByChunk().containsKey(loc(0, 0)));
        assertTrue(OrphanService.INSTANCE.listOrphans().isEmpty());
    }

    @Test
    void restoredOwnerClearsOrphanStateAndGraceRestarts() throws Exception {
        player(ALICE);
        String groupId = createGroup("Guild");
        String claimId = groupClaim(groupId, 2);
        GroupService.INSTANCE.disband(ALICE, "Guild").join();
        assertEquals(1, OrphanService.INSTANCE.runCycle(t(0)).join().notices().size());
        assertEquals(t(0), fresh(claimId).getOrphanSince());

        // 管理员在宽限期内重建同 ID 组（等价接管：组行+成员行恢复）
        LandDaoManager.INSTANCE.groupDao().create(
            new GroupData(groupId, "Guild", ALICE, t(100), 0D));
        LandDaoManager.INSTANCE.groupMemberDao().create(
            new GroupMemberData(groupId, ALICE, Roles.OWNER));
        DataStore.INSTANCE.reloadFrom(connection).join();

        // 扫描到所有者恢复：孤儿时间戳清零，不释放
        UpkeepCycleResult rescued = OrphanService.INSTANCE.runCycle(t(ORPHAN_GRACE + 999)).join();
        assertEquals(0, rescued.released());
        assertEquals(0L, fresh(claimId).getOrphanSince());
        assertNotNull(fresh(claimId));
        assertTrue(OrphanService.INSTANCE.listOrphans().isEmpty());
    }

    @Test
    void unknownProviderTypeIsOrphanAndReleasedAfterGrace() throws Exception {
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(new ClaimData(
            claimId, world, "thirdparty-org", "org-42", "Ext", false, now, now, 0D, false));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, 4, 0));
        DataStore.INSTANCE.reloadFrom(connection).join();

        assertEquals(1, OrphanService.INSTANCE.listOrphans().size());
        OrphanService.INSTANCE.runCycle(t(0)).join();
        UpkeepCycleResult expired = OrphanService.INSTANCE.runCycle(t(ORPHAN_GRACE)).join();
        assertEquals(1, expired.released());
        assertNull(fresh(claimId));
    }

    @Test
    void adminServerClaimNeverOrphaned() throws Exception {
        ClaimOpResult created = ClaimService.INSTANCE.claim(
            OwnerRef.of(BuiltinOwnerTypes.SERVER, ServerClaimOwner.ID),
            world, List.of(loc(5, 0)), "Admin Claim", true).join();
        assertTrue(created.success());
        String claimId = created.claimId();

        ClaimData row = fresh(claimId);
        assertTrue(row.isAdmin());
        assertEquals(BuiltinOwnerTypes.SERVER, row.getOwnerType());
        assertEquals(ServerClaimOwner.ID, row.getOwnerId());
        assertTrue(OrphanService.INSTANCE.listOrphans().isEmpty());

        // 极远未来：无警告无释放
        UpkeepCycleResult result = OrphanService.INSTANCE.runCycle(t(1000000)).join();
        assertEquals(0, result.notices().size());
        assertEquals(0, result.released());
        assertNotNull(fresh(claimId));
    }

    // ================= 管理员强制操作 =================

    @Test
    void forceClaimIgnoresAdjacencyAndExpandsServerClaim() throws Exception {
        ClaimOpResult first = ClaimService.INSTANCE.claim(
            OwnerRef.of(BuiltinOwnerTypes.SERVER, ServerClaimOwner.ID),
            world, List.of(loc(6, 0)), "Admin Claim", true).join();
        assertTrue(first.success());
        // 与首个区块不相邻：普通认领会被拒，管理认领跳过相邻校验，并入同一 server 领地
        ClaimOpResult second = ClaimService.INSTANCE.claim(
            OwnerRef.of(BuiltinOwnerTypes.SERVER, ServerClaimOwner.ID),
            world, List.of(loc(9, 0)), "Admin Claim", true).join();
        assertTrue(second.success());
        assertEquals(first.claimId(), second.claimId());
        assertEquals(2, DataStore.INSTANCE.snapshot()
            .chunksByClaim().get(first.claimId()).size());
    }

    @Test
    void adminUnclaimRemovesOthersChunksAndCorrectsQuota() throws Exception {
        player(ALICE);
        quota(ALICE, 2);
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(new ClaimData(
            claimId, world, BuiltinOwnerTypes.PLAYER, ALICE.toString(), "Home",
            false, now, now, 0D, false));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, 0, 0));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, 1, 0));
        DataStore.INSTANCE.reloadFrom(connection).join();

        ClaimOpResult result = ClaimService.INSTANCE.adminUnclaim(List.of(loc(0, 0), loc(1, 0))).join();
        assertTrue(result.success());
        assertEquals(2, result.affectedChunks());
        assertNull(fresh(claimId));
        assertEquals(0, LandDaoManager.INSTANCE.playerQuotaDao().queryForId(ALICE).getUsedChunks());
    }

    @Test
    void forceTransferMovesOwnershipResetsLifecycleAndFixesQuota() throws Exception {
        player(ALICE);
        player(BOB);
        quota(ALICE, 2);
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(new ClaimData(
            claimId, world, BuiltinOwnerTypes.PLAYER, ALICE.toString(), "Home",
            false, now, now, 5D, true, 123L, 456L, 789L, 0L));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, 0, 0));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, 1, 0));
        DataStore.INSTANCE.reloadFrom(connection).join();

        AdminOpResult transferred = AdminService.INSTANCE.transferClaim(claimId, BOB).join();
        assertTrue(transferred.success());
        assertEquals(2, transferred.affectedChunks());
        ClaimData row = fresh(claimId);
        assertEquals(BuiltinOwnerTypes.PLAYER, row.getOwnerType());
        assertEquals(BOB.toString(), row.getOwnerId());
        assertFalse(row.isAdmin());
        assertFalse(row.isUpkeepExempt());
        assertEquals(0L, row.getUpkeepChargedAt());
        assertEquals(0L, row.getUpkeepUnpaidSince());
        assertEquals(0L, row.getInactiveWarnedAt());
        assertEquals(0L, row.getOrphanSince());
        // 领地银行余额随领地保留
        assertEquals(5D, row.getBankBalance(), 1e-9);
        assertEquals(2, LandDaoManager.INSTANCE.playerQuotaDao().queryForId(BOB).getUsedChunks());
        assertEquals(0, LandDaoManager.INSTANCE.playerQuotaDao().queryForId(ALICE).getUsedChunks());

        // 再次转让给同一人：幂等拒绝
        assertEquals(AdminFailureReason.ALREADY_OWNED,
            AdminService.INSTANCE.transferClaim(claimId, BOB).join().failureReason());
    }

    @Test
    void forceReleaseAndExemptOperations() throws Exception {
        player(ALICE);
        quota(ALICE, 1);
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(new ClaimData(
            claimId, world, BuiltinOwnerTypes.PLAYER, ALICE.toString(), "Home",
            false, now, now, 0D, false));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, 7, 0));
        DataStore.INSTANCE.reloadFrom(connection).join();

        assertTrue(AdminService.INSTANCE.setExempt(claimId, true).join().success());
        assertTrue(fresh(claimId).isUpkeepExempt());
        assertEquals(AdminFailureReason.CLAIM_NOT_FOUND,
            AdminService.INSTANCE.setExempt("missing-id", false).join().failureReason());

        AdminOpResult released = AdminService.INSTANCE.releaseClaim(claimId).join();
        assertTrue(released.success());
        assertEquals(1, released.affectedChunks());
        assertNull(fresh(claimId));
        assertEquals(0, LandDaoManager.INSTANCE.playerQuotaDao().queryForId(ALICE).getUsedChunks());

        assertEquals(AdminFailureReason.CLAIM_NOT_FOUND,
            AdminService.INSTANCE.releaseClaim(claimId).join().failureReason());
    }

    @Test
    void manualMaintenanceRunsOrphanChain() throws Exception {
        player(ALICE);
        String groupId = createGroup("Guild");
        groupClaim(groupId, 8);
        GroupService.INSTANCE.disband(ALICE, "Guild").join();

        UpkeepCycleResult result = AdminService.INSTANCE.runMaintenance(t(0)).join();
        assertEquals(1, result.notices().size());
        assertEquals(UpkeepCause.ORPHAN, result.notices().get(0).cause());
    }

}
