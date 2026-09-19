package pers.yufiria.landguard.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.DatabaseConfigs;
import pers.yufiria.landguard.config.EconomyConfigs;
import pers.yufiria.landguard.config.PluginConfigs;
import pers.yufiria.landguard.data.DataStore;

/**
 * bStats 接入管理。
 * SERVICE_ID 在 bStats 平台注册后填入（见 Task 13 发布物料），未注册前不初始化上报；
 * 图表注册不依赖 SERVICE_ID，ID 填入后自动随上报生效。
 */
public enum MetricsManager {

    INSTANCE;

    // TODO: 在 https://bstats.org 注册插件后替换为正式 SERVICE_ID
    private static final int SERVICE_ID = -1;

    private @Nullable Metrics metrics;

    public void init(JavaPlugin plugin) {
        if (!PluginConfigs.BSTATS.value()) {
            return;
        }
        if (SERVICE_ID <= 0) {
            return;
        }
        if (metrics == null) {
            metrics = new Metrics(plugin, SERVICE_ID);
            registerCharts(metrics);
        }
    }

    private void registerCharts(Metrics metrics) {
        metrics.addCustomChart(new SingleLineChart("claimed_chunks",
            () -> DataStore.INSTANCE.snapshot().claimIdByChunk().size()));
        metrics.addCustomChart(new SingleLineChart("claims",
            () -> DataStore.INSTANCE.snapshot().claimsById().size()));
        metrics.addCustomChart(new SimplePie("database_type",
            () -> String.valueOf(DatabaseConfigs.TYPE.value())));
        metrics.addCustomChart(new SimplePie("economy_enabled",
            () -> String.valueOf(EconomyConfigs.ENABLED.value())));
        metrics.addCustomChart(new SimplePie("platform", MetricsManager::detectPlatform));
    }

    private static String detectPlatform() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return "Folia";
        } catch (ClassNotFoundException ignored) {
            // 非 Folia，继续判定
        }
        String serverName = Bukkit.getServer() == null ? "Unknown" : Bukkit.getServer().getName();
        if (serverName != null && serverName.contains("Paper")) {
            return "Paper";
        }
        if (serverName != null && serverName.contains("Spigot")) {
            return "Spigot";
        }
        return serverName == null ? "Unknown" : serverName;
    }

    public @Nullable Metrics metrics() {
        return metrics;
    }

}
