package pers.yufiria.landguard.util;

import crypticlib.CrypticLibBukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 异步结果的回线程派发。写线程/异步链路的结果必须投回玩家所在区域线程（Folia 安全），
 * 并统一处理「离线 / 异常 / 空结果」三种终止条件 —— 命令与 GUI 的异步回调一律走这里，
 * 避免各处重复书写 whenComplete + runOnEntity + 三重判空。
 */
public final class AsyncReply {

    private AsyncReply() {
    }

    /** 以 Bukkit 玩家为调度目标。 */
    public static <T> void toPlayer(Player player, CompletableFuture<T> future, Consumer<T> onResult) {
        future.whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(player, () -> {
            if (throwable != null || result == null || !player.isOnline()) {
                return;
            }
            onResult.accept(result);
        }));
    }

}