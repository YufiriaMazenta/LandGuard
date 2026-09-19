package pers.yufiria.landguard.protection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TR-6.1 纯规则侧：活塞、岩浆/水、爆炸、火焰蔓延跨界判定。
 * 监听只取消/剔除被规则拒绝的方块，因此这里对「受影响集合」的断言即前后区块差异的判据。
 */
public class CrossBoundaryRulesTest {

    static final UUID WORLD = UUID.randomUUID();

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    private BlockPoint p(int x, int y, int z) {
        return new BlockPoint(WORLD, x, y, z);
    }

    /**
     * 区块 0=领地A（explosion 覆盖为允许），区块 2=领地B（默认全自然保护），其余为野外。
     */
    private DataSnapshot snapshot() {
        Map<ChunkLoc, String> byChunk = new LinkedHashMap<>();
        byChunk.put(ChunkLoc.of(WORLD, 0, 0), "A");
        byChunk.put(ChunkLoc.of(WORLD, 2, 0), "B");
        Map<String, Map<String, Map<String, Boolean>>> roleFlags = new LinkedHashMap<>();
        roleFlags.put("A", Map.of(ProtectionChecker.ENVIRONMENT_ROLE,
            Map.of(BuiltinFlags.EXPLOSION.id(), true, BuiltinFlags.FLUID_FLOW.id(), true,
                BuiltinFlags.PISTON.id(), true, BuiltinFlags.FIRE_SPREAD.id(), true)));
        return new DataSnapshot(
            new LinkedHashMap<>(), byChunk, new LinkedHashMap<>(), new LinkedHashMap<>(), roleFlags,
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>()
        );
    }

    @Test
    void explosionOnlyDestroysAllowedClaimBlocksAndWilderness() {
        DataSnapshot snapshot = snapshot();
        List<BlockPoint> affected = List.of(
            p(1, 64, 1),    // A：覆盖允许破坏
            p(40, 64, 1),   // B：默认保护
            p(80, 64, 1)    // 野外：允许
        );
        List<BlockPoint> protectedBlocks = CrossBoundaryRules.protectedExplosionBlocks(snapshot, affected);
        assertEquals(List.of(p(40, 64, 1)), protectedBlocks);
    }

    @Test
    void fluidCrossingBoundary() {
        DataSnapshot snapshot = snapshot();
        // 野外→B（默认禁流）拒绝
        assertFalse(CrossBoundaryRules.fluidAllowed(snapshot, p(20, 64, 0), p(32, 64, 0)));
        // 野外→野外放行
        assertTrue(CrossBoundaryRules.fluidAllowed(snapshot, p(80, 64, 0), p(96, 64, 0)));
        // B→野外放行
        assertTrue(CrossBoundaryRules.fluidAllowed(snapshot, p(40, 64, 0), p(48, 64, 0)));
        // B 内部流动同样受 flag 约束（默认拒绝）
        assertFalse(CrossBoundaryRules.fluidAllowed(snapshot, p(33, 64, 0), p(34, 64, 0)));
        // A 覆盖允许流动：野外→A 与内部均放行
        assertTrue(CrossBoundaryRules.fluidAllowed(snapshot, p(15, 64, 0), p(16, 64, 0)));
        assertTrue(CrossBoundaryRules.fluidAllowed(snapshot, p(2, 64, 0), p(3, 64, 0)));
    }

    @Test
    void fireCannotSpreadIntoProtectedClaim() {
        DataSnapshot snapshot = snapshot();
        // 火从野外烧入 B：拒绝
        assertFalse(CrossBoundaryRules.fireAllowed(snapshot, p(32, 64, 0)));
        // 火在野外：放行
        assertTrue(CrossBoundaryRules.fireAllowed(snapshot, p(80, 64, 0)));
        // A 覆盖允许火焰蔓延
        assertTrue(CrossBoundaryRules.fireAllowed(snapshot, p(5, 64, 0)));
    }

    @Test
    void pistonMovesAreAllOrNothingPerFlag() {
        DataSnapshot snapshot = snapshot();
        // B 边界方块被推入一格：目的地仍在 B（默认禁活塞）→拒绝
        List<BlockPoint> deniedInside = CrossBoundaryRules.deniedPistonMoves(
            snapshot, List.of(p(40, 64, 0)), 1, 0, 0);
        assertEquals(1, deniedInside.size());
        // 从野外推入 B：拒绝
        List<BlockPoint> deniedCross = CrossBoundaryRules.deniedPistonMoves(
            snapshot, List.of(p(31, 64, 0)), 1, 0, 0);
        assertEquals(1, deniedCross.size());
        // A 覆盖允许活塞：A 内部/进入 A 都放行
        assertTrue(CrossBoundaryRules.deniedPistonMoves(
            snapshot, List.of(p(15, 64, 0)), 1, 0, 0).isEmpty());
        assertTrue(CrossBoundaryRules.deniedPistonMoves(
            snapshot, List.of(p(2, 64, 0)), 1, 0, 0).isEmpty());
        // 推到野外永远放行
        assertTrue(CrossBoundaryRules.deniedPistonMoves(
            snapshot, List.of(p(47, 64, 0)), 1, 0, 0).isEmpty());
        // 混合列表：一个被拒即返回被拒集合，监听器据此取消整次事件
        List<BlockPoint> mixed = CrossBoundaryRules.deniedPistonMoves(
            snapshot, List.of(p(2, 64, 0), p(40, 64, 0)), 1, 0, 0);
        assertEquals(1, mixed.size());
    }

}
