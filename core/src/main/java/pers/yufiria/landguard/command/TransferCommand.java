package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.claim.ClaimMessages;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;
import pers.yufiria.landguard.util.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把脚下领地转让给「玩家或用户组」：仅该领地的 owner 可操作
 * （个人领地=本人，用户组领地=领袖）。
 * <p>
 * 目标用标志显式指定，避免玩家名与用户组名重名时的歧义：
 * <ul>
 *   <li>{@code /land transfer --player <玩家名>} → 归属变为该玩家个人所有（{@link ClaimService#transferClaim}）</li>
 *   <li>{@code /land transfer --group <用户组名>} → 归属变为该用户组（{@link GroupService#giveClaim}，
 *       要求脚下领地属于操作者本人）</li>
 * </ul>
 * 两个标志都不给或同时给出时输出用法。
 */
public final class TransferCommand extends CommandNode {

    public static final TransferCommand INSTANCE = new TransferCommand();

    /** 目标玩家标志。 */
    public static final String FLAG_PLAYER = "--player";
    /** 目标用户组标志。 */
    public static final String FLAG_GROUP = "--group";

    private TransferCommand() {
        super(CommandInfo.builder("transfer").permission(new PermInfo("landguard.command.transfer")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        CommonPlayer player = invoker.asPlayer();
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        String claimId = standingClaimId(bukkitPlayer);
        if (claimId == null) {
            LangUtils.sendLang(player, Languages.COMMAND_INFO_UNCLAIMED);
            return;
        }
        transfer(player, bukkitPlayer, claimId, args, null);
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!invoker.isPlayer()) {
            return null;
        }
        CommonPlayer player = invoker.asPlayer();
        if (player == null) {
            return List.of();
        }
        if (args.size() == 1) {
            return List.of(FLAG_PLAYER, FLAG_GROUP);
        }
        if (args.size() == 2) {
            String flag = args.getFirst();
            if (flag.equalsIgnoreCase(FLAG_PLAYER)) {
                return CommandCompletions.onlinePlayers();
            }
            if (flag.equalsIgnoreCase(FLAG_GROUP)) {
                return CommandCompletions.managedGroups(player.uniqueId());
            }
        }
        return List.of();
    }

    /**
     * 按 {@code --player <名>} / {@code --group <名>} 解析目标并执行转让。
     * 命令层与 GUI 共用，{@code onSuccess} 用于 GUI 转让后刷新界面。
     */
    public static void transfer(@NotNull CommonPlayer player, @NotNull Player bukkitPlayer,
                                @NotNull String claimId, @NotNull List<String> args,
                                @Nullable Runnable onSuccess) {
        String playerName = CommandUtils.parseFlag(args, FLAG_PLAYER);
        String groupName = CommandUtils.parseFlag(args, FLAG_GROUP);
        // 两个标志必须且只能给一个，否则无法确定转让目标
        if ((playerName == null) == (groupName == null)) {
            LangUtils.sendLang(player, Languages.COMMAND_TRANSFER_USAGE);
            return;
        }
        if (playerName != null) {
            UUID target = CommandUtils.resolvePlayer(playerName);
            if (target == null) {
                LangUtils.sendLang(player, Languages.COMMAND_TRANSFER_FAIL_PLAYER_NOT_FOUND);
                return;
            }
            ClaimService.INSTANCE.transferClaim(player.uniqueId(), claimId, target)
                .whenComplete((result, throwable) -> Schedulers.onPlayer(bukkitPlayer, () -> {
                    if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                        return;
                    }
                    if (result.success()) {
                        LangUtils.sendLang(player, Languages.COMMAND_TRANSFER_SUCCESS,
                            Map.of("<player>", playerName));
                        runQuietly(onSuccess);
                    } else {
                        ClaimMessages.failure(player, result.failureReason());
                    }
                }));
            return;
        }
        GroupData group = GroupService.findById(DataStore.INSTANCE.snapshot(), groupName);
        if (group == null) {
            LangUtils.sendLang(player, Languages.COMMAND_TRANSFER_FAIL_GROUP_NOT_FOUND);
            return;
        }
        GroupService.INSTANCE.giveClaim(player.uniqueId(), group.getName(),
                bukkitPlayer.getWorld().getUID(),
                bukkitPlayer.getLocation().getBlockX() >> 4,
                bukkitPlayer.getLocation().getBlockZ() >> 4)
            .whenComplete((result, throwable) -> Schedulers.onPlayer(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_TRANSFER_GROUP_SUCCESS,
                        Map.of("<group>", group.getName()));
                    runQuietly(onSuccess);
                } else {
                    GroupCommand.sendFailure(player, result);
                }
            }));
    }

    private static @Nullable String standingClaimId(Player player) {
        ChunkLoc standing = ChunkLoc.of(
            player.getWorld().getUID(),
            player.getLocation().getBlockX() >> 4,
            player.getLocation().getBlockZ() >> 4
        );
        return DataStore.INSTANCE.snapshot().claimIdByChunk().get(standing);
    }

    private static void runQuietly(@Nullable Runnable task) {
        if (task != null) {
            task.run();
        }
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
