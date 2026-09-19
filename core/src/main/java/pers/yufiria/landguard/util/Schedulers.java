package pers.yufiria.landguard.util;

import crypticlib.CrypticLibBukkit;
import org.bukkit.entity.Player;

/**
 * 调度语义工具：玩家实体相关操作（发消息以外的实体/库存访问）必须运行在其所属线程。
 * Folia 上为 EntityScheduler（无 retired 回调），Spigot/Paper 上等价于同步任务。
 */
public final class Schedulers {

    private Schedulers() {
    }

    public static void onPlayer(Player player, Runnable task) {
        CrypticLibBukkit.scheduler().runOnEntity(player, task, null);
    }
}
