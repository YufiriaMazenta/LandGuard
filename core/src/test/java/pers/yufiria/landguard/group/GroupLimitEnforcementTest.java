package pers.yufiria.landguard.group;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.data.SnapshotAudit;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组织数量上限（权限驱动）的领域行为：
 * 拥有上限限制建组与接管领袖，加入上限限制建组与接受邀请，且自己拥有的组织计入已加入数量；
 * 上限判定失败不得消耗邀请，自转让（无操作）不受上限影响。
 */
public class GroupLimitEnforcementTest {

    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();
    static final UUID CAROL = UUID.randomUUID();

    @TempDir
    Path tempDir;

    /** 未显式设置的玩家默认不限制，便于单个测试聚焦某个玩家的上限行为。 */
    private final Map<UUID, Integer> joinLimits = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> ownLimits = new ConcurrentHashMap<>();

    private ConnectionSource connection;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("limits.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        GroupService.INSTANCE.setLimitResolver(new GroupLimitResolver() {
            @Override
            public int joinLimit(UUID player) {
                return joinLimits.getOrDefault(player, NO_LIMIT);
            }

            @Override
            public int ownLimit(UUID player) {
                return ownLimits.getOrDefault(player, NO_LIMIT);
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        GroupService.INSTANCE.setLimitResolver(GroupLimitResolver.noLimits());
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        // 增量重载安全网：已发布快照必须与全量重读按值一致
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());
        DataStore.INSTANCE.joinReload();
        DataStore.INSTANCE.publish(DataSnapshot.empty());
        connection.close();
    }

    @Test
    void ownedGroupsCountTowardJoinLimit() {
        joinLimits.put(ALICE, 1);
        assertTrue(create(ALICE, "Guild").success());

        // 已拥有 1 个组织即已占用唯一的加入名额
        assertEquals(GroupFailureReason.JOIN_LIMIT_EXCEEDED, create(ALICE, "Guild2").failureReason());
    }

    @Test
    void ownLimitBlocksCreateAndJoinLimitBlocksAccept() {
        joinLimits.put(ALICE, 2);
        ownLimits.put(ALICE, 1);
        assertTrue(create(ALICE, "Guild").success());
        // 拥有名额已满：不能再建组
        assertEquals(GroupFailureReason.OWN_LIMIT_EXCEEDED, create(ALICE, "Guild2").failureReason());

        // 加入名额还剩 1 个：可接受 BOB 的邀请
        assertTrue(create(BOB, "Other").success());
        assertTrue(GroupService.INSTANCE.invite(BOB, "Other", ALICE).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(ALICE, "Other").join().success());

        // 加入名额已满：拒绝继续加入
        assertTrue(create(CAROL, "Third").success());
        assertTrue(GroupService.INSTANCE.invite(CAROL, "Third", ALICE).join().success());
        assertEquals(GroupFailureReason.JOIN_LIMIT_EXCEEDED,
            GroupService.INSTANCE.acceptInvite(ALICE, "Third").join().failureReason());

        // 上限失败不消耗邀请：腾出名额后仍可接受
        assertTrue(GroupService.INSTANCE.leave(ALICE, "Other").join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(ALICE, "Third").join().success());
    }

    @Test
    void transferLeadershipChecksReceiverOwnLimit() {
        ownLimits.put(BOB, 0);
        GroupOpResult created = create(ALICE, "Guild");
        assertTrue(created.success());
        String groupId = created.groupId();
        assertTrue(GroupService.INSTANCE.invite(ALICE, groupId, BOB).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(BOB, groupId).join().success());

        // 接收者拥有上限为 0：不能接管，领袖保持原状
        assertEquals(GroupFailureReason.OWN_LIMIT_EXCEEDED,
            GroupService.INSTANCE.transferLeadership(ALICE, groupId, BOB).join().failureReason());
        assertTrue(IdentityPermissions.isLeader(DataStore.INSTANCE.snapshot(), groupId, ALICE));

        // 授予 1 个拥有名额后可接管
        ownLimits.put(BOB, 1);
        assertTrue(GroupService.INSTANCE.transferLeadership(ALICE, groupId, BOB).join().success());
        assertTrue(IdentityPermissions.isLeader(DataStore.INSTANCE.snapshot(), groupId, BOB));
    }

    @Test
    void transferLeadershipToSelfIsNotBlockedByLimit() {
        assertTrue(create(ALICE, "Guild").success());
        ownLimits.put(ALICE, 1);
        // 自转让是无操作：即便已在上限也不应失败
        assertTrue(GroupService.INSTANCE.transferLeadership(ALICE, "Guild", ALICE).join().success());
    }

    private static GroupOpResult create(UUID creator, String id) {
        return GroupService.INSTANCE.createGroup(creator, id, id).join();
    }

}