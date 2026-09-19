package pers.yufiria.landguard.config;

import crypticlib.config.ConfigHandler;
import crypticlib.config.node.impl.bukkit.BooleanConfig;
import crypticlib.config.node.impl.bukkit.DoubleConfig;
import crypticlib.config.node.impl.bukkit.IntConfig;

import java.util.List;

/**
 * upkeep.yml：维护费、欠费宽限与不活跃回收（FR-8）。
 * 时间全部以秒为单位，便于短周期测试；维护费只从领地/组银行余额扣除，
 * 不直接触碰玩家个人账户（银行存款走 /land bank deposit）。
 */
@ConfigHandler(path = "upkeep.yml")
public class UpkeepConfigs {

    // ================= 维护费 =================

    public static final BooleanConfig UPKEEP_ENABLED = new BooleanConfig(
        "upkeep.enabled",
        true,
        List.of(
            "是否按周期收取领地维护费；经济功能不可用（未装 Vault 等）时自动暂停收费",
            "维护费只从领地银行（组领地从组银行）扣除，余额不足进入欠费宽限，宽限期满自动释放"
        )
    );

    public static final DoubleConfig COST_PER_CHUNK = new DoubleConfig(
        "upkeep.cost_per_chunk",
        1D,
        List.of("每个区块每个收费周期的维护费金额")
    );

    public static final IntConfig PERIOD_SECONDS = new IntConfig(
        "upkeep.period_seconds",
        86400,
        List.of("收费周期（秒），默认 86400（每天一次）")
    );

    public static final IntConfig GRACE_SECONDS = new IntConfig(
        "upkeep.grace_seconds",
        604800,
        List.of("欠费宽限期（秒），默认 604800（7 天）；宽限期内存费并被成功扣款则解除欠费")
    );

    // ================= 不活跃回收 =================

    public static final BooleanConfig INACTIVITY_ENABLED = new BooleanConfig(
        "inactivity.enabled",
        true,
        List.of(
            "是否启用不活跃回收：个人领地按所有者最后登录判定，组领地按最近成员登录判定",
            "超过阈值先通知，越过宽限期仍不活跃则自动释放；管理领地与逐领地豁免不受影响"
        )
    );

    public static final IntConfig INACTIVITY_THRESHOLD_SECONDS = new IntConfig(
        "inactivity.threshold_seconds",
        5184000,
        List.of("不活跃阈值（秒），默认 5184000（60 天）；达到后向在线所有者成员发出警告")
    );

    public static final IntConfig INACTIVITY_GRACE_SECONDS = new IntConfig(
        "inactivity.grace_seconds",
        604800,
        List.of("不活跃警告后的宽限期（秒），默认 604800（7 天）；期间任一所有者成员登录即解除警告")
    );

    // ================= 孤儿领地 =================

    public static final BooleanConfig ORPHAN_ENABLED = new BooleanConfig(
        "orphan.enabled",
        true,
        List.of(
            "是否启用孤儿领地宽限：所有者实体消失（提供方注销、用户组解散等）的领地先进入宽限期",
            "宽限期内管理员可手动处理，宽限期满自动释放；不随经济功能开关变化"
        )
    );

    public static final IntConfig ORPHAN_GRACE_SECONDS = new IntConfig(
        "orphan.grace_seconds",
        604800,
        List.of("孤儿领地宽限期（秒），默认 604800（7 天）；期间所有者实体恢复可解析则解除孤儿状态")
    );

    // ================= 调度 =================

    public static final IntConfig TICK_INTERVAL_SECONDS = new IntConfig(
        "scheduler.tick_interval_seconds",
        3600,
        List.of("后台扫描间隔（秒），默认 3600（每小时一次）；实际收费/释放时点以领地时间戳为准")
    );

    private UpkeepConfigs() {
    }

}
