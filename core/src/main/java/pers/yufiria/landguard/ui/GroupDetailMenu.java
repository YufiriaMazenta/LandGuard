package pers.yufiria.landguard.ui;

import crypticlib.lang.entry.StringLangEntry;
import crypticlib.ui.display.Icon;
import crypticlib.ui.display.MenuDisplay;
import crypticlib.ui.display.MenuLayout;
import crypticlib.ui.menu.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.claim.ClaimEngine;
import pers.yufiria.landguard.command.GroupCommand;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.group.GroupOpResult;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.PermissionPoint;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 组织管理页：展示组信息，并按查看者在组内的身份权限提供管理入口
 * （成员管理、邀请、改展示名、解散、退出，以及接受/拒绝待处理邀请）。
 * <p>
 * 按钮可见性按 {@link PermissionPoint} 判定（GUI 点击不走命令框架的权限节点，与
 * {@link MemberIdentityMenu} 一致），最终授权与层级规则仍由 {@link GroupService} 复核；
 * 解散为破坏性操作，需 Shift+左键确认。
 */
public class GroupDetailMenu extends Menu {

    static final List<String> LAYOUT = List.of(
        "ggggggggg",
        "ggggxgggg",
        "ggggggggg",
        "gmvndlarg",
        "ggggggggg",
        "ggggbgggg"
    );

    private final String groupId;
    private final int listPage;

    public GroupDetailMenu(@NotNull Player player, @NotNull String groupId, int listPage) {
        super(player);
        this.groupId = groupId;
        this.listPage = listPage;
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        GroupData group = snapshot.groups().get(groupId);
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('x', this::infoIcon);
        icons.put('m', () -> actionIcon(Material.PLAYER_HEAD,
            Languages.MENU_GROUP_DETAIL_MEMBERS_NAME, Languages.MENU_GROUP_DETAIL_MEMBERS_LORE,
            this::openMembers));
        if (group != null && player != null) {
            UUID viewer = player.getUniqueId();
            if (IdentityPermissions.has(snapshot, groupId, viewer, PermissionPoint.GROUP_INVITE)) {
                icons.put('v', () -> actionIcon(Material.WRITABLE_BOOK,
                    Languages.MENU_GROUP_DETAIL_INVITE_NAME, Languages.MENU_GROUP_DETAIL_INVITE_LORE,
                    this::startInvite));
            }
            if (IdentityPermissions.has(snapshot, groupId, viewer, PermissionPoint.GROUP_RENAME)) {
                icons.put('n', () -> actionIcon(Material.NAME_TAG,
                    Languages.MENU_GROUP_DETAIL_RENAME_NAME, Languages.MENU_GROUP_DETAIL_RENAME_LORE,
                    this::startRename));
            }
            if (IdentityPermissions.has(snapshot, groupId, viewer, PermissionPoint.GROUP_DISBAND)) {
                icons.put('d', this::disbandIcon);
            }
            // 领袖不能直接退出（须先转让领袖或解散），故只对非领袖成员显示
            if (IdentityPermissions.isMember(snapshot, groupId, viewer)
                && !IdentityPermissions.isLeader(snapshot, groupId, viewer)) {
                icons.put('l', () -> actionIcon(Material.OAK_DOOR,
                    Languages.MENU_GROUP_DETAIL_LEAVE_NAME, Languages.MENU_GROUP_DETAIL_LEAVE_LORE,
                    this::leave));
            }
            if (GroupService.INSTANCE.pendingInviteGroupIds(viewer).contains(groupId)) {
                icons.put('a', () -> actionIcon(Material.LIME_DYE,
                    Languages.MENU_GROUP_DETAIL_ACCEPT_NAME, Languages.MENU_GROUP_DETAIL_ACCEPT_LORE,
                    this::accept));
                icons.put('r', () -> actionIcon(Material.RED_DYE,
                    Languages.MENU_GROUP_DETAIL_DENY_NAME, Languages.MENU_GROUP_DETAIL_DENY_LORE,
                    this::deny));
            }
        }
        icons.put('b', () -> MenuSupport.backIcon(player, () -> {
            Player viewer = player().orElse(null);
            if (viewer != null) {
                new GroupListMenu(viewer, listPage).openMenu();
            }
        }));
        return new MenuDisplay(title(player), new MenuLayout(LAYOUT, icons));
    }

