package pers.yufiria.landguard;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.ClaimRoleFlagData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-12: 高频并发读 + 持续重载期间，读侧不得出现 NPE/半初始化，重载后索引与数据库一致。
 */
public class ReloadConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void readersNeverObservePartialSnapshotDuringReload() throws Exception {
        Path dbFile = tempDir.resolve("landguard-concurrent.db");
        UUID world = UUID.randomUUID();
        ConnectionSource connection = new JdbcConnectionSource("jdbc:sqlite:" + dbFile);
        LandDaoManager.INSTANCE.init(connection);
        LandDaoManager daos = LandDaoManager.INSTANCE;

        int claimCount = 64;
        int chunksPerClaim = 4;
        for (int i = 0; i < claimCount; i++) {
            String claimId = new UUID(0, i + 1).toString();
            daos.claimDao().create(new ClaimData(claimId, world, "player",
                UUID.randomUUID().toString(), null, false, 1L, 1L, 0, false));
            for (int j = 0; j < chunksPerClaim; j++) {
                daos.claimChunkDao().create(new ClaimChunkData(claimId, world, i * 2, j));
            }
            daos.roleFlagDao().create(new ClaimRoleFlagData(claimId, "visitor", "BLOCK_PLACE", false));
        }

        DataStore.INSTANCE.reloadFrom(connection).join();

        List<Throwable> readerErrors = new ArrayList<>();
        AtomicLong successfulReads = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        int readerCount = 8;
        CountDownLatch readersDone = new CountDownLatch(readerCount);
        for (int r = 0; r < readerCount; r++) {
            int seed = r;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    long endAt = System.currentTimeMillis() + 3000;
                    int i = seed;
                    while (System.currentTimeMillis() < endAt) {
                        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
                        for (int k = 0; k < 100; k++) {
                            int claimIndex = (i + k) % claimCount;
                            int chunkZ = k % chunksPerClaim;
                            String claimId = new UUID(0, claimIndex + 1).toString();
                            //每次读取都必须观察到完整一致的状态
                            assertNotNull(snapshot.claimsById().get(claimId));
                            assertNotNull(snapshot.claimAt(world, claimIndex * 2, chunkZ));
                            assertEquals(claimId, snapshot.claimIdByChunk().get(ChunkLoc.of(world, claimIndex * 2, chunkZ)));
                            assertNotNull(snapshot.roleFlagsByClaim().get(claimId).get("visitor").get("BLOCK_PLACE"));
                            successfulReads.incrementAndGet();
                        }
                        i += 7;
                    }
                } catch (Throwable t) {
                    synchronized (readerErrors) {
                        readerErrors.add(t);
                    }
                } finally {
                    readersDone.countDown();
                }
            }, "landguard-test-reader-" + r);
            thread.start();
        }

        start.countDown();
        for (int reload = 0; reload < 30; reload++) {
            DataStore.INSTANCE.reloadFrom(connection).join();
        }
        readersDone.await();

        assertTrue(readerErrors.isEmpty(),
            "reader observed inconsistent snapshot: " + (readerErrors.isEmpty() ? "" : readerErrors.get(0)));
        assertTrue(successfulReads.get() > 10000, "expected many successful reads, got " + successfulReads.get());

        DataSnapshot finalSnapshot = DataStore.INSTANCE.snapshot();
        assertEquals(claimCount, finalSnapshot.claimsById().size());
        assertEquals((long) claimCount * chunksPerClaim, finalSnapshot.claimIdByChunk().size());
        connection.close();
    }

}
