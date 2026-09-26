package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 仅玩家可执行的子命令节点：统一处理 invoker→player 转换与无权限反馈，
 * 各子命令只关心参数解析与业务。参数列表由框架去掉子命令名后传入（不再包含动作名）。
 * <p>
 * 可选传入 {@link TabCompleter} 提供参数值补全；返回 null 表示不接管，
 * 由框架回退到子节点名/空候选。框架会按当前输入前缀过滤候选。
 */
class PlayerOnlyCommand extends CommandNode {

    /** 参数值补全：返回候选列表，null 表示不接管。 */
    @FunctionalInterface
    interface TabCompleter {

        @Nullable List<String> complete(@NotNull CommonPlayer player, @NotNull List<String> args);
    }

    private final BiConsumer<CommonPlayer, List<String>> handler;
    private final @Nullable TabCompleter completer;

    PlayerOnlyCommand(@NotNull String permission, @NotNull String name,
                      @NotNull BiConsumer<CommonPlayer, List<String>> handler) {
        this(permission, name, handler, null);
    }

    PlayerOnlyCommand(@NotNull String permission, @NotNull String name,
                      @NotNull BiConsumer<CommonPlayer, List<String>> handler,
                      @Nullable TabCompleter completer) {
        super(CommandInfo.builder(name).permission(new PermInfo(permission)).build());
        this.handler = handler;
        this.completer = completer;
    }

    @Override
    public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        handler.accept(invoker.asPlayer(), args);
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (completer == null || !invoker.isPlayer()) {
            return null;
        }
        CommonPlayer player = invoker.asPlayer();
        return player == null ? null : completer.complete(player, args);
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
