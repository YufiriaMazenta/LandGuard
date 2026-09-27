package pers.yufiria.landguard.group;

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
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.data.SnapshotAudit;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code giveClaim} 的两个边界（方案「giveClaim 两个边界」一节）：
 * 目标组同世界已有领地 → 拒绝（不合并）；组额度不足 → 拒绝；
 * 捐赠成功后校正捐赠者个人额度账本（按剩余实际持有量下降）。
 */
public class GiveClaimQuotaTest {

    static final UUID ALICE = UUID.randomUUID();

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
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("giveclaim.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        world = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws Exception {
        set(ClaimConfigs.GROUP_BASE_CHUNKS, null);
        set(ClaimConfigs.GROUP_BONUS_PER_MEMBER, null);
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        // 增量重载安全网：已发布快照必须与全量重读按值一致
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());
        DataStore.INSTANCE.joinReload();
        DataStore.INSTANCE.publish(DataSnapshot.empty());
        connection.close();
    }

    private static void set(ConfigNode<?, ?> node, Object value) throws Exception {
        Field field = ConfigNode.class.getDeclaredField("value");
        field.setAccessible(true);
        field.set(node, value);
    }

    private String createGuild() {
        GroupOpResult created = GroupService.INSTANCE.createGroup(ALICE, "Guild", "Guild").join();
        assertTrue(created.success());
        return created.groupId();
    }

    private ClaimOpResult claimPersonal(List<ChunkLoc> targets) {
        OwnerRef alice = OwnerRef.of(BuiltinOwnerTypes.PLAYER, ALICE.toString());
        return ClaimService.INSTANCE.claim(alice, world, targets, "AliceHome", false).join();
    }

    private int groupClaimCount(String groupId) {
        return DataStore.INSTANCE.snapshot().claimsByOwner()
            .getOrDefault(OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId), Set.of()).size();
    }

    @Test
    void secondClaimInSameWorldIsRejected() {
        String groupId = createGuild();

        assertTrue(claimPersonal(List.of(ChunkLoc.of(world, 5, 5))).success());
        assertTrue(GroupService.INSTANCE.giveClaim(ALICE, "Guild", world, 5, 5).join().success());
        assertEquals(1, groupClaimCount(groupId));

        // 同一玩家再新建一块个人领地，再次捐赠同一世界 → 拒绝
        assertTrue(claimPersonal(List.of(ChunkLoc.of(world, 10, 10))).success());
        GroupOpResult second = GroupService.INSTANCE.giveClaim(ALICE, "Guild", world, 10, 10).join();
        assertFalse(second.success());
        assertEquals(GroupFailureReason.GROUP_HAS_CLAIM, second.failureReason());

        // 组仍只有 1 块领地，第二块仍是玩家个人领地
        assertEquals(1, groupClaimCount(groupId));
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData secondClaim = snapshot.claimsById().get(snapshot.claimIdByChunk().get(ChunkLoc.of(world, 10, 10)));
        assertEquals(BuiltinOwnerTypes.PLAYER, secondClaim.getOwnerType());
        assertEquals(ALICE.toString(), secondClaim.getOwnerId());
    }

    @Test
    void groupQuotaExceededIsRejected() throws Exception {
        // 压低组容量：base=1、bonus=0 → 1 人组的容量为 1
        set(ClaimConfigs.GROUP_BASE_CHUNKS, 1);
        set(ClaimConfigs.GROUP_BONUS_PER_MEMBER, 0);
        String groupId = createGuild();

        // 个人领地持 2 区块，超过组容量 1 → 拒绝
        assertTrue(claimPersonal(List.of(ChunkLoc.of(world, 0, 0), ChunkLoc.of(world, 1, 0))).success());
        GroupOpResult result = GroupService.INSTANCE.giveClaim(ALICE, "Guild", world, 0, 0).join();
        assertFalse(result.success());
        assertEquals(GroupFailureReason.GROUP_QUOTA_EXCEEDED, result.failureReason());

        // 领地仍属玩家，未转给组
        assertEquals(0, groupClaimCount(groupId));
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(snapshot.claimIdByChunk().get(ChunkLoc.of(world, 0, 0)));
        assertEquals(BuiltinOwnerTypes.PLAYER, claim.getOwnerType());
        assertEquals(ALICE.toString(), claim.getOwnerId());
    }

    @Test
    void donorQuotaIsReleasedAfterGiveClaim() throws Exception {
        createGuild();

        assertTrue(claimPersonal(List.of(ChunkLoc.of(world, 5, 5))).success());
        int before = LandDaoManager.INSTANCE.playerQuotaDao().queryForId(ALICE).getUsedChunks();
        assertEquals(1, before, "认领后账本已用量为 1");

        assertTrue(GroupService.INSTANCE.giveClaim(ALICE, "Guild", world, 5, 5).join().success());

        int after = LandDaoManager.INSTANCE.playerQuotaDao().queryForId(ALICE).getUsedChunks();
        assertEquals(0, after, "捐赠后不再持有区块，个人账本已用量应校正为 0");
    }

}
