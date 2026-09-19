package pers.yufiria.landguard.config;

import crypticlib.config.ConfigHandler;
import crypticlib.config.node.impl.bukkit.BooleanConfig;
import crypticlib.config.node.impl.bukkit.DoubleConfig;
import crypticlib.config.node.impl.bukkit.IntConfig;

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

    private ClaimConfigs() {
    }

}
