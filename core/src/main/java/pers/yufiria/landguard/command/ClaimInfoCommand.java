package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ClaimInfoCommand extends CommandNode {

    public static final ClaimInfoCommand INSTANCE = new ClaimInfoCommand();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .localizedBy(Locale.getDefault())
        .withZone(ZoneId.systemDefault());

    private ClaimInfoCommand() {
        super(CommandInfo.builder("info").permission(new PermInfo("landguard.command.info")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        CommonPlayer player = invoker.asPlayer();
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ChunkLoc standing = ChunkLoc.of(
            bukkitPlayer.getWorld().getUID(),
            bukkitPlayer.getLocation().getBlockX() >> 4,
            bukkitPlayer.getLocation().getBlockZ() >> 4
        );
        String claimId = snapshot.claimIdByChunk().get(standing);
        if (claimId == null) {
            LangUtils.sendLang(player, Languages.COMMAND_INFO_UNCLAIMED);
            return;
        }
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim == null) {
            LangUtils.sendLang(player, Languages.COMMAND_INFO_UNCLAIMED);
            return;
        }
        World world = Bukkit.getWorld(claim.getWorldUuid());
        int chunks = snapshot.chunksByClaim().getOrDefault(claimId, Set.of()).size();
        OwnerRef ownerRef = OwnerRef.of(claim.getOwnerType(), claim.getOwnerId());
        ClaimOwner resolved = ClaimOwnerRegistry.INSTANCE.resolve(ownerRef);
        String ownerName = resolved == null ? claim.getOwnerId()
            : PlainTextComponentSerializer.plainText().serialize(resolved.displayName());

        LangUtils.sendLang(player, Languages.COMMAND_INFO_HEADER);
        LangUtils.sendLang(player, Languages.COMMAND_INFO_NAME, Map.of(
            "<name>", claim.getName() == null ? claimId : claim.getName()));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_OWNER, Map.of(
            "<owner>", ownerName, "<type>", LangUtils.ownerTypeLabel(player.locale(), claim.getOwnerType())));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_WORLD, Map.of(
            "<world>", world == null ? claim.getWorldUuid().toString().substring(0, 8) : world.getName()));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_CHUNKS, Map.of("<chunks>", String.valueOf(chunks)));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_CREATED, Map.of(
            "<created>", DATE_FORMAT.format(Instant.ofEpochMilli(claim.getCreatedAt()))));
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
