package pers.yufiria.landguard.claim;

import crypticlib.CrypticLibBukkit;
import crypticlib.particle.pobject.EffectGroup;
import crypticlib.particle.pobject.Line;
import crypticlib.particle.pobject.ParticleObject;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.util.ConfigValues;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 领地粒子边界可视化：只发粒子，不放任何临时方块。
 * 沿区块组并集的外轮廓画线（接邻同组区块的边不画），避免半径批量时内部线条刷屏。
 * 粒子绘制复用 crypticlib 的 bukkit-particle 模块（EffectLib 封装）：{@link Line} 负责撒点，
 * {@link EffectGroup} 负责批量配置与启停；Folia 区域调度由框架内部处理。
 * 粒子类型/颜色/大小/密度/周期/高度均可在 claim.yml 的 visualization.particle 节调整。
 * 玩家可用 {@code /land boundary} 关闭渲染，开关按玩家在内存中记录。
 */
public final class ClaimBoundaryVisualizer {

    private static final Logger LOGGER = Logger.getLogger("LandGuard");

    /** 粒子配置非法时的兜底颜色 */
    private static final Color FALLBACK_COLOR = Color.fromRGB(0xFF3B30);
    /** DUST 粒子尺寸的可用区间，避免配置笔误导致粒子过小不可见或过大糊屏 */
    private static final double MIN_SIZE = 0.1D;
    private static final double MAX_SIZE = 4.0D;
    /** 步长下限，防止配置成 0 导致死循环式撒点 */
    private static final double MIN_STEP = 0.05D;

    /**
     * 1.20.5 起 REDSTONE 更名为 DUST。
     * 框架的 {@code ParticleObject.REDSTONE} 是 protected，此处按名称自行做一次版本兼容解析。
     */
    private static final Particle DUST_PARTICLE = resolveDustParticle();

    /** 已告警过的配置问题，避免每轮粒子重绘都刷控制台 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** 关闭了边界渲染的玩家；默认全部开启，故记录关闭者而非开启者。 */
    private static final Set<UUID> DISABLED = ConcurrentHashMap.newKeySet();

    /** 玩家当前正在渲染的特效组，用于重复触发或退出时关掉上一组。 */
    private static final Map<UUID, EffectGroup> ACTIVE = new ConcurrentHashMap<>();

    private ClaimBoundaryVisualizer() {
    }

    public static boolean isEnabled(UUID playerUuid) {
        return !DISABLED.contains(playerUuid);
    }

    /**
     * 切换该玩家的边界渲染开关。
     *
     * @return 切换后的状态，true 为已开启
     */
    public static boolean toggle(UUID playerUuid) {
        if (DISABLED.remove(playerUuid)) {
            return true;
        }
        DISABLED.add(playerUuid);
        return false;
    }

    public static void show(@NotNull Player player, @NotNull List<ChunkLoc> chunks) {
        if (chunks.isEmpty() || !isEnabled(player.getUniqueId())) {
            return;
        }
        double step = Math.max(MIN_STEP, ConfigValues.get(ClaimConfigs.BOUNDARY_PARTICLE_STEP));
        long period = Math.max(1, ConfigValues.get(ClaimConfigs.BOUNDARY_PARTICLE_PERIOD_TICKS));
        double yOffset = ConfigValues.get(ClaimConfigs.BOUNDARY_PARTICLE_Y_OFFSET);

        EffectGroup group = outline(player.getWorld(), chunks, player.getLocation().getY() + yOffset, step, period);
        if (group == null) {
            return;
        }
        ParticleStyle style = particleStyle();
        group.setParticle(style.particle());
        if (style.data() != null) {
            group.setData(style.data());
        }

        turnOff(ACTIVE.put(player.getUniqueId(), group));
        group.alwaysShow();
        long duration = Math.max(period, ConfigValues.get(ClaimConfigs.BOUNDARY_DURATION_TICKS));
        CrypticLibBukkit.scheduler().runOnLocationLater(player.getLocation(), () -> {
            if (ACTIVE.remove(player.getUniqueId(), group)) {
                turnOff(group);
            }
        }, duration);
    }

    /** 清理该玩家的边界渲染，避免玩家离开后特效仍在无人可见的位置发包。 */
    public static void clear(@NotNull UUID playerUuid) {
        turnOff(ACTIVE.remove(playerUuid));
    }

    private static void turnOff(EffectGroup group) {
        if (group == null) {
            return;
        }
        group.effectList().forEach(ParticleObject::turnOffTask);
    }

