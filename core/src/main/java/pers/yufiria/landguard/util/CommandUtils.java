package pers.yufiria.landguard.util;

import crypticlib.BukkitPlayer;
import crypticlib.CommonPlayer;
import crypticlib.CrypticLibBukkit;
import crypticlib.Invoker;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.Languages;

import java.util.List;
import java.util.UUID;

/**
 * crypticlib 与 Bukkit 之间的双向转换收口。
 * 约定：命令/消息一律以 crypticlib 的 {@link Invoker}、{@link CommonPlayer} 为准，
 * 只有世界/坐标/库存、实体区域调度等 Bukkit 专有能力才转成 {@link Player}。
 */
public class CommandUtils {

    public static boolean checkInvokerIsPlayer(Invoker invoker) {
        if (invoker.isPlayer()) {
            return true;
        }
        LangUtils.sendLang(invoker, Languages.COMMAND_PLAYER_ONLY);
        return false;
    }

    /**
     * Invoker → Bukkit 玩家：仅在必须使用 Bukkit 专有能力时调用
     * （世界/坐标、库存、{@link CrypticLibBukkit#scheduler()} 实体区域调度等）。
     * 命令执行期间玩家必然在线，取不到属于异常状态。
     */
    public static @NotNull Player bukkitPlayer(@NotNull CommonPlayer player) {
        return player.getPlatformPlayer(Bukkit::getPlayer)
            .orElseThrow(() -> new IllegalStateException("Player " + player.name() + " is offline!"));
    }

    /**
     * Bukkit 玩家 → crypticlib CommonPlayer：事件监听、GUI 等只有 Bukkit 对象的场景，
     * 转一次后统一走 crypticlib 的消息发送路径。
     */
    public static @NotNull CommonPlayer commonPlayer(@NotNull Player player) {
        return BukkitPlayer.byPlayer(player);
    }

    /**
     * 解析玩家名 → UUID：优先在线玩家，其次本地用户缓存（离线玩家）。
     * 取不到返回 null，由调用方给出「找不到该玩家」提示。
     */
    public static @Nullable UUID resolvePlayer(@NotNull String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    /**
     * 从参数列表中解析标志值。例如：对于参数 ["--player", "Steve", "--type", "land"]和标志 "--player", 返回 "Steve"。
     */
    public static @Nullable String parseFlag(List<String> args, String flag) {
        for (int i = 0; i < args.size() - 1; i++) {
            if (args.get(i).equalsIgnoreCase(flag)) {
                return args.get(i + 1);
            }
        }
        return null;
    }

    /**
     * 检查参数列表中是否存在某个标志（不需要值）。
     */
    public static boolean hasFlag(List<String> args, String flag) {
        return args.stream().anyMatch(a -> a.equalsIgnoreCase(flag));
    }

}
