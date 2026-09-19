package pers.yufiria.landguard.command;

import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClaimListCommand extends CommandNode {

    public static final ClaimListCommand INSTANCE = new ClaimListCommand();

    private ClaimListCommand() {
        super(CommandInfo.builder("list").permission(new PermInfo("landguard.command.list")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        Player player = (Player) CommandUtils.invoker2Sender(invoker);
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.getUniqueId().toString());
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        Set<String> claimIds = snapshot.claimsByOwner().getOrDefault(owner, Set.of());
        if (claimIds.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_LIST_EMPTY);
            return;
        }
        LangUtils.sendLang(player, Languages.COMMAND_LIST_HEADER, Map.of("size", String.valueOf(claimIds.size())));
        for (String claimId : claimIds) {
            ClaimData claim = snapshot.claimsById().get(claimId);
            if (claim == null) {
                continue;
            }
            World world = Bukkit.getWorld(claim.getWorldUuid());
            int chunks = snapshot.chunksByClaim().getOrDefault(claimId, Set.of()).size();
            LangUtils.sendLang(player, Languages.COMMAND_LIST_ENTRY, Map.of(
                "name", claim.getName() == null ? claimId : claim.getName(),
                "world", world == null ? claim.getWorldUuid().toString().substring(0, 8) : world.getName(),
                "chunks", String.valueOf(chunks)
            ));
        }
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
