package pers.yufiria.landguard.config;

import crypticlib.config.ConfigHandler;
import crypticlib.config.node.impl.bukkit.BooleanConfig;
import crypticlib.config.node.impl.bukkit.IntConfig;
import crypticlib.config.node.impl.bukkit.StringListConfig;

import java.util.List;

@ConfigHandler(path = "config.yml")
public class PluginConfigs {

    public final static BooleanConfig BSTATS = new BooleanConfig(
        "bstats",
        true,
        "是否允许插件通过bStats收集使用信息"
    );

    public final static BooleanConfig DEBUG = new BooleanConfig("debug", false);

    public final static StringListConfig MAIN_COMMAND_ALIASES = new StringListConfig(
        "main_command_aliases",
        List.of("landguard", "lg"),
        List.of("插件主命令的别名，只在插件启动时读取一次")
    );

    public final static IntConfig OWNER_MEMBERSHIP_REVALIDATE_INTERVAL_TICKS = new IntConfig(
        "owner.membership_revalidate_interval_ticks",
        1200,
        List.of(
            "所有者成员资格的周期性全量校正间隔（tick），20tick=1秒，默认1200（60秒）",
            "作为第三方组织插件漏发成员变更事件时的兜底，设为0可关闭"
        )
    );

}
