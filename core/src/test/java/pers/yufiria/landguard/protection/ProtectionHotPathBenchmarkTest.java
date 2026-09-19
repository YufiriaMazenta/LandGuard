package pers.yufiria.landguard.protection;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerProvider;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TR-13.1 / AC-15 离线微基准：1 万认领区块（100×100 网格，每区块一个独立 claim + 独立 owner）下，
 * 模拟高频保护事件的纯 Java 判定链开销。真机 spark 报告作为补充证据（见 tasks.md Task 13）。
 *
 * 判定链每次事件的工作：ChunkLoc 哈希 O(1) 区块索引 → claim 行 → SPI owner 解析（ConcurrentHashMap 两次）
 * → roleOf → 覆盖链 → 默认矩阵。全程无 IO、无锁竞争、无列表扫描。
 */
public class ProtectionHotPathBenchmarkTest {

    static final OwnerType BENCH_TYPE = new OwnerType("bench:town");
    static final UUID WORLD = UUID.randomUUID();
    static final UUID MEMBER = UUID.randomUUID();
    static final UUID OUTSIDER = UUID.randomUUID();
    static final int GRID = 100;
    static final int CLAIM_COUNT = GRID * GRID;
    static final int BATCH = 4096;
    static final int MEASURED_BATCHES = 256; // 256 * 4096 = 1,048,576 次事件/轮
    static final int WARMUP_BATCHES = 256;
    static final int ROUNDS = 4; // 多轮取最小轮均值，规避全量构建并行负载 / GC 抖动

    static volatile long blackhole;

    @BeforeAll
    static void setUp() {
        BuiltinFlags.registerAll();
        ClaimOwnerRegistry.INSTANCE.register(new BenchProvider());
    }

    @AfterAll
    static void tearDown() {
        ClaimOwnerRegistry.INSTANCE.unregister(BENCH_TYPE);
    }

