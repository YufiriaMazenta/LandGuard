package pers.yufiria.landguard.ui;

import crypticlib.CrypticLibBukkit;
import crypticlib.ui.display.Icon;
import crypticlib.ui.display.MenuDisplay;
import crypticlib.ui.display.MenuLayout;
import crypticlib.ui.menu.Menu;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.claim.ClaimMessages;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.economy.EconomyService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 领地信息页：展示基础信息，入口包含 flag 角色切换、成员只读列表、领地银行（经济可用且玩家身处该领地时显示）、
 * 放弃脚下区块。每个操作均走与命令等价的服务路径。
 */
public class ClaimDetailMenu extends Menu {

    static final List<String> LAYOUT = List.of(
        "ggggggggg",
        "ggggigggg",
        "ggggggggg",
        "gg.fkmu..g",
        "ggggrgggg"
    );

    private final String claimId;
    private final int listPage;

    public ClaimDetailMenu(@NotNull Player player, @NotNull String claimId, int listPage) {
        super(player);
        this.claimId = claimId;
        this.listPage = listPage;
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, java.util.function.Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('i', this::infoIcon);
        icons.put('f', () -> actionIcon(Material.COMPARATOR,
            Languages.MENU_DETAIL_FLAGS_NAME, Languages.MENU_DETAIL_FLAGS_LORE,
            this::openFlags));
        icons.put('k', () -> actionIcon(Material.PLAYER_HEAD,
            Languages.MENU_DETAIL_MEMBERS_NAME, Languages.MENU_DETAIL_MEMBERS_LORE,
            this::openMembers));
        if (EconomyService.INSTANCE.available() && claimId.equals(MenuSupport.standingClaimId(player))) {
            icons.put('m', () -> actionIcon(Material.GOLD_INGOT,
                Languages.MENU_DETAIL_BANK_NAME, Languages.MENU_DETAIL_BANK_LORE,
                this::openBank));
        }
        if (player != null && player.hasPermission("landguard.command.unclaim")) {
            icons.put('u', () -> actionIcon(Material.BARRIER,
                Languages.MENU_DETAIL_UNCLAIM_NAME, Languages.MENU_DETAIL_UNCLAIM_LORE,
                this::unclaimStanding));
        }
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new ClaimListMenu(player, listPage).openMenu()));
        return new MenuDisplay(title(player), new MenuLayout(LAYOUT, icons));
    }

    private String title(Player player) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(claimId);
        String name = claim == null || claim.getName() == null ? claimId : claim.getName();
        return MenuSupport.text(player, Languages.MENU_DETAIL_TITLE, Map.of("name", name));
    }

    private Icon infoIcon() {
        Player player = player().orElse(null);
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim == null) {
            return MenuSupport.icon(Material.BARRIER, title(player), null);
        }
        World world = Bukkit.getWorld(claim.getWorldUuid());
        int chunks = snapshot.chunksByClaim().getOrDefault(claimId, Set.of()).size();
        ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(
            OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()));
        String ownerName = owner == null ? claim.getOwnerId()
            : PlainTextComponentSerializer.plainText().serialize(owner.displayName());
        List<String> lore = List.of(
            MenuSupport.text(player, Languages.MENU_DETAIL_INFO_OWNER, Map.of(
                "owner", ownerName, "type", claim.getOwnerType())),
            MenuSupport.text(player, Languages.MENU_DETAIL_INFO_WORLD, Map.of(
                "world", world == null ? claim.getWorldUuid().toString().substring(0, 8) : world.getName())),
            MenuSupport.text(player, Languages.MENU_DETAIL_INFO_CHUNKS, Map.of("chunks", String.valueOf(chunks))),
            MenuSupport.text(player, Languages.MENU_DETAIL_INFO_CREATED, Map.of(
                "created", MenuSupport.createdDate(claim)))
        );
        String name = claim.getName() == null ? claimId : claim.getName();
        return MenuSupport.icon(Material.WRITABLE_BOOK,
            MenuSupport.text(player, Languages.MENU_DETAIL_INFO_NAME, Map.of("name", name)), lore);
    }

    private Icon actionIcon(Material material, crypticlib.lang.entry.StringLangEntry nameEntry,
                            crypticlib.lang.entry.StringLangEntry loreEntry, Runnable action) {
        Player player = player().orElse(null);
        Icon icon = MenuSupport.icon(material,
            MenuSupport.text(player, nameEntry),
            List.of(MenuSupport.text(player, loreEntry)));
        icon.setClickAction(event -> action.run());
        return icon;
    }

    private void openFlags() {
        Player player = player().orElse(null);
        if (player != null) {
            new FlagRoleMenu(player, claimId, listPage).openMenu();
        }
    }

    private void openMembers() {
        Player player = player().orElse(null);
        if (player != null) {
            new MembersMenu(player, claimId, listPage).openMenu();
        }
    }

    private void openBank() {
        Player player = player().orElse(null);
        if (player == null) {
            return;
        }
        if (!claimId.equals(MenuSupport.standingClaimId(player))) {
            pers.yufiria.landguard.util.LangUtils.sendLang(player, Languages.MENU_FAIL_NOT_STANDING);
            return;
        }
        new BankMenu(player, claimId, listPage).openMenu();
    }

    private void unclaimStanding() {
        Player player = player().orElse(null);
        if (player == null) {
            return;
        }
        if (!claimId.equals(MenuSupport.standingClaimId(player))) {
            pers.yufiria.landguard.util.LangUtils.sendLang(player, Languages.MENU_FAIL_NOT_STANDING);
            return;
        }
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
                // 整块领地下最后一个区块被放弃时返回列表，否则刷新本页
                if (DataStore.INSTANCE.snapshot().claimsById().containsKey(claimId)) {
                    new ClaimDetailMenu(player, claimId, listPage).openMenu();
                } else {
                    new ClaimListMenu(player, listPage).openMenu();
                }
            }));
    }

    @Override
    public String parsedMenuTitle() {
        return title(player().orElse(null));
    }

}
