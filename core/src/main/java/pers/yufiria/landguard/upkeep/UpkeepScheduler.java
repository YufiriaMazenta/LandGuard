package pers.yufiria.landguard.upkeep;

import crypticlib.CrypticLibBukkit;
import crypticlib.CrypticLibPlugin;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import crypticlib.scheduler.TaskWrapper;
import pers.yufiria.landguard.admin.AdminService;
import pers.yufiria.landguard.config.UpkeepConfigs;
import pers.yufiria.landguard.util.ConfigValues;

import java.util.logging.Logger;

/**
 * upkeep/不活跃回收的后台扫描调度。
 * 生命周期优先级 40：在 DataStore(0) 建快照、VaultHook(20) 挂经济之后启动；
 * RELOAD 时重启以应用新配置，DISABLE 时取消。
 *
 * 线程模型（NFR-2）：定时器运行在全局区域线程，所有判定与 JDBC 都在
 * DataStore 单写线程完成（runCycle 内部提交），Bukkit 通知再 sync 回主线程；
 * Folia 下 crypticlib 调度器无位置任务即走全局区域调度。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, priority = 40),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, priority = 40),
        @LifecycleSchedule(phase = LifecyclePhase.DISABLE)
    }
)
public enum UpkeepScheduler implements LifecycleTask {

    INSTANCE;

    private static final Logger LOGGER = Logger.getLogger("LandGuard");

    private TaskWrapper task;

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase phase) {
        if (phase == LifecyclePhase.ACTIVE || phase == LifecyclePhase.RELOAD) {
            restart();
        } else if (phase == LifecyclePhase.DISABLE && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void restart() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        int intervalSeconds = ConfigValues.get(UpkeepConfigs.TICK_INTERVAL_SECONDS);
        if (intervalSeconds <= 0) {
            return;
        }
        long intervalTicks = intervalSeconds * 20L;
        task = CrypticLibBukkit.scheduler().syncTimer(this::tick, intervalTicks, intervalTicks);
    }

    private void tick() {
        // 维护周期内含 upkeep/不活跃/孤儿三条扫描链（各自独立落库后合并通知）
        AdminService.INSTANCE.runMaintenance(System.currentTimeMillis())
            .thenAccept(result -> CrypticLibBukkit.scheduler().sync(() -> UpkeepNotifications.dispatch(result)))
            .exceptionally(throwable -> {
                LOGGER.warning("LandGuard upkeep cycle failed: " + throwable.getMessage());
                return null;
            });
    }

}
