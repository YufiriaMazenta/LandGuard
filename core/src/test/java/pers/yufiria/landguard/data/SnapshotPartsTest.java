package pers.yufiria.landguard.data;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.claim.ClaimOpResult;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 快照增量重载的安全网：范围重读只改动目标领地（摘旧插新），
 * 增量发布的结果必须与全量重读按值一致，且校验失败的 mutation 不允许发布新快照。
 */
public class SnapshotPartsTest {

    @TempDir
    Path tempDir;

    private ConnectionSource connection;
    private UUID world;
    private UUID alice;
    private OwnerRef owner;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("parts.db"));
        LandDaoManager.INSTANCE.init(connection);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        world = UUID.randomUUID();
        alice = UUID.randomUUID();
        LandDaoManager.INSTANCE.playerDao().create(new PlayerData(alice, 64, 0, System.currentTimeMillis()));
        DataStore.INSTANCE.reloadFrom(connection).join();
        owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, alice.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        DataStore.INSTANCE.joinReload();
        DataStore.INSTANCE.publish(DataSnapshot.empty());
        connection.close();
    }

    @Test
    void incrementalReloadKeepsSnapshotEqualToFullReload() throws Exception {
        assertTrue(ClaimService.INSTANCE
            .claim(owner, world, List.of(ChunkLoc.of(world, 0, 0)), "home", false).join().success());
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());

        // 同世界扩容：仍只重读这一块领地
        assertTrue(ClaimService.INSTANCE
            .claim(owner, world, List.of(ChunkLoc.of(world, 1, 0)), "home", false).join().success());
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());

        String claimId = DataStore.INSTANCE.snapshot().claimIdByChunk().get(ChunkLoc.of(world, 0, 0));
        assertTrue(ClaimService.INSTANCE.renameClaim(alice, claimId, "renamed").join().success());
        assertEquals("renamed", DataStore.INSTANCE.snapshot().claimsById().get(claimId).getName());
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());

        assertTrue(ClaimService.INSTANCE
            .unclaim(owner, List.of(ChunkLoc.of(world, 0, 0))).join().success());
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());
    }

    @Test
    void scopedChunkReloadOnlyReplacesTargetClaim() throws Exception {
        LandDaoManager daos = LandDaoManager.INSTANCE;
        daos.claimDao().create(ClaimData.builder("A", world, BuiltinOwnerTypes.PLAYER, alice.toString(), "A")
            .createdAt(1L).lastActiveAt(1L).build());
        daos.claimChunkDao().create(new ClaimChunkData("A", world, 0, 0));
        daos.claimDao().create(ClaimData.builder("B", world, BuiltinOwnerTypes.PLAYER,
            UUID.randomUUID().toString(), "B").createdAt(1L).lastActiveAt(1L).build());
        daos.claimChunkDao().create(new ClaimChunkData("B", world, 5, 5));
        DataSnapshot base = DataStore.SnapshotLoader.load(null);

        // A 换区块：(0,0) 删除、(1,0) 新增；B 完全不动
        daos.claimChunkDao().create(new ClaimChunkData("A", world, 1, 0));
        daos.claimChunkDao().deleteBuilder().where(w -> w
            .equals("claim_id", "A")
            .and().equals("world_uuid", world)
            .and().equals("chunk_x", 0)
            .and().equals("chunk_z", 0)).delete();

        DataSnapshot patched = DataStore.SnapshotLoader.loadScoped(SnapshotPart.CLAIM_CHUNK, "A", base);

        assertEquals(Set.of(ChunkLoc.of(world, 1, 0)), patched.chunksByClaim().get("A"));
        assertFalse(patched.claimIdByChunk().containsKey(ChunkLoc.of(world, 0, 0)), "旧坐标必须被摘除");
        assertEquals("A", patched.claimIdByChunk().get(ChunkLoc.of(world, 1, 0)));
        assertEquals(Set.of(ChunkLoc.of(world, 5, 5)), patched.chunksByClaim().get("B"));
        assertEquals("B", patched.claimIdByChunk().get(ChunkLoc.of(world, 5, 5)));
        SnapshotAudit.assertFresh(patched);
    }

    @Test
    void abortedMutationKeepsPublishedSnapshotInstance() {
        assertTrue(ClaimService.INSTANCE
            .claim(owner, world, List.of(ChunkLoc.of(world, 0, 0)), "home", false).join().success());
        DataSnapshot before = DataStore.INSTANCE.snapshot();

        // 重复认领同一区块：整批目标都已被占用 → OVERLAP，属校验失败路径
        ClaimOpResult overlap = ClaimService.INSTANCE
            .claim(owner, world, List.of(ChunkLoc.of(world, 0, 0)), "home", false).join();
        assertFalse(overlap.success());
        assertSame(before, DataStore.INSTANCE.snapshot(), "校验失败的 mutation 不应发布新快照");
    }

}