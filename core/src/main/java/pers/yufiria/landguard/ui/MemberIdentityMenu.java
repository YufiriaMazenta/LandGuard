package pers.yufiria.landguard.ui;

import crypticlib.ui.display.Icon;
import crypticlib.ui.display.MenuDisplay;
import crypticlib.ui.display.MenuLayout;
import crypticlib.ui.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.command.GroupCommand;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.identity.Identity;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.IdentityRegistry;
import pers.yufiria.landguard.identity.PermissionPoint;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 成员身份选择菜单：列出 {@code identities.yml} 定义的全部身份，点击把该成员指派为对应身份。
 * <p>
 * 身份来自全服配置（{@link IdentityRegistry#all()}，优先级降序），当前生效身份追加高亮行。
 * GUI 点击不走命令框架的权限节点，因此点击时自行复核
 * {@link PermissionPoint#GROUP_ASSIGN}；服务层 {@link GroupService#assignRole} 还会再做一次
 * 授权与层级校验（同优先级/上级成员不可操作），此处不重复实现层级规则。
 */
public class MemberIdentityMenu extends Menu {

    /** 玻璃底 + 末行返回，身份图标从 0 号槽位依次摆放，40 号返回。 */
    static final List<String> LAYOUT = List.of(
        "ggggggggg",
        "ggggggggg",
        "ggggggggg",
        "ggggggggg",
        "ggggrgggg"
    );

    /** 领袖身份固定金色头盔，其余身份按顺序循环这三种头盔（与 FlagRoleMenu 一致）。 */
    private static final List<Material> OTHER_MATERIALS = List.of(
        Material.IRON_HELMET, Material.CHAINMAIL_HELMET, Material.LEATHER_HELMET);

    private final Player viewer;
    private final String claimId;
    private final UUID memberId;
    private final int listPage;

    public MemberIdentityMenu(@NotNull Player viewer, @NotNull String claimId,
                              @NotNull UUID memberId, int listPage) {
        super(viewer);
        this.viewer = viewer;
        this.claimId = claimId;
        this.memberId = memberId;
        this.listPage = listPage;
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new MembersMenu(player, claimId, listPage).openMenu()));
        return new MenuDisplay(title(), new MenuLayout(LAYOUT, icons));
    }

    private String title() {
        Player player = player().orElse(null);
        return MenuSupport.text(player, Languages.MENU_IDENTITY_TITLE,
            Map.of("<member>", MenuSupport.displayName(memberId)));
    }

    @Override
    public void onLayoutUpdated() {
        Player player = player().orElse(null);
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(claimId);
        String groupId = claim == null ? null : claim.getOwnerId();
        String currentId = groupId == null ? null
            : IdentityPermissions.identityOf(snapshot, groupId, memberId).id();
        int slot = 0;
        int otherIndex = 0;
        for (Identity identity : IdentityRegistry.INSTANCE.all()) {
            Material material = identity.leader()
                ? Material.GOLDEN_HELMET
                : OTHER_MATERIALS.get(otherIndex++ % OTHER_MATERIALS.size());
            setIcon(slot++, identityIcon(player, identity, material, identity.id().equals(currentId)));
        }
    }

    private Icon identityIcon(Player player, Identity identity, Material material, boolean current) {
        List<String> lore = new ArrayList<>();
        lore.add(MenuSupport.text(player, Languages.MENU_IDENTITY_ENTRY_LORE, Map.of(
            "<priority>", String.valueOf(identity.priority()),
            "<permissions>", String.valueOf(identity.permissions().size()))));
        if (current) {
            lore.add(MenuSupport.text(player, Languages.MENU_IDENTITY_CURRENT));
        }
        Icon icon = MenuSupport.icon(material, identity.name(), lore);
        icon.setClickAction(event -> assign(identity));
        return icon;
    }

    /** 点击指派：先自检 {@link PermissionPoint#GROUP_ASSIGN}，再交给服务层做最终授权与层级校验。 */
    private void assign(Identity identity) {
        if (!viewer.isOnline()) {
            return;
        }
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim == null || !BuiltinOwnerTypes.GROUP.equals(claim.getOwnerType())) {
            LangUtils.sendLang(viewer, Languages.MENU_MEMBERS_NO_PERMISSION);
            return;
        }
        if (!IdentityPermissions.canActOnClaim(snapshot, claim, viewer.getUniqueId(),
            PermissionPoint.GROUP_ASSIGN)) {
            LangUtils.sendLang(viewer, Languages.MENU_MEMBERS_NO_PERMISSION);
            return;
        }
        String groupId = claim.getOwnerId();
        AsyncReply.toPlayer(viewer,
            GroupService.INSTANCE.assignRole(viewer.getUniqueId(), groupId, memberId, identity.id()),
            result -> {
                if (result.success()) {
                    LangUtils.sendLang(viewer, Languages.COMMAND_GROUP_ROLE_ASSIGNED, Map.of(
                        "<group>", groupId,
                        "<player>", MenuSupport.displayName(memberId),
                        "<role>", identity.name()));
                    new MembersMenu(viewer, claimId, listPage).openMenu();
                } else {
                    GroupCommand.sendFailure(CommandUtils.commonPlayer(viewer), result);
                }
            });
    }

    @Override
    public String parsedMenuTitle() {
        return title();
    }

}
