package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.claim.*;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.ConfigValues;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;
import java.util.Map;

public final class ClaimCommand extends CommandNode {

    public static final ClaimCommand INSTANCE = new ClaimCommand();

    /** 认领身份标志：给出则以该用户组身份认领（缺省为本人）。 */
    public static final String FLAG_GROUP = "--group";

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

        // 标志可出现在任意位置：先剥掉 --group 再做位置参数解析
        String groupIdArg = CommandUtils.parseFlag(args, FLAG_GROUP);
        List<String> positional = CommandUtils.withoutValueFlag(args, FLAG_GROUP);
        // 裸 --group（标志后没有值）不静默降级成「以本人身份认领」，否则玩家会误以为认到了组名下
        if (groupIdArg == null && CommandUtils.hasFlag(args, FLAG_GROUP)) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_USAGE);
            return;
        }
        GroupData group = null;
        if (groupIdArg != null) {
            group = GroupService.findById(DataStore.INSTANCE.snapshot(), groupIdArg);
            if (group == null) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_GROUP_NOT_FOUND);
                return;
            }
        }

        if (!positional.isEmpty() && positional.get(0).equalsIgnoreCase("auto")) {
            AutoModeManager.AutoState state = AutoModeManager.INSTANCE.toggle(
                player.uniqueId(), AutoModeManager.Mode.CLAIM, group == null ? null : group.getGroupId());
            LangUtils.sendLang(player, state.mode() == AutoModeManager.Mode.CLAIM
                ? Languages.COMMAND_CLAIM_AUTO_ON
                : Languages.COMMAND_CLAIM_AUTO_OFF);
            return;
        }

        int radius = 1;
        if (!positional.isEmpty()) {
            if (!positional.get(0).equalsIgnoreCase("radius")) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_USAGE);
                return;
            }
            if (positional.size() < 2) {
                LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
                return;
            }
            try {
                radius = Integer.parseInt(positional.get(1));
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
        OwnerRef owner = group == null
            ? OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.uniqueId().toString())
            : OwnerRef.of(BuiltinOwnerTypes.GROUP, group.getGroupId());
        String defaultName = group == null ? player.name() : group.getName();
        AsyncReply.toPlayer(bukkitPlayer, ClaimService.INSTANCE.claim(owner, bukkitPlayer.getWorld().getUID(), targets, defaultName, false, player.uniqueId()), result -> {
            if (result.success()) {
                ClaimBoundaryVisualizer.show(bukkitPlayer, targets);
                ClaimMessages.claimSuccess(player, result);
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
        if (args.size() == 1) {
            return List.of("auto", "radius", FLAG_GROUP);
        }
        // 标志后可出现在任意位置：补全 --group 的取值
        if (args.size() >= 2 && args.get(args.size() - 2).equalsIgnoreCase(FLAG_GROUP) && invoker.isPlayer()) {
            return CommandCompletions.expandableGroups(invoker.asPlayer().uniqueId());
        }
        return List.of();
    }

}
