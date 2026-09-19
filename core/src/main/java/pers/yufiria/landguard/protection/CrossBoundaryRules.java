package pers.yufiria.landguard.protection;

import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 跨界自然过程的纯规则（FR-5.2）：活塞推拉、流体流动、爆炸、火焰蔓延。
 * 唯一判据：目标/被影响方块所在区块的归属与自然 flag；野外目标永不拦截。
 */
public final class CrossBoundaryRules {

    private CrossBoundaryRules() {
    }

    private static String claimAt(DataSnapshot snapshot, UUID world, int chunkX, int chunkZ) {
        return snapshot.claimIdByChunk().get(ChunkLoc.of(world, chunkX, chunkZ));
    }

    /**
     * 流体从 from 流向 to 是否放行。目标为野外永远放行；
     * 目标在领地内时按该领地 fluid_flow 判定（同领地内部流动同样受 flag 约束）。
     */
    public static boolean fluidAllowed(@NotNull DataSnapshot snapshot, @NotNull BlockPoint from, @NotNull BlockPoint to) {
        String targetClaim = claimAt(snapshot, to.worldUuid(), to.chunkX(), to.chunkZ());
        if (targetClaim == null) {
            return true;
        }
        return ProtectionChecker.checkNatural(snapshot, to.worldUuid(), to.chunkX(), to.chunkZ(), BuiltinFlags.FLUID_FLOW)
            .allowed();
    }

    /**
     * 火焰是否允许蔓延到 to（起火方块）。
     */
    public static boolean fireAllowed(@NotNull DataSnapshot snapshot, @NotNull BlockPoint to) {
        if (claimAt(snapshot, to.worldUuid(), to.chunkX(), to.chunkZ()) == null) {
            return true;
        }
        return ProtectionChecker.checkNatural(snapshot, to.worldUuid(), to.chunkX(), to.chunkZ(), BuiltinFlags.FIRE_SPREAD)
            .allowed();
    }

    /**
     * 活塞把 blocks 从各自当前位置朝 direction 推一格后，哪些方块不得移动（null 目的地代表推出世界边界，视为野外放行）。
     * 任一方块被拒则监听应取消整个事件（活塞全有或全无），返回值仅供日志/测试断言。
     */
    public static @NotNull List<BlockPoint> deniedPistonMoves(@NotNull DataSnapshot snapshot,
                                                             @NotNull List<BlockPoint> blocks,
                                                             int dirX, int dirY, int dirZ) {
        List<BlockPoint> denied = new ArrayList<>();
        for (BlockPoint block : blocks) {
            BlockPoint destination = new BlockPoint(
                block.worldUuid(), block.x() + dirX, block.y() + dirY, block.z() + dirZ);
            String targetClaim = claimAt(snapshot, destination.worldUuid(), destination.chunkX(), destination.chunkZ());
            if (targetClaim == null) {
                continue;
            }
            // 推出野外→领地、跨界进入他人领地、乃至同领地内移动，都按目的地 piston flag 判定
            if (!ProtectionChecker.checkNatural(
                snapshot, destination.worldUuid(), destination.chunkX(), destination.chunkZ(), BuiltinFlags.PISTON
            ).allowed()) {
                denied.add(destination);
            }
        }
        return denied;
    }

    /**
     * 爆炸影响方块中必须保留（从破坏列表剔除）的方块：位于关闭 explosion 的领地内。
     */
    public static @NotNull List<BlockPoint> protectedExplosionBlocks(@NotNull DataSnapshot snapshot,
                                                                    @NotNull List<BlockPoint> blocks) {
        List<BlockPoint> protectedBlocks = new ArrayList<>();
        for (BlockPoint block : blocks) {
            if (claimAt(snapshot, block.worldUuid(), block.chunkX(), block.chunkZ()) == null) {
                continue;
            }
            if (!ProtectionChecker.checkNatural(
                snapshot, block.worldUuid(), block.chunkX(), block.chunkZ(), BuiltinFlags.EXPLOSION
            ).allowed()) {
                protectedBlocks.add(block);
            }
        }
        return protectedBlocks;
    }

}
