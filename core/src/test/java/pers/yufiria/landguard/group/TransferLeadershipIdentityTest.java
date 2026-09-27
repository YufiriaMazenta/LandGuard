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
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.identity.IdentityRegistry;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 转让领袖时的身份变更改为配置驱动（方案「层级规则」末句）：
 * 目标写入领袖身份 id，原领袖降为非领袖身份中优先级最高者（默认配置即 manager）；
 * 同时回归锁定「旧快照内的 GroupData 不被就地改写」。
 */
public class TransferLeadershipIdentityTest {

    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();

    @TempDir
    Path tempDir;

    private ConnectionSource connection;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("transfer.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
    }

    @AfterEach
    void tearDown() throws Exception {
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        // 增量重载安全网：已发布快照必须与全量重读按值一致
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());
        DataStore.INSTANCE.joinReload();
        DataStore.INSTANCE.publish(DataSnapshot.empty());
        connection.close();
    }

    @Test
    void transferLeadershipUsesConfiguredIdentities() throws Exception {
        GroupOpResult created = GroupService.INSTANCE.createGroup(ALICE, "Guild", "Guild").join();
        assertTrue(created.success());
        String groupId = created.groupId();
        assertTrue(GroupService.INSTANCE.invite(ALICE, "Guild", BOB).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(BOB, "Guild").join().success());

        DataSnapshot before = DataStore.INSTANCE.snapshot();
        GroupData beforeGroup = before.groups().get(groupId);
        assertEquals(ALICE, beforeGroup.getLeaderUuid());

        assertTrue(GroupService.INSTANCE.transferLeadership(ALICE, "Guild", BOB).join().success());

        DataSnapshot after = DataStore.INSTANCE.snapshot();
        assertEquals(BOB, after.groups().get(groupId).getLeaderUuid());
        assertEquals(IdentityRegistry.INSTANCE.leaderIdentityId(),
            after.groupMembers().get(groupId).get(BOB), "新领袖写入领袖身份 id");
        assertEquals(Roles.MANAGER, after.groupMembers().get(groupId).get(ALICE),
            "原领袖降为非领袖最高优先级身份（默认配置即 manager）");

        // 旧快照内的 GroupData 不得被就地改写
        assertSame(beforeGroup, before.groups().get(groupId));
        assertEquals(ALICE, beforeGroup.getLeaderUuid(), "旧快照内的领袖被就地改写了");

        // 持久化校验
        DataStore.INSTANCE.reloadFrom(connection).join();
        DataSnapshot persisted = DataStore.INSTANCE.snapshot();
        assertEquals(BOB, persisted.groups().get(groupId).getLeaderUuid());
        assertEquals(IdentityRegistry.INSTANCE.leaderIdentityId(),
            persisted.groupMembers().get(groupId).get(BOB));
        assertEquals(Roles.MANAGER, persisted.groupMembers().get(groupId).get(ALICE));
    }

}
