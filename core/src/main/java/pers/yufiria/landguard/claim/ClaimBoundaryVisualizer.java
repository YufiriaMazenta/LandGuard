package pers.yufiria.landguard.claim;

import crypticlib.CrypticLibBukkit;
import crypticlib.scheduler.TaskWrapper;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.data.ChunkLoc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 领地粒子边界可视化：只发粒子，不放任何临时方块。
 * 沿区块组并集的外轮廓画线（接邻同组区块的边不画），避免半径批量时内部线条刷屏。
 * Particle 枚举常量在 1.20.5 由 REDSTONE 更名为 DUST，此处按名称反射兼容旧端。
 */
public final class ClaimBoundaryVisualizer {

    private static final Particle DUST_PARTICLE;

    static {
        Particle particle;
        try {
            particle = Particle.valueOf("DUST");
        } catch (IllegalArgumentException oldVersion) {
            particle = Particle.valueOf("REDSTONE");
        }
        DUST_PARTICLE = particle;
    }

    private ClaimBoundaryVisualizer() {
    }

    public static void show(@NotNull Player player, @NotNull List<ChunkLoc> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        World world = player.getWorld();
        double y = player.getLocation().getY() + 0.05D;
        List<double[]> points = outlinePoints(chunks, y);
        if (points.isEmpty()) {
            return;
        }
        Object dust = new Particle.DustOptions(Color.fromRGB(0x44DD77), 1.0F);
        int duration = Math.max(20, pers.yufiria.landguard.util.ConfigValues.get(ClaimConfigs.BOUNDARY_DURATION_TICKS));
        int period = 20;
        int[] pulsesLeft = {duration / period};
        TaskWrapper[] taskHolder = new TaskWrapper[1];
        taskHolder[0] = CrypticLibBukkit.scheduler().runOnLocationTimer(player.getLocation(), () -> {
            if (!player.isOnline() || pulsesLeft[0]-- < 0) {
                taskHolder[0].cancel();
                return;
            }
            for (double[] point : points) {
                world.spawnParticle(DUST_PARTICLE, point[0], point[1], point[2], 1, 0, 0, 0, 0, dust);
            }
        }, 0, period);
    }

    /**
     * 计算区块组外轮廓上的粒子点（每格一个）。
     */
    private static List<double[]> outlinePoints(List<ChunkLoc> chunks, double y) {
        Set<ChunkLoc> set = new HashSet<>(chunks);
        java.util.UUID worldUuid = chunks.get(0).worldUuid();
        List<double[]> points = new ArrayList<>();
        for (ChunkLoc c : set) {
            int baseX = c.x() << 4;
            int baseZ = c.z() << 4;
            // 西/东边
            if (!set.contains(ChunkLoc.of(worldUuid, c.x() - 1, c.z()))) {
                addLine(points, baseX, y, baseZ);
            }
            if (!set.contains(ChunkLoc.of(worldUuid, c.x() + 1, c.z()))) {
                addLine(points, baseX + 16, y, baseZ);
            }
            // 北/南边
            if (!set.contains(ChunkLoc.of(worldUuid, c.x(), c.z() - 1))) {
                addLine(points, baseZ, y, baseX, true);
            }
            if (!set.contains(ChunkLoc.of(worldUuid, c.x(), c.z() + 1))) {
                addLine(points, baseZ + 16, y, baseX, true);
            }
        }
        return points;
    }

    private static void addLine(List<double[]> points, int fixedX, double y, int zStart) {
        addLine(points, fixedX, y, zStart, false);
    }

    /**
     * @param swapped false：fixedX 为 X、rangeStart 为 Z 轴起边；true：fixedX 实为 Z、rangeStart 为 X 轴起边
     */
    private static void addLine(List<double[]> points, int fixedX, double y, int rangeStart, boolean swapped) {
        for (int i = 0; i <= 16; i++) {
            if (swapped) {
                points.add(new double[]{rangeStart + i, y, fixedX});
            } else {
                points.add(new double[]{fixedX, y, rangeStart + i});
            }
        }
    }

}
