package pers.yufiria.landguard.util;

import crypticlib.config.node.impl.bukkit.BooleanConfig;
import crypticlib.config.node.impl.bukkit.DoubleConfig;
import crypticlib.config.node.impl.bukkit.IntConfig;
import crypticlib.config.node.impl.bukkit.StringConfig;

/**
 * typed config 读取兜底：crypticlib ConfigNode 构造时 value 为 null，
 * 只有平台引导加载配置文件后 value 才被填充；在未加载环境（单元测试）下回退声明的默认值。
 */
public final class ConfigValues {

    private ConfigValues() {
    }

    public static boolean get(BooleanConfig config) {
        Boolean value = config.value();
        return value != null ? value : config.def();
    }

    public static int get(IntConfig config) {
        Integer value = config.value();
        return value != null ? value : config.def();
    }

    public static double get(DoubleConfig config) {
        Double value = config.value();
        return value != null ? value : config.def();
    }

    public static String get(StringConfig config) {
        String value = config.value();
        return value != null ? value : config.def();
    }

}
