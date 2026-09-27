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
import pers.yufiria.landguard.data.SnapshotAudit;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.group.GroupOpResult;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 以组身份认领的授权（P4）：无 {@code CLAIM_EXPAND} 的成员被拒、manager 可扩容同一块组领地、
 * 无操作者（actor == null）一律拒绝，且失败的认领不落库。
 */
public class ClaimGroupIdentityTest {

    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();
    static final UUID CAROL = UUID.randomUUID();

    @TempDir
    Path tempDir;

    private ConnectionSource connection;
    private UUID world;

    @BeforeEach
    void setUp() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("group-identity.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        world = UUID.randomUUID();
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

    private String createGuild() {
        GroupOpResult created = GroupService.INSTANCE.createGroup(ALICE, "Guild", "Guild").join();
        assertTrue(created.success());
        return created.groupId();
    }

    private void joinAs(UUID player, String roleId) {
        assertTrue(GroupService.INSTANCE.invite(ALICE, "Guild", player).join().success());
        assertTrue(GroupService.INSTANCE.acceptInvite(player, "Guild").join().success());
        if (roleId != null) {
            assertTrue(GroupService.INSTANCE.assignRole(ALICE, "Guild", player, roleId).join().success());
        }
    }

    @Test
    void memberWithoutExpandPermissionCannotClaimForGroup() throws Exception {
        String groupId = createGuild();
        joinAs(BOB, null);

        OwnerRef group = OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId);
        ClaimOpResult result = ClaimService.INSTANCE.claim(
            group, world, List.of(ChunkLoc.of(world, 0, 0)), "GuildHome", false, BOB).join();

        assertFalse(result.success());
        assertEquals(ClaimFailureReason.NOT_PERMITTED, result.failureReason());
        // 不落库
        assertTrue(LandDaoManager.INSTANCE.claimDao().queryForAll().isEmpty());
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        assertTrue(snapshot.claimsById().isEmpty());
        assertFalse(snapshot.claimIdByChunk().containsKey(ChunkLoc.of(world, 0, 0)));
    }

    @Test
    void managerExpandsExistingGroupClaimInsteadOfCreatingNewOne() throws Exception {
        String groupId = createGuild();
        joinAs(CAROL, Roles.MANAGER);

        OwnerRef group = OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId);
        // 先以领袖身份造一块组领地
        assertTrue(ClaimService.INSTANCE.claim(
            group, world, List.of(ChunkLoc.of(world, 0, 0)), "GuildHome", false, ALICE).join().success());
        assertEquals(1, DataStore.INSTANCE.snapshot().claimsByOwner().get(group).size());

        // manager 认领相邻区块：命中同一块组领地（扩容，不新建）
        ClaimOpResult expand = ClaimService.INSTANCE.claim(
            group, world, List.of(ChunkLoc.of(world, 1, 0)), "GuildHome", false, CAROL).join();
        assertTrue(expand.success());

        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        Set<String> owned = snapshot.claimsByOwner().getOrDefault(group, Set.of());
        assertEquals(1, owned.size(), "该组仍只有一块领地（扩容而非新建）");
        String claimId = snapshot.claimIdByChunk().get(ChunkLoc.of(world, 0, 0));
        assertEquals(claimId, snapshot.claimIdByChunk().get(ChunkLoc.of(world, 1, 0)));
        assertEquals(2, snapshot.chunksByClaim().get(claimId).size());
    }

    @Test
    void nullActorCannotClaimForGroup() throws Exception {
        String groupId = createGuild();

        OwnerRef group = OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId);
        ClaimOpResult result = ClaimService.INSTANCE.claim(
            group, world, List.of(ChunkLoc.of(world, 0, 0)), "GuildHome", false, null).join();

        assertFalse(result.success());
        assertEquals(ClaimFailureReason.NOT_PERMITTED, result.failureReason());
        assertTrue(LandDaoManager.INSTANCE.claimDao().queryForAll().isEmpty());
        assertTrue(DataStore.INSTANCE.snapshot().claimsById().isEmpty());
    }

}
