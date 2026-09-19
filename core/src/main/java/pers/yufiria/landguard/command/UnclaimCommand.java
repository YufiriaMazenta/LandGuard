package pers.yufiria.landguard.command;

import crypticlib.CrypticLibBukkit;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.claim.ClaimMessages;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
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
        Player player = (Player) CommandUtils.invoker2Sender(invoker);
        ChunkLoc standing = ChunkLoc.of(
            player.getWorld().getUID(),
            player.getLocation().getBlockX() >> 4,
            player.getLocation().getBlockZ() >> 4
        );
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.getUniqueId().toString());
        ClaimService.INSTANCE.unclaim(owner, List.of(standing))
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    ClaimMessages.unclaimSuccess(player, result);
                } else {
                    ClaimMessages.failure(player, result.failureReason());
                }
            }));
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
