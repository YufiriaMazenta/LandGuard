package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.claim.ClaimBoundaryVisualizer;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;

/**
 * {@code /land boundary}：切换该玩家的领地粒子边界渲染。
 * 开关只作用于玩家自身且在内存中记录（与 {@code /land claim auto} 一致），重启后恢复为开启。
 */
public final class BoundaryCommand extends CommandNode {

    public static final BoundaryCommand INSTANCE = new BoundaryCommand();

    private BoundaryCommand() {
        super(CommandInfo.builder("boundary").permission(new PermInfo("landguard.command.boundary")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        CommonPlayer player = invoker.asPlayer();
        boolean enabled = ClaimBoundaryVisualizer.toggle(player.uniqueId());
        LangUtils.sendLang(player, enabled ? Languages.COMMAND_BOUNDARY_ON : Languages.COMMAND_BOUNDARY_OFF);
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
