package pers.yufiria.landguard.ui;

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
import pers.yufiria.landguard.claim.ClaimBoundaryVisualizer;
import pers.yufiria.landguard.claim.ClaimEngine;
import pers.yufiria.landguard.claim.ClaimMessages;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;

import java.util.*;
import java.util.function.Supplier;

/**
 * 领地列表（/land 入口）：列出玩家可见的全部领地（个人所有 + 作为成员的组领地），
 * 分页浏览；底部提供「认领脚下区块」等价入口。所有写操作走 {@link ClaimService}，不绕过校验。
 */
public class ClaimListMenu extends Menu {

    static final int PAGE_SIZE = 45;
    static final List<String> LAYOUT = List.of(
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "pgggcgggn"
    );

    private int page;
    private final List<String> pageClaimIds = new ArrayList<>();

    public ClaimListMenu(@NotNull Player player) {
        this(player, 0);
    }

    public ClaimListMenu(@NotNull Player player, int page) {
        super(player);
        this.page = Math.max(0, page);
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('p', () -> MenuSupport.arrow(player().orElse(null), false, page > 0,
            () -> changePage(page - 1)));
        icons.put('n', () -> MenuSupport.arrow(player().orElse(null), true, page < maxPage(),
            () -> changePage(page + 1)));
        icons.put('c', this::claimHereIcon);
        return new MenuDisplay(title(), new MenuLayout(LAYOUT, icons));
    }

    private String title() {
        Player player = player().orElse(null);
        return MenuSupport.text(player, Languages.MENU_LIST_TITLE, Map.of(
            "<page>", String.valueOf(page + 1),
            "<max_page>", String.valueOf(maxPage() + 1)
        ));
    }

    private List<String> visibleClaimIds() {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        UUID viewer = playerId;
        List<String> result = new ArrayList<>();
        for (ClaimData claim : snapshot.claimsById().values()) {
            if (claim.isAdmin() || BuiltinOwnerTypes.SERVER.equals(claim.getOwnerType())) {
                continue;
            }
            ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(
                OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()));
            if (owner != null && owner.isMember(viewer)) {
                result.add(claim.getClaimId());
            }
        }
        return result;
    }

    private int maxPage() {
        return Math.max(0, (visibleClaimIds().size() - 1) / PAGE_SIZE);
    }

    private Icon claimHereIcon() {
        Player player = player().orElse(null);
        Icon icon = MenuSupport.icon(Material.GOLDEN_SHOVEL,
            MenuSupport.text(player, Languages.MENU_LIST_CLAIM_HERE_NAME),
            List.of(MenuSupport.text(player, Languages.MENU_LIST_CLAIM_HERE_LORE)));
        if (player == null || !player.hasPermission("landguard.command.claim")) {
            return icon;
        }
        icon.setClickAction(event -> claimHere());
        return icon;
    }

    private void claimHere() {
        Player player = player().orElse(null);
        if (player == null) {
            return;
        }
        List<ChunkLoc> targets = ClaimEngine.radiusTargets(
            player.getWorld().getUID(),
            player.getLocation().getBlockX() >> 4,
            player.getLocation().getBlockZ() >> 4,
            1
        );
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.getUniqueId().toString());
        AsyncReply.toPlayer(player, ClaimService.INSTANCE.claim(owner, player.getWorld().getUID(), targets, player.getName(), false), result -> {
            if (result.success()) {
                ClaimBoundaryVisualizer.show(player, targets);
                ClaimMessages.claimSuccess(CommandUtils.commonPlayer(player), result);
            } else {
                ClaimMessages.failure(CommandUtils.commonPlayer(player), result.failureReason());
            }
            refresh();
        });
    }

    @Override
    public void onLayoutUpdated() {
        pageClaimIds.clear();
        List<String> all = visibleClaimIds();
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
        }
        int start = page * PAGE_SIZE;
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        Player player = player().orElse(null);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index < all.size()) {
                String claimId = all.get(index);
                pageClaimIds.add(claimId);
                setIcon(slot, claimEntryIcon(player, snapshot, claimId, slot));
            }
        }
        if (all.isEmpty()) {
            Icon empty = MenuSupport.icon(Material.BARRIER,
                MenuSupport.text(player, Languages.MENU_LIST_EMPTY), null);
            setIcon(22, empty);
        }
    }

    private Icon claimEntryIcon(Player player, DataSnapshot snapshot, String claimId, int index) {
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim == null) {
            return MenuSupport.glass();
        }
        World world = Bukkit.getWorld(claim.getWorldUuid());
        int chunks = snapshot.chunksByClaim().getOrDefault(claimId, Set.of()).size();
        ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(
            OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()));
        String ownerName = owner == null ? claim.getOwnerId()
            : PlainTextComponentSerializer.plainText().serialize(owner.displayName());
        String name = claim.getName() == null ? claimId : claim.getName();
        List<String> lore = List.of(
            MenuSupport.text(player, Languages.MENU_LIST_ENTRY_OWNER, Map.of("<owner>", ownerName)),
            MenuSupport.text(player, Languages.MENU_LIST_ENTRY_WORLD, Map.of(
                "<world>", world == null ? claim.getWorldUuid().toString().substring(0, 8) : world.getName())),
            MenuSupport.text(player, Languages.MENU_LIST_ENTRY_CHUNKS, Map.of("<chunks>", String.valueOf(chunks)))
        );
        Icon icon = MenuSupport.icon(Material.PAPER,
            MenuSupport.text(player, Languages.MENU_LIST_ENTRY_NAME, Map.of("<name>", name)), lore);
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker == null) {
                return;
            }
            String currentId = index < pageClaimIds.size() ? pageClaimIds.get(index) : null;
            if (currentId == null) {
                return;
            }
            new ClaimDetailMenu(clicker, currentId, page).openMenu();
        });
        return icon;
    }

    private void changePage(int target) {
        if (target < 0 || target > maxPage()) {
            return;
        }
        page = target;
        refresh();
    }

    private void refresh() {
        this.display = buildDisplay();
        updateMenu(true);
    }

    @Override
    public String parsedMenuTitle() {
        return title();
    }

}
