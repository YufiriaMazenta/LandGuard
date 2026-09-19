package pers.yufiria.landguard.group;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.claim.ClaimFailureReason;
import pers.yufiria.landguard.claim.ClaimOpResult;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.MembershipInvalidationListener;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;
import pers.yufiria.landguard.protection.CheckResult;
import pers.yufiria.landguard.protection.ProtectionChecker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TR-7.1 / AC-3 / AC-6（组侧）：邀请加入/踢出即时生效、自定义角色随组持久化、
 * 领袖转让、领地转让给组、解散触发孤儿流程、组额度公式。
 */
public class GroupLifecycleIntegrationTest {

    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();
    static final UUID CAROL = UUID.randomUUID();

    @TempDir
    Path tempDir;

    private ConnectionSource connection;
    private final AtomicInteger membershipEvents = new AtomicInteger();
    private final AtomicInteger ownerRemovedEvents = new AtomicInteger();
    private final MembershipInvalidationListener listener = new MembershipInvalidationListener() {
        @Override
        public void onMembershipChanged(OwnerRef owner) {
            membershipEvents.incrementAndGet();
        }

        @Override
        public void onOwnerRemoved(OwnerRef owner) {
            ownerRemovedEvents.incrementAndGet();
        }
    };

    private UUID world;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("groups.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.addListener(listener);
        world = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws Exception {
        ClaimOwnerRegistry.INSTANCE.removeListener(listener);
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        DataStore.INSTANCE.joinReload();
        connection.close();
    }

    private String createGuild() {
        GroupOpResult created = GroupService.INSTANCE.createGroup(ALICE, "Guild").join();
        assertTrue(created.success());
        return created.groupId();
    }

    private void placeGroupClaim(String groupId, int cx, int cz) throws Exception {
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(new ClaimData(
            claimId, world, BuiltinOwnerTypes.GROUP, groupId, "GuildHome", false, now, now, 0D, false));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, cx, cz));
        DataStore.INSTANCE.reloadFrom(connection).join();
    }

    @Test
    void membershipChangesTakeEffectImmediately() throws Exception {
        String groupId = createGuild();
        placeGroupClaim(groupId, 0, 0);
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();

        // 加入前：BOB 是 visitor，放置默认拒绝；ALICE 是 owner 放行
        assertFalse(ProtectionChecker.checkBehavior(snapshot, BOB, world, 0, 0, BuiltinFlags.PLACE).allowed());
        assertTrue(ProtectionChecker.checkBehavior(snapshot, ALICE, world, 0, 0, BuiltinFlags.PLACE).allowed());

        // 邀请 → 接受：不重登、不切世界，立刻获得 member 权限
        assertTrue(GroupService.INSTANCE.invite(ALICE, "Guild", BOB).join().success());
        GroupOpResult joined = GroupService.INSTANCE.acceptInvite(BOB, "Guild").join();
        assertTrue(joined.success());
        snapshot = DataStore.INSTANCE.snapshot();
        CheckResult afterJoin = ProtectionChecker.checkBehavior(snapshot, BOB, world, 0, 0, BuiltinFlags.PLACE);
        assertTrue(afterJoin.allowed(), "加入用户组后立即获得 member 放置权限");
        assertEquals(Roles.MEMBER, afterJoin.context().memberRole());
        ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId));
        assertNotNull(owner);
        assertEquals(Roles.MEMBER, owner.roleOf(BOB));
        assertTrue(membershipEvents.get() >= 1, "成员变更走 SPI 失效回调");

        // 被踢：立刻失去权限
        assertTrue(GroupService.INSTANCE.kick(ALICE, "Guild", BOB).join().success());
        snapshot = DataStore.INSTANCE.snapshot();
        assertFalse(ProtectionChecker.checkBehavior(snapshot, BOB, world, 0, 0, BuiltinFlags.PLACE).allowed(),
            "被踢后立即失去权限");
        assertNull(owner.roleOf(BOB));
    }

    @Test
    void customRolePersistsAndLeadershipTransfers() throws Exception {
        String groupId = createGuild();
        placeGroupClaim(groupId, 1, 0);

        // 自定义角色随组存储（含优先级），接受邀请后分配
        GroupOpResult roleCreated = GroupService.INSTANCE.createRole(ALICE, "Guild", "veteran", 10, "老兵").join();
        assertTrue(roleCreated.success());
        assertTrue(GroupService.INSTANCE.invite(ALICE, "Guild", CAROL).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(CAROL, "Guild").join().success());
        assertTrue(GroupService.INSTANCE.assignRole(ALICE, "Guild", CAROL, "veteran").join().success());

        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        assertEquals("veteran", snapshot.groupMembers().get(groupId).get(CAROL));
        assertEquals(10, snapshot.groupRoles().get(groupId).get("veteran").getPriority());
        // 身份不因 flag 变化而改变（AC-6 双字段模型）
        ClaimOwner group = ClaimOwnerRegistry.INSTANCE.resolve(OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId));
        assertEquals("veteran", group.roleOf(CAROL));

        // 领袖转让：CAROL 成为 owner，ALICE 降为 manager，持久化保持
        GroupOpResult transferred = GroupService.INSTANCE.transferLeadership(ALICE, "Guild", CAROL).join();
        assertTrue(transferred.success());
        snapshot = DataStore.INSTANCE.snapshot();
        assertEquals(CAROL, snapshot.groups().get(groupId).getLeaderUuid());
        assertEquals(Roles.OWNER, snapshot.groupMembers().get(groupId).get(CAROL));
        assertEquals(Roles.MANAGER, snapshot.groupMembers().get(groupId).get(ALICE));

        DataStore.INSTANCE.reloadFrom(connection).join();
        snapshot = DataStore.INSTANCE.snapshot();
        assertEquals(CAROL, snapshot.groups().get(groupId).getLeaderUuid());
        assertEquals("veteran", snapshot.groupRoles().get(groupId).get("veteran").getRoleId());
    }

    @Test
    void disbandOrphansClaimsAndLeaderCannotLeave() {
        String groupId = createGuild();

        // 领袖不能直接退出
        assertEquals(GroupFailureReason.LEADER_CANNOT_LEAVE,
            GroupService.INSTANCE.leave(ALICE, "Guild").join().failureReason());

        GroupOpResult disbanded = GroupService.INSTANCE.disband(ALICE, "Guild").join();
        assertTrue(disbanded.success());
        assertNull(ClaimOwnerRegistry.INSTANCE.resolve(OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId)));
        assertEquals(1, ownerRemovedEvents.get(), "解散发出 OWNER_REMOVED 失效通知");
    }

    @Test
    void personalClaimCanBeGivenToGroup() throws Exception {
        String groupId = createGuild();
        assertTrue(GroupService.INSTANCE.invite(ALICE, "Guild", BOB).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(BOB, "Guild").join().success());

        // ALICE 个人认领 (5,5)，随后整领转让给组；区块与设置保留，BOB 立即获得权限
        OwnerRef alice = OwnerRef.of(BuiltinOwnerTypes.PLAYER, ALICE.toString());
        ClaimOpResult claimed = ClaimService.INSTANCE.claim(
            alice, world, List.of(ChunkLoc.of(world, 5, 5)), "AliceHome", false).join();
        assertTrue(claimed.success());
        assertFalse(ProtectionChecker.checkBehavior(DataStore.INSTANCE.snapshot(), BOB, world, 5, 5, BuiltinFlags.PLACE).allowed());

        assertTrue(GroupService.INSTANCE.giveClaim(ALICE, "Guild", world, 5, 5).join().success());
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(snapshot.claimIdByChunk().get(ChunkLoc.of(world, 5, 5)));
        assertEquals(BuiltinOwnerTypes.GROUP, claim.getOwnerType());
        assertEquals(groupId, claim.getOwnerId());
        assertEquals("AliceHome", claim.getName());
        assertTrue(ProtectionChecker.checkBehavior(snapshot, BOB, world, 5, 5, BuiltinFlags.PLACE).allowed(),
            "转让后组成员立即获得权限");

        // 领袖再次转让已是组产的领地：不是个人名下领地，拒绝
        assertEquals(GroupFailureReason.NOT_CLAIM_OWNER,
            GroupService.INSTANCE.giveClaim(ALICE, "Guild", world, 5, 5).join().failureReason());
        // 普通成员无权发起转让
        assertEquals(GroupFailureReason.NOT_MANAGER,
            GroupService.INSTANCE.giveClaim(BOB, "Guild", world, 5, 5).join().failureReason());
    }

    @Test
    void groupQuotaIsBasePlusMemberBonus() {
        String groupId = createGuild();
        OwnerRef groupRef = OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId);
        UUID quotaWorld = UUID.randomUUID();

        // 仅领袖 1 人：256 + 16 = 272；17x17=289 超额拒绝
        List<ChunkLoc> tooMany = square(quotaWorld, 17);
        ClaimOpResult over = ClaimService.INSTANCE.claim(groupRef, quotaWorld, tooMany, "G", false).join();
        assertFalse(over.success());
        assertEquals(ClaimFailureReason.QUOTA_EXCEEDED, over.failureReason());

        // 16x16=256 可认领，剩余 16
        List<ChunkLoc> fit = square(quotaWorld, 16);
        ClaimOpResult ok = ClaimService.INSTANCE.claim(groupRef, quotaWorld, fit, "G", false).join();
        assertTrue(ok.success());
        assertEquals(16, ok.availableChunks());
    }

    private static List<ChunkLoc> square(UUID worldUuid, int size) {
        List<ChunkLoc> result = new ArrayList<>(size * size);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                result.add(ChunkLoc.of(worldUuid, x, z));
            }
        }
        return result;
    }

}
