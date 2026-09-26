package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
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

public final class UnclaimCommand extends CommandNode {

    public static final UnclaimCommand INSTANCE = new UnclaimCommand();

    private UnclaimCommand() {
        super(CommandInfo.builder("unclaim").permission(new PermInfo("landguard.command.unclaim")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        CommonPlayer player = invoker.asPlayer();
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);

        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("auto")) {
            AutoModeManager.Mode mode = AutoModeManager.INSTANCE.toggle(
                player.uniqueId(), AutoModeManager.Mode.UNCLAIM);
            LangUtils.sendLang(player, mode == AutoModeManager.Mode.UNCLAIM
                ? Languages.COMMAND_UNCLAIM_AUTO_ON
                : Languages.COMMAND_UNCLAIM_AUTO_OFF);
            return;
        }
        if (!args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_UNCLAIM_USAGE);
            return;
        }

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

    @Override
    public List<String> tabComplete(@NotNull Invoker invoker, @NotNull List<String> args) {
        return args.size() == 1 ? List.of("auto") : List.of();
    }

}
