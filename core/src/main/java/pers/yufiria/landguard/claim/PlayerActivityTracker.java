package pers.yufiria.landguard.claim;

import crypticlib.CrypticLibBukkit;
import crypticlib.CrypticLibPlugin;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import crypticlib.listener.EventListener;
import crypticlib.scheduler.TaskWrapper;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import pers.yufiria.landguard.config.ClaimConfigs;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏时长额度累积（防挂机）：
 * 每隔固定间隔采样所有在线玩家的水平位移，位移达标才按比例累积额度；
 * 旁观者模式与原地不动（含原地转动视角）均不累积。采样只读取 Location，无数据库 IO。
 */
@EventListener
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, priority = 2),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, priority = 2),
        @LifecycleSchedule(phase = LifecyclePhase.DISABLE)
    }
)
public enum PlayerActivityTracker implements LifecycleTask, Listener {

    INSTANCE;

    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> fractionBanks = new ConcurrentHashMap<>();
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
        }
        lastLocations.clear();
        fractionBanks.clear();
        int interval = pers.yufiria.landguard.util.ConfigValues.get(ClaimConfigs.ACTIVITY_SAMPLE_INTERVAL_TICKS);
        if (interval > 0) {
            task = CrypticLibBukkit.scheduler().syncTimer(this::sample, interval, interval);
        }
    }

    private void sample() {
        int intervalTicks = pers.yufiria.landguard.util.ConfigValues.get(ClaimConfigs.ACTIVITY_SAMPLE_INTERVAL_TICKS);
        double minMovedSq = Math.pow(
            pers.yufiria.landguard.util.ConfigValues.get(ClaimConfigs.ACTIVITY_MIN_MOVED_BLOCKS), 2);
        double chunksPerInterval = pers.yufiria.landguard.util.ConfigValues.get(ClaimConfigs.ACCRUED_CHUNKS_PER_HOUR)
            * (intervalTicks / (20D * 3600D));
        // Folia: getLocation/getGameMode 属于实体区域线程，全局定时任务只能逐实体派发采样；
        // Spigot 上 runOnEntity 等价于同步任务。
        for (Player player : Bukkit.getOnlinePlayers()) {
            CrypticLibBukkit.scheduler().runOnEntity(player,
                () -> samplePlayer(player, intervalTicks, minMovedSq, chunksPerInterval), null);
        }
    }

    private void samplePlayer(Player player, int intervalTicks, double minMovedSq, double chunksPerInterval) {
        UUID uuid = player.getUniqueId();
        Location current = player.getLocation();
        Location previous = lastLocations.put(uuid, current);
        if (player.getGameMode() == GameMode.SPECTATOR || previous == null) {
            return;
        }
        double dx = current.getX() - previous.getX();
        double dz = current.getZ() - previous.getZ();
        if (dx * dx + dz * dz < minMovedSq) {
            return;
        }
        double bank = fractionBanks.merge(uuid, chunksPerInterval, Double::sum);
        int granted = (int) Math.floor(bank);
        if (granted > 0) {
            fractionBanks.put(uuid, bank - granted);
            ClaimService.INSTANCE.accrueChunks(uuid, granted);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastLocations.remove(event.getPlayer().getUniqueId());
        fractionBanks.remove(event.getPlayer().getUniqueId());
    }

}
