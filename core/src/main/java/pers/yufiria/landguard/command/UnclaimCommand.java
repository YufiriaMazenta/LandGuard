package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.command.annotation.Subcommand;
import crypticlib.perm.PermInfo;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.claim.AutoModeManager;
import pers.yufiria.landguard.claim.ClaimMessages;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;

/**
 * {@code /land unclaim ...}：放弃脚下所属领地、切换行走自动放弃。
 * {@code auto} 交由框架节点树分派（{@code @Subcommand}）为独立子节点，
 * 只有无子命令（{@code /land unclaim}）时才回落到本节点的 {@link #execute} 做就地放弃。
 * 两个入口共用同一权限节点 {@code landguard.command.unclaim}。
 */
public final class UnclaimCommand extends CommandNode {

    public static final UnclaimCommand INSTANCE = new UnclaimCommand();

    private static final String PERMISSION = "landguard.command.unclaim";

    private UnclaimCommand() {
        super(CommandInfo.builder("unclaim").permission(new PermInfo(PERMISSION)).build());
    }

    /** {@code /land unclaim auto}：切换行走自动放弃（不涉及身份，用兼容重载，等价于以本人身份）。 */
    @Subcommand
    CommandNode auto = new PlayerOnlyCommand(PERMISSION, "auto", (player, args) -> {
        if (!args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_UNCLAIM_USAGE);
            return;
        }
        AutoModeManager.AutoState state = AutoModeManager.INSTANCE.toggle(
            player.uniqueId(), AutoModeManager.Mode.UNCLAIM);
        LangUtils.sendLang(player, state.mode() == AutoModeManager.Mode.UNCLAIM
            ? Languages.COMMAND_UNCLAIM_AUTO_ON
            : Languages.COMMAND_UNCLAIM_AUTO_OFF);
    });

    /** 无子命令（或子命令名未命中）：放弃脚下区块。 */
    @Override
    public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        CommonPlayer player = invoker.asPlayer();
        if (!args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_UNCLAIM_USAGE);
            return;
        }

        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        ChunkLoc standing = ChunkLoc.of(
            bukkitPlayer.getWorld().getUID(),
            bukkitPlayer.getLocation().getBlockX() >> 4,
            bukkitPlayer.getLocation().getBlockZ() >> 4
        );
        AsyncReply.toPlayer(bukkitPlayer, ClaimService.INSTANCE.unclaimOwnedBy(player.uniqueId(), standing), result -> {
            if (result.success()) {
                ClaimMessages.unclaimSuccess(player, result);
            } else {
                ClaimMessages.failure(player, result.failureReason());
            }
        });
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
