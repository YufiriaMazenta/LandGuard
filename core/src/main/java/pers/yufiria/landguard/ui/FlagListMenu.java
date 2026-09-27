package pers.yufiria.landguard.ui;

import crypticlib.lang.entry.StringLangEntry;
import crypticlib.ui.display.Icon;
import crypticlib.ui.display.MenuDisplay;
import crypticlib.ui.display.MenuLayout;
import crypticlib.ui.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.identity.Identity;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.IdentityRegistry;
import pers.yufiria.landguard.identity.PermissionPoint;
import pers.yufiria.landguard.protection.BuiltinFlagDefaults;
import pers.yufiria.landguard.protection.FlagService;
import pers.yufiria.landguard.protection.ProtectionChecker;
import pers.yufiria.landguard.protection.ProtectionFlag;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.LangUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 单个身份维度的 flag 列表：左键在 允许 → 拒绝 → 默认（清除覆盖，回退全局矩阵）三态间循环。
 * 只有所有者/管理者可写；其余成员可查看，点击得到拒绝提示。全部写入走 {@link FlagService}。
 */
public class FlagListMenu extends Menu {

    static final List<String> LAYOUT = List.of(
        ".........",
        ".........",
        ".........",
        "ggggrgggg"
    );

    private static final Map<String, StringLangEntry> FLAG_NAMES = new LinkedHashMap<>();

    static {
        FLAG_NAMES.put("place", Languages.MENU_FLAG_ID_PLACE);
        FLAG_NAMES.put("break", Languages.MENU_FLAG_ID_BREAK);
        FLAG_NAMES.put("container", Languages.MENU_FLAG_ID_CONTAINER);
        FLAG_NAMES.put("door", Languages.MENU_FLAG_ID_DOOR);
        FLAG_NAMES.put("redstone", Languages.MENU_FLAG_ID_REDSTONE);
        FLAG_NAMES.put("crafting", Languages.MENU_FLAG_ID_CRAFTING);
        FLAG_NAMES.put("vehicle", Languages.MENU_FLAG_ID_VEHICLE);
        FLAG_NAMES.put("animal", Languages.MENU_FLAG_ID_ANIMAL);
        FLAG_NAMES.put("display", Languages.MENU_FLAG_ID_DISPLAY);
        FLAG_NAMES.put("planting", Languages.MENU_FLAG_ID_PLANTING);
        FLAG_NAMES.put("harvest", Languages.MENU_FLAG_ID_HARVEST);
        FLAG_NAMES.put("item", Languages.MENU_FLAG_ID_ITEM);
        FLAG_NAMES.put("bank", Languages.MENU_FLAG_ID_BANK);
        FLAG_NAMES.put("pvp", Languages.MENU_FLAG_ID_PVP);
        FLAG_NAMES.put("explosion", Languages.MENU_FLAG_ID_EXPLOSION);
        FLAG_NAMES.put("fire_spread", Languages.MENU_FLAG_ID_FIRE_SPREAD);
        FLAG_NAMES.put("fluid_flow", Languages.MENU_FLAG_ID_FLUID_FLOW);
        FLAG_NAMES.put("piston", Languages.MENU_FLAG_ID_PISTON);
        FLAG_NAMES.put("mob_spawn", Languages.MENU_FLAG_ID_MOB_SPAWN);
        FLAG_NAMES.put("mob_grief", Languages.MENU_FLAG_ID_MOB_GRIEF);
        FLAG_NAMES.put("trample", Languages.MENU_FLAG_ID_TRAMPLE);
    }

    private final String claimId;
    private final String roleId;
    private final boolean natural;
    private final int listPage;

    public FlagListMenu(@NotNull Player player, @NotNull String claimId, @Nullable String roleId,
                        boolean natural, int listPage) {
        super(player);
        this.claimId = claimId;
        this.roleId = roleId;
        this.natural = natural;
        this.listPage = listPage;
        this.display = buildDisplay();
    }

    private String storageRole() {
        return natural ? ProtectionChecker.ENVIRONMENT_ROLE : roleId;
    }

