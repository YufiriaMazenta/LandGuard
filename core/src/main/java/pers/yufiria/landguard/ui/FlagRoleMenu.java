package pers.yufiria.landguard.ui;

import crypticlib.lang.entry.StringLangEntry;
import crypticlib.ui.display.Icon;
import crypticlib.ui.display.MenuDisplay;
import crypticlib.ui.display.MenuLayout;
import crypticlib.ui.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.Roles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * flag 设置入口：先选择身份维度（owner/manager/member/visitor 行为矩阵，或自然环境开关）。
 */
public class FlagRoleMenu extends Menu {

    // o/m/e 位于 19-21，v/n 位于 23-24，返回 40
    static final List<String> LAYOUT = List.of(
        "ggggggggg",
        "ggggggggg",
        "gome.vngg",
        "ggggggggg",
        "ggggrgggg"
    );

    private final String claimId;
    private final int listPage;

    public FlagRoleMenu(@NotNull Player player, @NotNull String claimId, int listPage) {
        super(player);
        this.claimId = claimId;
        this.listPage = listPage;
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('o', () -> roleIcon(Material.GOLDEN_HELMET,
            Languages.MENU_ROLE_OWNER_NAME, Languages.MENU_ROLE_OWNER_LORE, Roles.OWNER, false));
        icons.put('m', () -> roleIcon(Material.IRON_HELMET,
            Languages.MENU_ROLE_MANAGER_NAME, Languages.MENU_ROLE_MANAGER_LORE, Roles.MANAGER, false));
        icons.put('e', () -> roleIcon(Material.CHAINMAIL_HELMET,
            Languages.MENU_ROLE_MEMBER_NAME, Languages.MENU_ROLE_MEMBER_LORE, Roles.MEMBER, false));
        icons.put('v', () -> roleIcon(Material.LEATHER_HELMET,
            Languages.MENU_ROLE_VISITOR_NAME, Languages.MENU_ROLE_VISITOR_LORE, Roles.VISITOR, false));
        icons.put('n', () -> roleIcon(Material.CLOCK,
            Languages.MENU_ROLE_NATURAL_NAME, Languages.MENU_ROLE_NATURAL_LORE, null, true));
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new ClaimDetailMenu(player, claimId, listPage).openMenu()));
        return new MenuDisplay(title(), new MenuLayout(LAYOUT, icons));
    }

    private Icon roleIcon(Material material, StringLangEntry nameEntry, StringLangEntry loreEntry,
                          String roleId, boolean natural) {
        Player player = player().orElse(null);
        Icon icon = MenuSupport.icon(material,
            MenuSupport.text(player, nameEntry),
            List.of(MenuSupport.text(player, loreEntry)));
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker != null) {
                new FlagListMenu(clicker, claimId, roleId, natural, listPage).openMenu();
            }
        });
        return icon;
    }

    private String title() {
        Player player = player().orElse(null);
        ClaimData claim = DataStore.INSTANCE.snapshot().claimsById().get(claimId);
        String name = claim == null || claim.getName() == null ? claimId : claim.getName();
        return MenuSupport.text(player, Languages.MENU_ROLE_TITLE, Map.of("<name>", name));
    }

    @Override
    public String parsedMenuTitle() {
        return title();
    }

}
