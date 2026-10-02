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
 * 两个入口共用同一实现：组领地的成员页按 {@code claimId} 反查所属用户组，
 * 组织 GUI（{@link GroupMembersMenu}）直接传入用户组标识符。
 * <p>
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
    private final String groupId;
    private final UUID memberId;
    private final Runnable onBack;

    /** 组领地成员页入口：由 {@code claimId} 反查组标识符，返回时重开该领地的成员页。 */
    public MemberIdentityMenu(@NotNull Player viewer, @NotNull String claimId,
                              @NotNull UUID memberId, int listPage) {
        this(viewer, groupIdOf(claimId), memberId,
            () -> new MembersMenu(viewer, claimId, listPage).openMenu());
    }

    /** 组织 GUI 入口：直接给出用户组标识符与返回动作。 */
    public MemberIdentityMenu(@NotNull Player viewer, @NotNull String groupId,
                              @NotNull UUID memberId, @NotNull Runnable onBack) {
        super(viewer);
        this.viewer = viewer;
        this.groupId = groupId;
        this.memberId = memberId;
        this.onBack = onBack;
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('r', () -> MenuSupport.backIcon(player, onBack));
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
        String currentId = IdentityPermissions.memberIdentityIdOf(snapshot, groupId, memberId);
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
        if (!IdentityPermissions.has(snapshot, groupId, viewer.getUniqueId(), PermissionPoint.GROUP_ASSIGN)) {
            LangUtils.sendLang(viewer, Languages.MENU_GROUP_NO_PERMISSION);
            return;
        }
        AsyncReply.toPlayer(viewer,
            GroupService.INSTANCE.assignRole(viewer.getUniqueId(), groupId, memberId, identity.id()),
            result -> {
                if (result.success()) {
                    LangUtils.sendLang(viewer, Languages.COMMAND_GROUP_ROLE_ASSIGNED, Map.of(
                        "<group>", groupId,
                        "<player>", MenuSupport.displayName(memberId),
                        "<role>", identity.name()));
                    // 指派成功：回到成员页看到新身份；失败则留在本页便于改选其他身份
                    onBack.run();
                } else {
                    GroupCommand.sendFailure(CommandUtils.commonPlayer(viewer), result);
                }
            });
    }

    /** 组领地成员页只对用户组领地开放该菜单，故按 claimId 反查组标识符即可；查不到返回空串（必然无权）。 */
    private static String groupIdOf(String claimId) {
        ClaimData claim = DataStore.INSTANCE.snapshot().claimsById().get(claimId);
        return claim == null ? "" : claim.getOwnerId();
    }

    @Override
    public String parsedMenuTitle() {
        return title();
    }

}