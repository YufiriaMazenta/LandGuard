package pers.yufiria.landguard.util;

import crypticlib.BukkitInvoker;
import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.chat.BukkitTextProcessor;
import crypticlib.lang.entry.StringLangEntry;
import crypticlib.util.StringHelper;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.LandGuard;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 语言条目发送的唯一入口。
 * 接收者统一使用 crypticlib 的 {@link Invoker}（玩家与控制台同构），
 * 玩家侧语言按其自身 locale 解析；整个工程只有控制台出口需要一次 Bukkit CommandSender 转换。
 * <p>
 * 占位符约定：调用方传入的键必须带尖括号（{@code Map.of("<name>", value)}）。
 * crypticlib 的 {@code StringHelper.replaceStrings} 是按字面量替换键本身，不会自动补尖括号，
 * 传裸键 {@code "name"} 会把语言文件里的 {@code <name>} 替换成 {@code <值>}（尖括号残留）。
 */
public class LangUtils {

    public static void sendLang(Invoker invoker, StringLangEntry message) {
        sendLang(invoker, message, Map.of());
    }

    public static void sendLang(Invoker invoker, StringLangEntry message, Map<String, String> formatMap) {
        if (invoker == null) {
            return;
        }
        Locale locale = localeOf(invoker);
        Map<String, String> params = new HashMap<>(formatMap);
        params.put("<prefix>", locale == null ? Languages.PREFIX.value() : Languages.PREFIX.value(locale));
        params.put("<version>", LandGuard.instance().getDescription().getVersion());
        // Invoker#sendMsg 内部自带占位符替换、PlaceholderAPI 与颜色处理
        invoker.sendMsg(locale == null ? message.value() : message.value(locale), params);
    }

    /**
     * 仅 GUI（crypticlib bukkit-ui 只暴露 Bukkit Player）等必须持有 Bukkit 对象的场景使用：
     * 转换一次成 Invoker 后走统一发送路径，避免下游再混用 CommandSender。
     */
    public static void sendLang(Player player, StringLangEntry message) {
        sendLang(player, message, Map.of());
    }

    public static void sendLang(Player player, StringLangEntry message, Map<String, String> formatMap) {
        if (player == null) {
            return;
        }
        sendLang(CommandUtils.commonPlayer(player), message, formatMap);
    }

    public static void info(StringLangEntry message) {
        info(message, Map.of());
    }

    public static void info(StringLangEntry message, Map<String, String> formatMap) {
        sendLang(consoleInvoker(), message, formatMap);
    }

    /** 动作栏提示：与聊天消息同源，走 crypticlib 的占位符替换 + PlaceholderAPI + 颜色处理。 */
    public static void sendActionBar(CommonPlayer player, StringLangEntry message) {
        sendActionBar(player, message, Map.of());
    }

    public static void sendActionBar(CommonPlayer player, StringLangEntry message, Map<String, String> formatMap) {
        if (player == null) {
            return;
        }
        Locale locale = player.locale();
        Map<String, String> params = new HashMap<>(formatMap);
        params.put("<prefix>", locale == null ? Languages.PREFIX.value() : Languages.PREFIX.value(locale));
        params.put("<version>", LandGuard.instance().getDescription().getVersion());
        player.sendActionBar(locale == null ? message.value() : message.value(locale), params);
    }

    /**
     * 解析为聊天组件（与 sendMsg 完全同源：占位符 + PlaceholderAPI + 颜色）。
     * 供 crypticlib Conversation 的 {@code Prompt#promptText} 使用（它要求 BaseComponent）。
     */
    public static BaseComponent component(@NotNull CommonPlayer player, StringLangEntry message,
                                          Map<String, String> formatMap) {
        Locale locale = player.locale();
        Map<String, String> params = new HashMap<>(formatMap);
        params.put("<prefix>", locale == null ? Languages.PREFIX.value() : Languages.PREFIX.value(locale));
        params.put("<version>", LandGuard.instance().getDescription().getVersion());
        String text = locale == null ? message.value() : message.value(locale);
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        // 与 BukkitInvoker.sendMsg 一致的三步：占位符 -> PAPI -> 颜色 -> 组件
        text = StringHelper.replaceStrings(text, params);
        if (bukkitPlayer != null) {
            text = BukkitTextProcessor.placeholder(bukkitPlayer, text);
        }
        return BukkitTextProcessor.toComponent(BukkitTextProcessor.color(text));
    }

    /** 玩家取自身 locale；控制台没有 locale，走默认语言文件。 */
    public static @Nullable Locale localeOf(@NotNull Invoker invoker) {
        if (!invoker.isPlayer()) {
            return null;
        }
        CommonPlayer player = invoker.asPlayer();
        return player == null ? null : player.locale();
    }

    /**
     * 所有者类型的展示名：内置类型按语言文件本地化，第三方注册的类型回退为原始类型键。
     * locale 为 null 时取默认语言；命令层传 {@link #localeOf}，GUI 层直接传玩家的 locale。
     */
    public static String ownerTypeLabel(@Nullable Locale locale, String ownerType) {
        if (ownerType == null) {
            return "";
        }
        StringLangEntry entry = switch (ownerType.toLowerCase(Locale.ROOT)) {
            case BuiltinOwnerTypes.PLAYER -> Languages.OWNER_TYPE_PLAYER;
            case BuiltinOwnerTypes.GROUP -> Languages.OWNER_TYPE_GROUP;
            case BuiltinOwnerTypes.SERVER -> Languages.OWNER_TYPE_SERVER;
            default -> null;
        };
        if (entry == null) {
            return ownerType;
        }
        return locale == null ? entry.value() : entry.value(locale);
    }

    /** 控制台接收者：Bukkit 侧唯一必要的转换（crypticlib 未提供独立的控制台 Invoker）。 */
    private static Invoker consoleInvoker() {
        return BukkitInvoker.byCommandSender(Bukkit.getConsoleSender());
    }

}