    /**
     * 把区块组外轮廓拆成若干条边，每条边交给一个 {@link Line}。
     *
     * @return 拼好的特效组；没有任何外露边时返回 null
     */
    private static EffectGroup outline(World world, List<ChunkLoc> chunks, double y, double step, long period) {
        Set<ChunkLoc> set = new HashSet<>(chunks);
        UUID worldUuid = chunks.get(0).worldUuid();
        List<ParticleObject> lines = new ArrayList<>();
        for (ChunkLoc c : set) {
            int baseX = c.x() << 4;
            int baseZ = c.z() << 4;
            // 西/东边：沿 Z 轴延伸
            if (!set.contains(ChunkLoc.of(worldUuid, c.x() - 1, c.z()))) {
                lines.add(side(world, baseX, y, baseZ, baseZ + 16, false, step, period));
            }
            if (!set.contains(ChunkLoc.of(worldUuid, c.x() + 1, c.z()))) {
                lines.add(side(world, baseX + 16, y, baseZ, baseZ + 16, false, step, period));
            }
            // 北/南边：沿 X 轴延伸
            if (!set.contains(ChunkLoc.of(worldUuid, c.x(), c.z() - 1))) {
                lines.add(side(world, baseZ, y, baseX, baseX + 16, true, step, period));
            }
            if (!set.contains(ChunkLoc.of(worldUuid, c.x(), c.z() + 1))) {
                lines.add(side(world, baseZ + 16, y, baseX, baseX + 16, true, step, period));
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        return new EffectGroup(lines).setPeriod(period);
    }

    /**
     * @param fixed  固定在另一条轴上的坐标
     * @param from   延伸轴上的起点
     * @param to     延伸轴上的终点
     * @param alongX true 表示沿 X 轴延伸（fixed 为 Z），false 表示沿 Z 轴延伸（fixed 为 X）
     */
    private static Line side(World world, double fixed, double y, double from, double to,
                             boolean alongX, double step, long period) {
        Location start = alongX ? new Location(world, from, y, fixed) : new Location(world, fixed, y, from);
        Location end = alongX ? new Location(world, to, y, fixed) : new Location(world, fixed, y, to);
        // Line#show 的循环条件是 i < length，终点本身不会被撒点，这里把终点外扩一个步长补上拐角。
        Vector over = end.clone().subtract(start).toVector().normalize().multiply(step);
        return new Line(start, end.clone().add(over), step, period);
    }

    /**
     * 读取 claim.yml 的粒子配置。DUST/REDSTONE 走 {@link Particle.DustOptions} 以支持自定义颜色与尺寸；
     * 其它粒子只按名字使用，需要额外数据的粒子（BLOCK、ITEM 等）不受支持，统一回退为 DUST。
     */
    private static ParticleStyle particleStyle() {
        String configured = ConfigValues.get(ClaimConfigs.BOUNDARY_PARTICLE_TYPE).trim().toUpperCase(Locale.ROOT);
        Particle particle = parseParticle(configured);
        if (particle == null) {
            warnOnce("type:" + configured,
                "未知的粒子类型 '" + configured + "'，边界渲染回退为 DUST");
            particle = DUST_PARTICLE;
        }
        if (particle.getDataType() == Particle.DustOptions.class) {
            return new ParticleStyle(particle, new Particle.DustOptions(particleColor(), particleSize()));
        }
        if (particle.getDataType() != Void.class) {
            warnOnce("data:" + configured,
                "粒子 '" + configured + "' 需要额外数据，暂不支持，边界渲染回退为 DUST");
            return new ParticleStyle(DUST_PARTICLE, new Particle.DustOptions(particleColor(), particleSize()));
        }
        return new ParticleStyle(particle, null);
    }

    private static Particle resolveDustParticle() {
        try {
            return Particle.valueOf("DUST");
        } catch (IllegalArgumentException newerNameMissing) {
            return Particle.valueOf("REDSTONE");
        }
    }

    private static Particle parseParticle(String name) {
        // DUST/REDSTONE 是同一个粒子的新旧名称，统一解析成当前服务端存在的那个
        if (name.equals("DUST") || name.equals("REDSTONE")) {
            return DUST_PARTICLE;
        }
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static Color particleColor() {
        String raw = ConfigValues.get(ClaimConfigs.BOUNDARY_PARTICLE_COLOR).trim();
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            int value = Integer.parseInt(hex, 16);
            return hex.length() > 6 ? Color.fromARGB(value) : Color.fromRGB(value);
        } catch (NumberFormatException invalid) {
            warnOnce("color:" + raw, "无法解析的粒子颜色 '" + raw + "'，回退为 #FF3B30");
            return FALLBACK_COLOR;
        }
    }

    private static float particleSize() {
        double size = ConfigValues.get(ClaimConfigs.BOUNDARY_PARTICLE_SIZE);
        return (float) Math.min(MAX_SIZE, Math.max(MIN_SIZE, size));
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOGGER.warning("LandGuard claim.yml visualization.particle 配置有误：" + message);
        }
    }

    /** 解析后的粒子外观：data 为空表示该粒子不需要额外数据。 */
    private record ParticleStyle(Particle particle, Object data) {
    }

}
