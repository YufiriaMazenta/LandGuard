package pers.yufiria.landguard.identity;

import crypticlib.CrypticLib;
import crypticlib.CrypticLibPlugin;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import org.bukkit.configuration.ConfigurationSection;
import pers.yufiria.landguard.LifecycleOrder;
import pers.yufiria.landguard.config.IdentityConfigs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从 {@code identities.yml} 加载身份定义。配置缺失或全部条目非法时保留注册表内置种子身份，
 * 因此行为与历史实现一致；单条非法只跳过该条并告警。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, priority = LifecycleOrder.REGISTRY),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, priority = LifecycleOrder.REGISTRY)
    }
)
public enum IdentityInitializer implements LifecycleTask {

    INSTANCE;

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase phase) {
        ConfigurationSection section = IdentityConfigs.IDENTITIES.value();
        if (section == null) {
            IdentityRegistry.INSTANCE.resetToBuiltins();
            info("&eidentities.yml 未配置身份，使用内置种子身份");
            return;
        }
        List<Identity> parsed = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            Identity identity = parse(id, entry);
            if (identity != null) {
                parsed.add(identity);
            }
        }
        if (parsed.isEmpty()) {
            IdentityRegistry.INSTANCE.resetToBuiltins();
            info("&cidentities.yml 没有任何合法身份，使用内置种子身份");
            return;
        }
        IdentityRegistry.INSTANCE.replaceAll(parsed);
    }

    private static Identity parse(String rawId, ConfigurationSection entry) {
        String id = rawId.toLowerCase(java.util.Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches()) {
            info("&c身份 id 「" + rawId + "」不合法（只允许小写字母/数字/下划线，1-32 字符），已跳过");
            return null;
        }
        String name = entry.getString("name", id);
        int priority = entry.getInt("priority", 0);
        boolean leader = entry.getBoolean("leader", false);
        boolean isDefault = entry.getBoolean("default", false);
        Set<String> permissions = new LinkedHashSet<>();
        for (String raw : entry.getStringList("permissions")) {
            PermissionPoint point = PermissionPoint.byKey(raw);
            if (point == null) {
                info("&e身份 " + id + " 的权限点「" + raw + "」无法识别，已忽略");
                continue;
            }
            permissions.add(point.key());
        }
        Set<String> behaviors = new LinkedHashSet<>(entry.getStringList("behaviors"));
        if (leader && isDefault) {
            info("&e身份 " + id + " 同时标记了 leader 与 default，仅 leader 生效");
            isDefault = false;
        }
        return new Identity(id, name, priority, leader, isDefault, permissions, behaviors);
    }

    private static void info(String message) {
        try {
            CrypticLib.info(message);
        } catch (Throwable platformUnavailable) {
            System.out.println("[LandGuard] " + message);
        }
    }

}
