package pers.yufiria.landguard.command;

import crypticlib.CrypticLibBukkit;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.claim.AutoClaimManager;
import pers.yufiria.landguard.claim.ClaimBoundaryVisualizer;
import pers.yufiria.landguard.claim.ClaimEngine;
import pers.yufiria.landguard.claim.ClaimMessages;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;
import java.util.Map;

public final class ClaimCommand extends CommandNode {

    public static final ClaimCommand INSTANCE = new ClaimCommand();

    private ClaimCommand() {
        super(CommandInfo.builder("claim").permission(new PermInfo("landguard.command.claim")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        Player player = (Player) CommandUtils.invoker2Sender(invoker);

        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("auto")) {
            boolean enabled = AutoClaimManager.INSTANCE.toggle(player.getUniqueId());
            LangUtils.sendLang(player, enabled ? Languages.COMMAND_CLAIM_AUTO_ON : Languages.COMMAND_CLAIM_AUTO_OFF);
            return;
        }

        int radius = 1;
        if (!args.isEmpty()) {
            if (!args.get(0).equalsIgnoreCase("radius")) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_USAGE);
                return;
            }
            if (args.size() < 2) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
                return;
            }
            try {
                radius = Integer.parseInt(args.get(1));
            } catch (NumberFormatException e) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
                return;
            }
            if (radius < 1) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
                return;
            }
            int max = pers.yufiria.landguard.util.ConfigValues.get(ClaimConfigs.MAX_RADIUS);
            if (radius > max) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_TOO_LARGE, Map.of("max", String.valueOf(max)));
                return;
            }
        }

        List<ChunkLoc> targets = ClaimEngine.radiusTargets(
            player.getWorld().getUID(), player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4, radius);
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.getUniqueId().toString());
        ClaimService.INSTANCE.claim(owner, player.getWorld().getUID(), targets, player.getName(), false)
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    ClaimBoundaryVisualizer.show(player, targets);
                    ClaimMessages.claimSuccess(player, result);
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
