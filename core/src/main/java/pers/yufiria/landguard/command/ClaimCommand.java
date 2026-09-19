package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
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
import pers.yufiria.landguard.util.ConfigValues;
import pers.yufiria.landguard.util.LangUtils;
import pers.yufiria.landguard.util.Schedulers;

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
        CommonPlayer player = invoker.asPlayer();
        // 仅世界/坐标与实体区域调度需要 Bukkit 玩家，其余一律走 crypticlib 对象
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);

        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("auto")) {
            boolean enabled = AutoClaimManager.INSTANCE.toggle(player.uniqueId());
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
            int max = ConfigValues.get(ClaimConfigs.MAX_RADIUS);
            if (radius > max) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_TOO_LARGE, Map.of("<max>", String.valueOf(max)));
                return;
            }
        }

        List<ChunkLoc> targets = ClaimEngine.radiusTargets(
            bukkitPlayer.getWorld().getUID(), bukkitPlayer.getLocation().getBlockX() >> 4, bukkitPlayer.getLocation().getBlockZ() >> 4, radius);
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.uniqueId().toString());
        ClaimService.INSTANCE.claim(owner, bukkitPlayer.getWorld().getUID(), targets, player.name(), false)
            .whenComplete((result, throwable) -> Schedulers.onPlayer(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    ClaimBoundaryVisualizer.show(bukkitPlayer, targets);
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

    @Override
    public List<String> tabComplete(@NotNull Invoker invoker, @NotNull List<String> args) {
        return args.size() == 1 ? List.of("auto", "radius") : List.of();
    }

}
