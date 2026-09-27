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
import pers.yufiria.landguard.identity.Identity;
import pers.yufiria.landguard.identity.IdentityRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * flag 设置入口：先选择身份维度（配置里定义的全部身份，本服统一），或自然环境开关。
 * 身份数量由 identities.yml 决定，因此图标在布局更新时按注册表顺序逐个摆放。
 */
public class FlagRoleMenu extends Menu {

    /** 身份与自然环境入口从 0 号槽位依次摆放，40 号槽位返回。 */
    static final List<String> LAYOUT = List.of(
        "ggggggggg",
        "ggggggggg",
        "ggggggggg",
        "ggggggggg",
        "ggggrgggg"
    );

    /** 领袖身份固定金色头盔，其余身份按顺序循环这三种头盔。 */
    private static final List<Material> OTHER_MATERIALS = List.of(
        Material.IRON_HELMET, Material.CHAINMAIL_HELMET, Material.LEATHER_HELMET);

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
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new ClaimDetailMenu(player, claimId, listPage).openMenu()));
        return new MenuDisplay(title(), new MenuLayout(LAYOUT, icons));
    }

    @Override
    public void onLayoutUpdated() {
        Player player = player().orElse(null);
        int slot = 0;
        int otherIndex = 0;
        for (Identity identity : IdentityRegistry.INSTANCE.all()) {
            Material material = identity.leader()
                ? Material.GOLDEN_HELMET
                : OTHER_MATERIALS.get(otherIndex++ % OTHER_MATERIALS.size());
            setIcon(slot++, identityIcon(player, identity, material));
        }
        setIcon(slot, roleIcon(Material.CLOCK,
            Languages.MENU_ROLE_NATURAL_NAME, Languages.MENU_ROLE_NATURAL_LORE, null, true));
    }

    private Icon identityIcon(Player player, Identity identity, Material material) {
        Icon icon = MenuSupport.icon(material, identity.name(),
            List.of(MenuSupport.text(player, Languages.MENU_ROLE_IDENTITY_LORE, Map.of(
                "<priority>", String.valueOf(identity.priority()),
                "<permissions>", String.valueOf(identity.permissions().size())))));
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker != null) {
                new FlagListMenu(clicker, claimId, identity.id(), false, listPage).openMenu();
            }
        });
        return icon;
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
