package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
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
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;
import pers.yufiria.landguard.util.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 重命名脚下领地：仅该领地的 owner 可操作（个人领地=本人，用户组领地=领袖），
 * 归属与名字合法性统一由 {@link ClaimService#renameClaim} 判定。
 */
public final class RenameCommand extends CommandNode {

    public static final RenameCommand INSTANCE = new RenameCommand();

    private RenameCommand() {
        super(CommandInfo.builder("rename").permission(new PermInfo("landguard.command.rename")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        CommonPlayer player = invoker.asPlayer();
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_RENAME_USAGE);
            return;
        }
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        ChunkLoc standing = ChunkLoc.of(
            bukkitPlayer.getWorld().getUID(),
            bukkitPlayer.getLocation().getBlockX() >> 4,
            bukkitPlayer.getLocation().getBlockZ() >> 4
        );
        String claimId = DataStore.INSTANCE.snapshot().claimIdByChunk().get(standing);
        if (claimId == null) {
            LangUtils.sendLang(player, Languages.COMMAND_INFO_UNCLAIMED);
            return;
        }
        // 名字允许带空格，整段参数拼接后再交给服务层 trim 与长度校验
        String name = String.join(" ", args);
        ClaimService.INSTANCE.renameClaim(player.uniqueId(), claimId, name)
            .whenComplete((result, throwable) -> Schedulers.onPlayer(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_RENAME_SUCCESS,
                        Map.of("<name>", ClaimService.normalizeClaimName(name)));
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
