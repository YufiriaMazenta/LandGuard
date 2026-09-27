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
import pers.yufiria.landguard.data.SnapshotAudit;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.PermissionPoint;
import pers.yufiria.landguard.owner.*;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * TR-7.1 / AC-3 / AC-6（组侧）：邀请加入/踢出即时生效、成员身份随组持久化、
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
        // 增量重载安全网：已发布快照必须与全量重读按值一致
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());
        DataStore.INSTANCE.joinReload();
        DataStore.INSTANCE.publish(DataSnapshot.empty());
        connection.close();
    }

    private String createGuild() {
        GroupOpResult created = GroupService.INSTANCE.createGroup(ALICE, "Guild", "Guild").join();
        assertTrue(created.success());
        return created.groupId();
    }

    private void placeGroupClaim(String groupId, int cx, int cz) throws Exception {
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(ClaimData.builder(
            claimId, world, BuiltinOwnerTypes.GROUP, groupId, "GuildHome")
            .createdAt(now).lastActiveAt(now).build());
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
    void memberIdentityPersistsAndLeadershipTransfers() throws Exception {
        String groupId = createGuild();
        placeGroupClaim(groupId, 1, 0);

        // 配置身份随组存储：接受邀请后把 CAROL 指派为管理者身份
        assertTrue(GroupService.INSTANCE.invite(ALICE, "Guild", CAROL).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(CAROL, "Guild").join().success());
        assertTrue(GroupService.INSTANCE.assignRole(ALICE, "Guild", CAROL, Roles.MANAGER).join().success());

        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        assertEquals(Roles.MANAGER, snapshot.groupMembers().get(groupId).get(CAROL));
        // 身份生效：管理者拥有组管理权限点
        assertTrue(IdentityPermissions.has(snapshot, groupId, CAROL, PermissionPoint.GROUP_INVITE));
        // 身份不因 flag 变化而改变（AC-6 双字段模型）
        ClaimOwner group = ClaimOwnerRegistry.INSTANCE.resolve(OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId));
        assertEquals(Roles.MANAGER, group.roleOf(CAROL));

        // 领袖转让：CAROL 成为 owner，ALICE 降为 manager，持久化保持
        GroupOpResult transferred = GroupService.INSTANCE.transferLeadership(ALICE, "Guild", CAROL).join();
        assertTrue(transferred.success());
        snapshot = DataStore.INSTANCE.snapshot();
        assertEquals(CAROL, snapshot.groups().get(groupId).getLeaderUuid());
        assertEquals(Roles.OWNER, snapshot.groupMembers().get(groupId).get(CAROL));
        assertEquals(Roles.MANAGER, snapshot.groupMembers().get(groupId).get(ALICE));
        assertTrue(IdentityPermissions.isLeader(snapshot, groupId, CAROL));

        DataStore.INSTANCE.reloadFrom(connection).join();
        snapshot = DataStore.INSTANCE.snapshot();
        assertEquals(CAROL, snapshot.groups().get(groupId).getLeaderUuid());
        assertEquals(Roles.OWNER, snapshot.groupMembers().get(groupId).get(CAROL));
        assertEquals(Roles.MANAGER, snapshot.groupMembers().get(groupId).get(ALICE));
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
        ClaimOpResult over = ClaimService.INSTANCE.claim(groupRef, quotaWorld, tooMany, "G", false, ALICE).join();
        assertFalse(over.success());
        assertEquals(ClaimFailureReason.QUOTA_EXCEEDED, over.failureReason());

        // 16x16=256 可认领，剩余 16
        List<ChunkLoc> fit = square(quotaWorld, 16);
        ClaimOpResult ok = ClaimService.INSTANCE.claim(groupRef, quotaWorld, fit, "G", false, ALICE).join();
        assertTrue(ok.success());
        assertEquals(16, ok.availableChunks());
    }

    @Test
    void groupLeaderCanUnclaimOwnClaimButMembersCannot() throws Exception {
        String groupId = createGuild();
        assertTrue(GroupService.INSTANCE.invite(ALICE, "Guild", BOB).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(BOB, "Guild").join().success());
        placeGroupClaim(groupId, 5, 5);

        ChunkLoc target = ChunkLoc.of(world, 5, 5);

        // 普通成员不是领地真实所有者：被拒且区块保留
        ClaimOpResult denied = ClaimService.INSTANCE.unclaimOwnedBy(BOB, target).join();
        assertFalse(denied.success());
        assertEquals(ClaimFailureReason.NOT_OWNER, denied.failureReason());
        assertTrue(DataStore.INSTANCE.snapshot().claimIdByChunk().containsKey(target));

        // 野外（无领主）同样是 NOT_CLAIMED
        assertEquals(ClaimFailureReason.NOT_CLAIMED,
            ClaimService.INSTANCE.unclaimOwnedBy(ALICE, ChunkLoc.of(world, 9, 9)).join().failureReason());

        // 领袖自助放弃：区块消失，最后一块放弃后整领随 flag/设置删除，额度按组容量展示
        ClaimOpResult done = ClaimService.INSTANCE.unclaimOwnedBy(ALICE, target).join();
        assertTrue(done.success());
        assertEquals(1, done.affectedChunks());
        assertEquals(0, done.refundedChunks(), "组领地放弃不结算个人额度返还");
        assertEquals(GroupService.INSTANCE.groupCapacity(DataStore.INSTANCE.snapshot(), groupId),
            done.availableChunks());
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        assertFalse(snapshot.claimIdByChunk().containsKey(target));
        assertTrue(snapshot.claimsById().isEmpty());
    }

    @Test
    void leadershipTransferDoesNotMutatePublishedSnapshot() throws Exception {
        String groupId = createGuild();
        assertTrue(GroupService.INSTANCE.invite(ALICE, "Guild", CAROL).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(CAROL, "Guild").join().success());

        DataSnapshot before = DataStore.INSTANCE.snapshot();
        GroupData beforeGroup = before.groups().get(groupId);
        assertEquals(ALICE, beforeGroup.getLeaderUuid());

        assertTrue(GroupService.INSTANCE.transferLeadership(ALICE, "Guild", CAROL).join().success());

        // 已发布的旧快照必须保持不变：转让只允许改数据库副本，不得就地改写快照里的实例
        assertSame(beforeGroup, before.groups().get(groupId));
        assertEquals(ALICE, beforeGroup.getLeaderUuid(), "旧快照内的领袖被就地改写了");
        assertEquals(CAROL, DataStore.INSTANCE.snapshot().groups().get(groupId).getLeaderUuid());
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
