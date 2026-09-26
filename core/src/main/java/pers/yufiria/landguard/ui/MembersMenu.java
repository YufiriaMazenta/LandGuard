package pers.yufiria.landguard.ui;

import crypticlib.ui.display.Icon;
import crypticlib.ui.display.MenuDisplay;
import crypticlib.ui.display.MenuLayout;
import crypticlib.ui.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;

import java.util.*;
import java.util.function.Supplier;

/**
 * 成员只读列表：展示当前所有者实体的全部成员及其角色标识。
 * 成员变更归属所有者体系（个人无成员；用户组走 /land group），本页不提供编辑操作。
 */
public class MembersMenu extends Menu {

    static final int PAGE_SIZE = 45;
    static final List<String> LAYOUT = List.of(
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "pghgrgggn"
    );

    private final String claimId;
    private final int listPage;
    private int page;
    private final List<UUID> pageMembers = new ArrayList<>();

    public MembersMenu(@NotNull Player player, @NotNull String claimId, int listPage) {
        super(player);
        this.claimId = claimId;
        this.listPage = listPage;
        this.page = 0;
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('p', () -> MenuSupport.arrow(player, false, page > 0, () -> changePage(page - 1)));
        icons.put('n', () -> MenuSupport.arrow(player, true, page < maxPage(memberIds()),
            () -> changePage(page + 1)));
        icons.put('h', () -> MenuSupport.icon(Material.KNOWLEDGE_BOOK,
            MenuSupport.text(player, Languages.MENU_MEMBERS_HINT), null));
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new ClaimDetailMenu(player, claimId, listPage).openMenu()));
        return new MenuDisplay(title(), new MenuLayout(LAYOUT, icons));
    }

    private String title() {
        Player player = player().orElse(null);
        ClaimData claim = DataStore.INSTANCE.snapshot().claimsById().get(claimId);
        String name = claim == null || claim.getName() == null ? claimId : claim.getName();
        return MenuSupport.text(player, Languages.MENU_MEMBERS_TITLE, Map.of("<name>", name));
    }

    private List<UUID> memberIds() {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim == null) {
            return List.of();
        }
        ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(
            OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()));
        if (owner == null) {
            return List.of();
        }
        List<UUID> members = new ArrayList<>(owner.members());
        members.sort(UUID::compareTo);
        return members;
    }

    private int maxPage(List<UUID> members) {
        return Math.max(0, (members.size() - 1) / PAGE_SIZE);
    }

    @Override
    public void onLayoutUpdated() {
        pageMembers.clear();
        List<UUID> members = memberIds();
        int maxPage = maxPage(members);
        if (page > maxPage) {
            page = maxPage;
        }
        int start = page * PAGE_SIZE;
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(claimId);
        ClaimOwner owner = claim == null ? null : ClaimOwnerRegistry.INSTANCE.resolve(
            OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()));
        Player player = player().orElse(null);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= members.size()) {
                break;
            }
            UUID memberId = members.get(index);
            pageMembers.add(memberId);
            String role = owner == null ? "?" : owner.roleOf(memberId);
            if (role == null) {
                role = "?";
            }
            List<String> lore = List.of(MenuSupport.text(player, Languages.MENU_MEMBERS_ENTRY_ROLE,
                Map.of("<role>", role)));
            setIcon(slot, MenuSupport.icon(Material.PLAYER_HEAD,
                MenuSupport.displayName(memberId), lore));
        }
        if (members.isEmpty()) {
            setIcon(22, MenuSupport.icon(Material.BARRIER,
                MenuSupport.text(player, Languages.MENU_MEMBERS_EMPTY), null));
        }
    }

    private void changePage(int target) {
        if (target < 0 || target > maxPage(memberIds())) {
            return;
        }
        page = target;
        this.display = buildDisplay();
        updateMenu(true);
    }

    @Override
    public String parsedMenuTitle() {
        return title();
    }

}
