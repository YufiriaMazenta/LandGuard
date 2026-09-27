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
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.PermissionPoint;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.LangUtils;

import java.util.*;
import java.util.function.Supplier;

/**
 * 成员列表：展示当前所有者实体的全部成员及其身份标识。
 * 个人领地只读；用户组领地可由拥有 {@link PermissionPoint#GROUP_ASSIGN} 的查看者左键点击成员，
 * 打开 {@link MemberIdentityMenu} 指派身份（身份来自 identities.yml，无权限则提示）。
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
        boolean groupClaim = claim != null && BuiltinOwnerTypes.GROUP.equals(claim.getOwnerType());
        UUID viewerUuid = player == null ? null : player.getUniqueId();
        boolean canAssign = groupClaim && viewerUuid != null
            && IdentityPermissions.canActOnClaim(snapshot, claim, viewerUuid, PermissionPoint.GROUP_ASSIGN);
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
            List<String> lore = new ArrayList<>();
            lore.add(MenuSupport.text(player, Languages.MENU_MEMBERS_ENTRY_ROLE, Map.of("<role>", role)));
            if (groupClaim) {
                lore.add(MenuSupport.text(player, Languages.MENU_MEMBERS_ENTRY_ACTION));
            }
            Icon icon = MenuSupport.icon(Material.PLAYER_HEAD, MenuSupport.displayName(memberId), lore);
            if (groupClaim) {
                icon.setClickAction(event -> {
                    if (canAssign) {
                        new MemberIdentityMenu(player, claimId, memberId, page).openMenu();
                    } else {
                        LangUtils.sendLang(player, Languages.MENU_MEMBERS_NO_PERMISSION);
                    }
                });
            }
            setIcon(slot, icon);
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