    private List<ProtectionFlag> flags() {
        return new ArrayList<>(natural ? MenuSupport.NATURAL_MATERIALS.keySet()
            : MenuSupport.BEHAVIOR_MATERIALS.keySet());
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new FlagRoleMenu(player, claimId, listPage).openMenu()));
        return new MenuDisplay(title(), new MenuLayout(LAYOUT, icons));
    }

    private String title() {
        Player player = player().orElse(null);
        ClaimData claim = DataStore.INSTANCE.snapshot().claimsById().get(claimId);
        String claimName = claim == null || claim.getName() == null ? claimId : claim.getName();
        return MenuSupport.text(player, Languages.MENU_FLAG_TITLE, Map.of(
            "<name>", claimName,
            "<role>", roleDisplayName(player)
        ));
    }

    private String roleDisplayName(Player player) {
        if (natural) {
            return MenuSupport.text(player, Languages.MENU_ROLE_NATURAL_NAME);
        }
        Identity identity = IdentityRegistry.INSTANCE.get(roleId);
        return identity == null ? roleId : identity.name();
    }

    @Override
    public void onLayoutUpdated() {
        Player player = player().orElse(null);
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<ProtectionFlag> flags = flags();
        for (int i = 0; i < flags.size() && i < 27; i++) {
            setIcon(i, flagIcon(player, snapshot, flags.get(i)));
        }
    }

    private Icon flagIcon(Player player, DataSnapshot snapshot, ProtectionFlag flag) {
        Material material = (natural ? MenuSupport.NATURAL_MATERIALS : MenuSupport.BEHAVIOR_MATERIALS)
            .getOrDefault(flag, Material.PAPER);
        Boolean state = override(snapshot, flag);
        boolean defaultValue = natural
            ? BuiltinFlagDefaults.naturalDefault(flag)
            : BuiltinFlagDefaults.behaviorDefault(roleId, flag);
        boolean effective = state != null ? state : defaultValue;

        String stateText = state == null
            ? MenuSupport.text(player, Languages.MENU_FLAG_STATE_DEFAULT, Map.of(
                "<value>", onOff(player, effective)))
            : MenuSupport.text(player, state ? Languages.MENU_FLAG_STATE_ALLOW
                : Languages.MENU_FLAG_STATE_DENY);
        String prefix = state == null ? "&7" : (state ? "&a" : "&c");
        String flagName = MenuSupport.text(player, FLAG_NAMES.get(flag.id()));
        List<String> lore = List.of(
            MenuSupport.text(player, Languages.MENU_FLAG_CURRENT, Map.of("<state>", stateText)),
            MenuSupport.text(player, Languages.MENU_FLAG_DEFAULT, Map.of(
                "<value>", onOff(player, defaultValue))),
            MenuSupport.text(player, Languages.MENU_FLAG_HINT)
        );
        Icon icon = MenuSupport.icon(material, prefix + flagName, lore);
        icon.setClickAction(event -> cycleFlag(flag, state));
        return icon;
    }

    private String onOff(Player player, boolean value) {
        return value
            ? MenuSupport.text(player, Languages.MENU_FLAG_STATE_ALLOW)
            : MenuSupport.text(player, Languages.MENU_FLAG_STATE_DENY);
    }

    private @Nullable Boolean override(DataSnapshot snapshot, ProtectionFlag flag) {
        Map<String, Boolean> overrides = snapshot.roleFlagsByClaim()
            .getOrDefault(claimId, Map.of())
            .get(storageRole());
        return overrides == null ? null : overrides.get(flag.id());
    }

    private void cycleFlag(ProtectionFlag flag, @Nullable Boolean current) {
        Player player = player().orElse(null);
        if (player == null) {
            return;
        }
        if (!canManage(player)) {
            LangUtils.sendLang(player, Languages.MENU_FAIL_NOT_MANAGER);
            return;
        }
        // null（默认）→ true → false → null（清除覆盖）
        Boolean next = current == null ? Boolean.TRUE : current ? Boolean.FALSE : null;
        CompletableFuture<Boolean> future;
        if (natural) {
            future = next == null
                ? FlagService.INSTANCE.resetNaturalOverride(claimId, flag)
                : FlagService.INSTANCE.setNaturalOverride(claimId, flag, next);
        } else {
            future = next == null
                ? FlagService.INSTANCE.resetBehaviorOverride(claimId, roleId, flag)
                : FlagService.INSTANCE.setBehaviorOverride(claimId, roleId, flag, next);
        }
        AsyncReply.toPlayer(player, future, result -> {
            this.display = buildDisplay();
            updateMenu(true);
        });
    }

    private boolean canManage(Player player) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(claimId);
        return IdentityPermissions.canActOnClaim(snapshot, claim, player.getUniqueId(), PermissionPoint.CLAIM_FLAGS);
    }

    @Override
    public String parsedMenuTitle() {
        return title();
    }

}