    private String title(Player player) {
        return MenuSupport.text(player, Languages.MENU_GROUP_DETAIL_TITLE,
            Map.of("<name>", groupName(player)));
    }

    private String groupName(@Nullable Player player) {
        GroupData group = DataStore.INSTANCE.snapshot().groups().get(groupId);
        return group == null ? groupId : group.getName();
    }

    private Icon infoIcon() {
        Player player = player().orElse(null);
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        GroupData group = snapshot.groups().get(groupId);
        if (group == null) {
            return MenuSupport.icon(Material.BARRIER, groupName(player), null);
        }
        Map<UUID, String> memberRoles = snapshot.groupMembers().getOrDefault(groupId, Map.of());
        int members = memberRoles.size() + (memberRoles.containsKey(group.getLeaderUuid()) ? 0 : 1);
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId);
        List<String> lore = List.of(
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_ID,
                Map.of("<group_id>", group.getGroupId())),
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_LEADER,
                Map.of("<leader>", MenuSupport.displayName(group.getLeaderUuid()))),
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_MEMBERS,
                Map.of("<members>", String.valueOf(members))),
            MenuSupport.text(player, Languages.MENU_GROUP_DETAIL_INFO_CHUNKS, Map.of(
                "<chunks>", String.valueOf(ClaimEngine.currentClaimedChunks(snapshot, owner)),
                "<capacity>", String.valueOf(GroupService.INSTANCE.groupCapacity(snapshot, groupId)))),
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_ROLE,
                Map.of("<role>", IdentityPermissions.identityOf(snapshot, groupId, playerId).name()))
        );
        return MenuSupport.icon(Material.WRITABLE_BOOK,
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_NAME,
                Map.of("<name>", group.getName())), lore);
    }

    private Icon actionIcon(Material material, StringLangEntry nameEntry,
                            StringLangEntry loreEntry, Runnable action) {
        Player player = player().orElse(null);
        Icon icon = MenuSupport.icon(material,
            MenuSupport.text(player, nameEntry),
            List.of(MenuSupport.text(player, loreEntry)));
        icon.setClickAction(event -> action.run());
        return icon;
    }

    /** 解散不可撤销：仅 Shift+左键真正执行，普通点击只提示确认方式。 */
    private Icon disbandIcon() {
        Player player = player().orElse(null);
        Icon icon = MenuSupport.icon(Material.BARRIER,
            MenuSupport.text(player, Languages.MENU_GROUP_DETAIL_DISBAND_NAME),
            List.of(MenuSupport.text(player, Languages.MENU_GROUP_DETAIL_DISBAND_LORE)));
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker == null) {
                return;
            }
            if (event == null || !event.isShiftClick()) {
                LangUtils.sendLang(clicker, Languages.MENU_GROUP_DETAIL_DISBAND_CONFIRM);
                return;
            }
            disband(clicker);
        });
        return icon;
    }

    private void openMembers() {
        Player player = player().orElse(null);
        if (player != null) {
            new GroupMembersMenu(player, groupId, listPage).openMenu();
        }
    }

    /** 邀请：聊天输入玩家名（仅在线或本地缓存，绝不触发阻塞式查询），等价于 {@code /land group invite}。 */
    private void startInvite() {
        Player player = player().orElse(null);
        if (player == null) {
            return;
        }
        ChatPrompt.ask(player, Languages.MENU_GROUP_DETAIL_INVITE_PROMPT, input -> {
            UUID target = resolveTarget(input);
            if (target == null) {
                LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
                new GroupDetailMenu(player, groupId, listPage).openMenu();
                return;
            }
            AsyncReply.toPlayer(player, GroupService.INSTANCE.invite(player.getUniqueId(), groupId, target),
                result -> {
                    if (result.success()) {
                        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INVITE_SENT,
                            Map.of("<group>", groupName(player), "<player>", input));
                        notifyInvited(player, target);
                    } else {
                        GroupCommand.sendFailure(CommandUtils.commonPlayer(player), result);
                    }
                    new GroupDetailMenu(player, groupId, listPage).openMenu();
                });
        });
    }

    /** 被邀请者在线时立即告知（与命令层同一文案），离线玩家则在其组织菜单里看到待处理邀请。 */
    private void notifyInvited(Player actor, UUID target) {
        Player online = Bukkit.getPlayer(target);
        if (online == null) {
            return;
        }
        GroupData group = DataStore.INSTANCE.snapshot().groups().get(groupId);
        LangUtils.sendLang(online, Languages.COMMAND_GROUP_INVITE_RECEIVED, Map.of(
            "<group>", group == null ? groupId : group.getName(),
            "<group_id>", groupId,
            "<leader>", actor.getName()));
    }

    private void startRename() {
        Player player = player().orElse(null);
        if (player == null) {
            return;
        }
        ChatPrompt.ask(player, Languages.MENU_GROUP_DETAIL_RENAME_PROMPT, input ->
            AsyncReply.toPlayer(player, GroupService.INSTANCE.renameGroup(player.getUniqueId(), groupId, input),
                result -> {
                    if (result.success()) {
                        LangUtils.sendLang(player, Languages.COMMAND_GROUP_RENAME_SUCCESS,
                            Map.of("<group>", input));
                    } else {
                        GroupCommand.sendFailure(CommandUtils.commonPlayer(player), result);
                    }
                    new GroupDetailMenu(player, groupId, listPage).openMenu();
                }));
    }

    private void leave() {
        Player player = player().orElse(null);
        if (player != null) {
            run(player, GroupService.INSTANCE.leave(player.getUniqueId(), groupId),
                Languages.COMMAND_GROUP_LEAVE_SUCCESS, true);
        }
    }

    private void accept() {
        Player player = player().orElse(null);
        if (player != null) {
            run(player, GroupService.INSTANCE.acceptInvite(player.getUniqueId(), groupId),
                Languages.COMMAND_GROUP_ACCEPT_SUCCESS, false);
        }
    }

    private void deny() {
        Player player = player().orElse(null);
        if (player != null) {
            run(player, GroupService.INSTANCE.denyInvite(player.getUniqueId(), groupId),
                Languages.COMMAND_GROUP_DENY_SUCCESS, true);
        }
    }

    private void disband(Player player) {
        run(player, GroupService.INSTANCE.disband(player.getUniqueId(), groupId),
            Languages.COMMAND_GROUP_DISBAND_SUCCESS, true);
    }

    /**
     * 退出 / 解散 / 拒绝邀请会使该组从本人的可见列表消失，成功后回组织列表；
     * 接受邀请则留在本页（按钮随状态刷新为成员视图）。
     * 成功文案与命令层复用同一批语言条目。
     */
    private void run(Player player, CompletableFuture<GroupOpResult> future,
                     StringLangEntry successEntry, boolean backToList) {
        AsyncReply.toPlayer(player, future, result -> {
            if (result.success()) {
                LangUtils.sendLang(player, successEntry, Map.of("<group>", groupName(player)));
            } else {
                GroupCommand.sendFailure(CommandUtils.commonPlayer(player), result);
            }
            if (backToList && result.success()) {
                new GroupListMenu(player, listPage).openMenu();
            } else {
                new GroupDetailMenu(player, groupId, listPage).openMenu();
            }
        });
    }

    private static @Nullable UUID resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    @Override
    public String parsedMenuTitle() {
        return title(player().orElse(null));
    }

}