    private static DataSnapshot buildTenThousandChunkSnapshot() {
        Map<String, ClaimData> byId = new LinkedHashMap<>(CLAIM_COUNT * 2);
        Map<ChunkLoc, String> byChunk = new LinkedHashMap<>(CLAIM_COUNT * 2);
        Map<String, Set<ChunkLoc>> chunksByClaim = new LinkedHashMap<>(CLAIM_COUNT * 2);
        Map<OwnerRef, Set<String>> byOwner = new LinkedHashMap<>(CLAIM_COUNT * 2);
        long now = System.currentTimeMillis();
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                String claimId = "c-" + x + "-" + z;
                String ownerId = "town-" + x + "-" + z;
                ClaimData claim = new ClaimData(claimId, WORLD, BENCH_TYPE.key(), ownerId, claimId, false, now, now, 0D, false);
                byId.put(claimId, claim);
                ChunkLoc loc = ChunkLoc.of(WORLD, x, z);
                byChunk.put(loc, claimId);
                chunksByClaim.put(claimId, Set.of(loc));
                byOwner.computeIfAbsent(OwnerRef.of(BENCH_TYPE.key(), ownerId), k -> new LinkedHashSet<>()).add(claimId);
            }
        }
        return new DataSnapshot(
            byId, byChunk, chunksByClaim, byOwner, new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>()
        );
    }

    @Test
    void averageEventUnderFiveMicrosAcrossTenThousandClaims() {
        DataSnapshot snapshot = buildTenThousandChunkSnapshot();

        // 预热（含类加载/JIT）
        for (int i = 0; i < ROUNDS; i++) {
            runBehaviorBatches(snapshot, MEMBER, WARMUP_BATCHES / ROUNDS);
            runBehaviorBatches(snapshot, OUTSIDER, WARMUP_BATCHES / ROUNDS);
            runNaturalBatches(snapshot, WARMUP_BATCHES / ROUNDS);
        }

        double behaviorMember = minOfRounds(() -> runBehaviorBatches(snapshot, MEMBER, MEASURED_BATCHES));
        double behaviorOutsider = minOfRounds(() -> runBehaviorBatches(snapshot, OUTSIDER, MEASURED_BATCHES));
        double natural = minOfRounds(() -> runNaturalBatches(snapshot, MEASURED_BATCHES));
        double wilderness = minOfRounds(() -> runWildernessBatches(snapshot, MEASURED_BATCHES));
        double pistonBatch = minOfRounds(() -> runPistonBatches(snapshot, MEASURED_BATCHES / 4)); // 每批含 12 个方块

        System.out.println("==== TR-13.1 hot-path benchmark (10,000 claimed chunks, min of " + ROUNDS + " rounds) ====");
        System.out.printf("behavior/member   avg = %.1f ns/event%n", behaviorMember);
        System.out.printf("behavior/outsider avg = %.1f ns/event%n", behaviorOutsider);
        System.out.printf("natural           avg = %.1f ns/event%n", natural);
        System.out.printf("wilderness        avg = %.1f ns/event%n", wilderness);
        System.out.printf("piston(12 blocks) avg = %.1f ns/event%n", pistonBatch);
        System.out.println("blackhole = " + blackhole);

        // TR-13.1 锚点 5 档：平均 < 5µs 量级；取最小轮均值仍要求远低于阈值
        assertTrue(behaviorMember < 5_000D, "claimed behavior 均值应 <5us/事件，实际 " + behaviorMember + " ns");
        assertTrue(behaviorOutsider < 5_000D, "visitor behavior 均值应 <5us/事件，实际 " + behaviorOutsider + " ns");
        assertTrue(natural < 5_000D, "natural 均值应 <5us/事件，实际 " + natural + " ns");
        assertTrue(wilderness < 5_000D, "wilderness 均值应 <5us/事件，实际 " + wilderness + " ns");
        assertTrue(pistonBatch < 5_000D, "piston 单方块均值应 <5us，实际 " + pistonBatch + " ns");
    }

    private interface Round {
        double run();
    }

    private static double minOfRounds(Round round) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < ROUNDS; i++) {
            min = Math.min(min, round.run());
        }
        return min;
    }

    private double runBehaviorBatches(DataSnapshot snapshot, UUID player, int batches) {
        long sink = 0;
        long start = System.nanoTime();
        for (int b = 0; b < batches; b++) {
            for (int i = 0; i < BATCH; i++) {
                int idx = b * BATCH + i;
                int x = (idx % CLAIM_COUNT) / GRID;
                int z = (idx % CLAIM_COUNT) % GRID;
                sink += ProtectionChecker.checkBehavior(snapshot, player, WORLD, x, z, BuiltinFlags.CONTAINER)
                    .allowed() ? 1 : 0;
            }
        }
        long elapsed = System.nanoTime() - start;
        blackhole = sink;
        return elapsed / (double) (batches * BATCH);
    }

    private double runNaturalBatches(DataSnapshot snapshot, int batches) {
        long sink = 0;
        long start = System.nanoTime();
        for (int b = 0; b < batches; b++) {
            for (int i = 0; i < BATCH; i++) {
                int idx = b * BATCH + i;
                int x = (idx % CLAIM_COUNT) / GRID;
                int z = (idx % CLAIM_COUNT) % GRID;
                sink += ProtectionChecker.checkNatural(snapshot, WORLD, x, z, BuiltinFlags.EXPLOSION).allowed() ? 1 : 0;
            }
        }
        long elapsed = System.nanoTime() - start;
        blackhole ^= sink;
        return elapsed / (double) (batches * BATCH);
    }

    private double runWildernessBatches(DataSnapshot snapshot, int batches) {
        long sink = 0;
        long start = System.nanoTime();
        for (int b = 0; b < batches; b++) {
            for (int i = 0; i < BATCH; i++) {
                int idx = b * BATCH + i;
                // 网格外坐标：走野外快速路径
                int x = GRID + (idx % GRID);
                int z = GRID + ((idx / GRID) % GRID);
                sink += ProtectionChecker.checkBehavior(snapshot, OUTSIDER, WORLD, x, z, BuiltinFlags.BREAK).allowed() ? 1 : 0;
            }
        }
        long elapsed = System.nanoTime() - start;
        blackhole ^= sink;
        return elapsed / (double) (batches * BATCH);
    }

    private double runPistonBatches(DataSnapshot snapshot, int batches) {
        List<BlockPoint> blocks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            blocks.add(new BlockPoint(WORLD, i * 16 + 8, 64, (i * 37) % (GRID * 16)));
        }
        long sink = 0;
        int blockEvents = batches * blocks.size();
        long start = System.nanoTime();
        for (int b = 0; b < batches; b++) {
            sink += CrossBoundaryRules.deniedPistonMoves(snapshot, blocks, 1, 0, 0).size();
        }
        long elapsed = System.nanoTime() - start;
        blackhole ^= sink;
        return elapsed / (double) blockEvents;
    }

    // ---------------- 基准用所有者提供方：1 万个内存 owner，O(1) 解析 ----------------

    record BenchProvider() implements ClaimOwnerProvider {

        static final Map<String, BenchOwner> OWNERS = new LinkedHashMap<>();

        static {
            for (int x = 0; x < GRID; x++) {
                for (int z = 0; z < GRID; z++) {
                    String id = "town-" + x + "-" + z;
                    OWNERS.put(id, new BenchOwner(id));
                }
            }
        }

        @Override
        public @NotNull OwnerType type() {
            return BENCH_TYPE;
        }

        @Override
        public @Nullable ClaimOwner getOwner(@NotNull String identifier) {
            return OWNERS.get(identifier);
        }

        @Override
        public @NotNull Collection<ClaimOwner> ownersOf(@NotNull UUID player) {
            return MEMBER.equals(player) ? List.copyOf(OWNERS.values()) : List.of();
        }
    }

    record BenchOwner(String id) implements ClaimOwner {

        @Override
        public @NotNull OwnerType type() {
            return BENCH_TYPE;
        }

        @Override
        public @NotNull String identifier() {
            return id;
        }

        @Override
        public @NotNull Component displayName() {
            return Component.text(id);
        }

        @Override
        public @NotNull Set<UUID> members() {
            return Set.of(MEMBER);
        }

        @Override
        public @Nullable String roleOf(UUID player) {
            return MEMBER.equals(player) ? Roles.MEMBER : null;
        }
    }
}
