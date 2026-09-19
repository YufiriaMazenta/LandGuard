package pers.yufiria.landguard.config;

import crypticlib.config.ConfigHandler;
import crypticlib.config.node.impl.bukkit.BooleanConfig;
import crypticlib.config.node.impl.bukkit.DoubleConfig;
import crypticlib.config.node.impl.bukkit.IntConfig;
import crypticlib.config.node.impl.bukkit.StringConfig;

import java.util.List;

/**
 * 认领规则与额度配置（claim.yml）。
 */
@ConfigHandler(path = "claim.yml")
public class ClaimConfigs {

    // ================= 认领规则 =================

    public static final BooleanConfig REQUIRE_ADJACENT = new BooleanConfig(
        "claim.require_adjacent",
        true,
        List.of("新建/扩容区块是否要求与本人已有领地相邻（首个领地不受限）")
    );

    public static final BooleanConfig ALLOW_DIAGONAL_ADJACENT = new BooleanConfig(
        "claim.allow_diagonal_adjacent",
        false,
        List.of("相邻判定是否把对角线相接也算相邻；false 时仅上下左右四方向")
    );

    public static final IntConfig MAX_RADIUS = new IntConfig(
        "claim.max_radius",
        16,
        List.of("/land claim radius 允许的最大半径（以站立区块为中心，1=单区块，2=3x3，以此类推）")
    );

    public static final DoubleConfig UNCLAIM_RETURN_RATIO = new DoubleConfig(
        "claim.unclaim_return_ratio",
        0.5D,
        List.of("放弃区块时返还的额度比例，0.0~1.0；0=不返还，1.0=全额返还")
    );

    // ================= 玩家额度 =================

    public static final IntConfig START_CHUNKS = new IntConfig(
        "quota.start_chunks",
        64,
        List.of("玩家首次进入服务器时获得的初始区块额度")
    );

    public static final IntConfig ACCRUED_CHUNKS_PER_HOUR = new IntConfig(
        "quota.accrued_chunks_per_hour",
        6,
        List.of(
            "每小时有效游戏时长累积的区块额度；只统计有位移的活跃时间，挂机不累积",
            "按采样间隔线性折算（例如600秒间隔、每小时6块，则每10分钟移动达标给1块）"
        )
    );

    public static final IntConfig MAX_ACCRUED_CHUNKS = new IntConfig(
        "quota.max_accrued_chunks",
        512,
        List.of("游戏时长累积额度的上限（初始额度与购买额度不受此限）")
    );

    public static final IntConfig ACTIVITY_SAMPLE_INTERVAL_TICKS = new IntConfig(
        "quota.activity_sample_interval_ticks",
        1200,
        List.of("活跃度采样间隔（tick），20tick=1秒，默认1200（60秒）；间隔内位移达标才累积额度")
    );

    public static final IntConfig ACTIVITY_MIN_MOVED_BLOCKS = new IntConfig(
        "quota.activity_min_moved_blocks",
        8,
        List.of("一个采样间隔内至少水平移动的格数，低于该值视为挂机，不累积额度；旁观者模式不累积")
    );

    // ================= 用户组额度 =================

    public static final IntConfig GROUP_BASE_CHUNKS = new IntConfig(
        "quota.group.base_chunks",
        256,
        List.of("用户组基础区块额度；组总额度 = 基础额度 + 每名成员加成 × 成员数")
    );

    public static final IntConfig GROUP_BONUS_PER_MEMBER = new IntConfig(
        "quota.group.bonus_per_member",
        16,
        List.of("用户组每名成员额外贡献的区块额度（领袖计入成员数）")
    );

    // ================= 边界可视化 =================

    public static final IntConfig BOUNDARY_DURATION_TICKS = new IntConfig(
        "visualization.boundary_duration_ticks",
        80,
        List.of("认领/查询后粒子边界的持续时间（tick），默认80（4秒）")
    );

    public static final BooleanConfig ENTER_BOUNDARY = new BooleanConfig(
        "visualization.enter_boundary",
        true,
        List.of(
            "玩家进入领地时是否用粒子渲染该领地边界",
            "玩家侧还可用 /land boundary 单独开关，两者都开启才会渲染"
        )
    );

    public static final BooleanConfig ENTER_NOTIFY_ENABLED = new BooleanConfig(
        "visualization.enter_notify.enabled",
        true,
        List.of("玩家进入领地时是否发送提示消息")
    );

    public static final StringConfig ENTER_NOTIFY_CHANNEL = new StringConfig(
        "visualization.enter_notify.channel",
        "actionbar",
        List.of(
            "提示消息的显示方式：actionbar（动作栏）、chat（聊天框）、both（两者都发）、none（不发送）",
            "无法识别的值按 actionbar 处理"
        )
    );

    public static final BooleanConfig EXIT_NOTIFY_ENABLED = new BooleanConfig(
        "visualization.exit_notify.enabled",
        true,
        List.of("玩家离开领地时是否发送提示消息（离开时不会渲染粒子边界）")
    );

    public static final StringConfig EXIT_NOTIFY_CHANNEL = new StringConfig(
        "visualization.exit_notify.channel",
        "actionbar",
        List.of(
            "离开领地时的提示通道，取值同 enter_notify.channel",
            "离开提示仅在你真正走到不属于任何领地的区域时发送；领地 A 直接穿入领地 B 不会发离开提示"
        )
    );

    public static final StringConfig BOUNDARY_PARTICLE_TYPE = new StringConfig(
        "visualization.particle.type",
        "DUST",
        List.of(
            "边界粒子类型，填 Bukkit 粒子名，例如 DUST、FLAME、END_ROD、SOUL_FIRE_FLAME、ELECTRIC_SPARK、TOTEM_OF_UNDYING",
            "DUST（1.20.5 之前叫 REDSTONE）是彩色尘埃，颜色与大小由下面的 color/size 决定",
            "需要额外数据的粒子（如 BLOCK、ITEM）不支持；无法识别的名字会回退为 DUST 并在控制台告警一次"
        )
    );

    public static final StringConfig BOUNDARY_PARTICLE_COLOR = new StringConfig(
        "visualization.particle.color",
        "#FF3B30",
        List.of("DUST 粒子的颜色，#RRGGBB 或 #AARRGGBB；仅 type 为 DUST/REDSTONE 时生效")
    );

    public static final DoubleConfig BOUNDARY_PARTICLE_SIZE = new DoubleConfig(
        "visualization.particle.size",
        1.5D,
        List.of("DUST 粒子的尺寸倍率，越大越醒目，取值范围 0.1~4.0；仅 type 为 DUST/REDSTONE 时生效")
    );

    public static final DoubleConfig BOUNDARY_PARTICLE_STEP = new DoubleConfig(
        "visualization.particle.step",
        0.5D,
        List.of("相邻粒子之间的间隔（方块），越小越密；过小会明显增加粒子包量，建议 0.25~2.0")
    );

    public static final IntConfig BOUNDARY_PARTICLE_PERIOD_TICKS = new IntConfig(
        "visualization.particle.period_ticks",
        10,
        List.of("粒子重绘周期（tick），20tick=1秒；越小越连续，但也越耗性能")
    );

    public static final DoubleConfig BOUNDARY_PARTICLE_Y_OFFSET = new DoubleConfig(
        "visualization.particle.y_offset",
        0.05D,
        List.of("边界线相对玩家脚底的高度偏移（方块），调高可避免粒子被草等地面方块遮挡")
    );

    private ClaimConfigs() {
    }

}
