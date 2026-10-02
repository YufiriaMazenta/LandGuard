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
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.PermissionPoint;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.*;
import java.util.function.Supplier;

/**
 * 组织成员列表：按身份优先级降序列出成员，点击按查看者权限执行操作 ——
 * 左键指派身份（{@link MemberIdentityMenu}）、右键踢出、Shift+左键转让领袖。
 * <p>
 * 可执行哪些操作按 {@link PermissionPoint} 判定并写在 lore 里；层级与自我保护规则
 * （不可踢领袖 / 不可踢自己 / 不能指派同级或更高）由 {@link GroupService} 复核。
 */
public class GroupMembersMenu extends Menu {

    static final int PAGE_SIZE = 45;
    static final List<String> LAYOUT = List.of(
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "pghgrgggn"
    );

    private final String groupId;
    private final int listPage;
    private int page;
    private final List<UUID> pageMembers = new ArrayList<>();

    public GroupMembersMenu(@NotNull Player player, @NotNull String groupId, int listPage) {
        super(player);
        this.groupId = groupId;
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
            MenuSupport.text(player, Languages.MENU_GROUP_MEMBERS_HINT), null));
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new GroupDetailMenu(player, groupId, listPage).openMenu()));
        return new MenuDisplay(title(player), new MenuLayout(LAYOUT, icons));
    }

    private String title(Player player) {
        GroupData group = DataStore.INSTANCE.snapshot().groups().get(groupId);
        String name = group == null ? groupId : group.getName();
        return MenuSupport.text(player, Languages.MENU_GROUP_MEMBERS_TITLE, Map.of("<name>", name));
    }

    /** 成员 = 成员表中的玩家 + 领袖（历史数据可能没有领袖的成员行），按身份优先级降序。 */
    private List<UUID> memberIds() {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        GroupData group = snapshot.groups().get(groupId);
        if (group == null) {
            return List.of();
        }
        Set<UUID> members = new LinkedHashSet<>(snapshot.groupMembers().getOrDefault(groupId, Map.of()).keySet());
        members.add(group.getLeaderUuid());
        List<UUID> sorted = new ArrayList<>(members);
        sorted.sort(Comparator
            .comparingInt((UUID member) -> -IdentityPermissions.identityOf(snapshot, groupId, member).priority())
            .thenComparing(UUID::compareTo));
        return sorted;
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
        Player player = player().orElse(null);
        UUID viewer = player == null ? null : player.getUniqueId();
        boolean canAssign = viewer != null
            && IdentityPermissions.has(snapshot, groupId, viewer, PermissionPoint.GROUP_ASSIGN);
        boolean canKick = viewer != null
            && IdentityPermissions.has(snapshot, groupId, viewer, PermissionPoint.GROUP_KICK);
        boolean canTransfer = viewer != null
            && IdentityPermissions.has(snapshot, groupId, viewer, PermissionPoint.GROUP_TRANSFER);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= members.size()) {
                break;
            }
            UUID memberId = members.get(index);
            pageMembers.add(memberId);
            setIcon(slot, memberIcon(player, snapshot, memberId, slot, canAssign, canKick, canTransfer));
        }
        if (members.isEmpty()) {
            setIcon(22, MenuSupport.icon(Material.BARRIER,
                MenuSupport.text(player, Languages.MENU_GROUP_MEMBERS_EMPTY), null));
        }
    }

    private Icon memberIcon(Player player, DataSnapshot snapshot, UUID memberId, int slot,
                            boolean canAssign, boolean canKick, boolean canTransfer) {
        List<String> lore = new ArrayList<>();
        lore.add(MenuSupport.text(player, Languages.MENU_GROUP_MEMBERS_ENTRY_ROLE,
            Map.of("<role>", IdentityPermissions.identityOf(snapshot, groupId, memberId).name())));
        if (canAssign) {
            lore.add(MenuSupport.text(player, Languages.MENU_GROUP_MEMBERS_ENTRY_ASSIGN));
        }
        if (canKick) {
            lore.add(MenuSupport.text(player, Languages.MENU_GROUP_MEMBERS_ENTRY_KICK));
        }
        if (canTransfer) {
            lore.add(MenuSupport.text(player, Languages.MENU_GROUP_MEMBERS_ENTRY_TRANSFER));
        }
        if (!canAssign && !canKick && !canTransfer) {
            lore.add(MenuSupport.text(player, Languages.MENU_GROUP_MEMBERS_ENTRY_READONLY));
        }
        Icon icon = MenuSupport.icon(Material.PLAYER_HEAD, MenuSupport.displayName(memberId), lore);
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker == null || slot >= pageMembers.size()) {
                return;
            }
            UUID target = pageMembers.get(slot);
            boolean shift = event != null && event.isShiftClick();
            boolean right = event != null && event.isRightClick();
            if (shift && canTransfer) {
                transfer(clicker, target);
            } else if (right && canKick) {
                kick(clicker, target);
            } else if (canAssign) {
                new MemberIdentityMenu(clicker, groupId, target, () ->
                    new GroupMembersMenu(clicker, groupId, listPage).openMenu()).openMenu();
            } else {
                LangUtils.sendLang(clicker, Languages.MENU_GROUP_NO_PERMISSION);
            }
        });
        return icon;
    }

    private void kick(Player player, UUID target) {
        String targetName = MenuSupport.displayName(target);
        AsyncReply.toPlayer(player,
            GroupService.INSTANCE.kick(player.getUniqueId(), groupId, target), result -> {
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_GROUP_KICK_SUCCESS,
                        Map.of("<group>", groupName(), "<player>", targetName));
                } else {
                    GroupCommand.sendFailure(CommandUtils.commonPlayer(player), result);
                }
                new GroupMembersMenu(player, groupId, listPage).openMenu();
            });
    }

    private void transfer(Player player, UUID target) {
        String targetName = MenuSupport.displayName(target);
        AsyncReply.toPlayer(player,
            GroupService.INSTANCE.transferLeadership(player.getUniqueId(), groupId, target), result -> {
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_GROUP_TRANSFER_SUCCESS,
                        Map.of("<group>", groupName(), "<player>", targetName));
                } else {
                    GroupCommand.sendFailure(CommandUtils.commonPlayer(player), result);
                }
                new GroupMembersMenu(player, groupId, listPage).openMenu();
            });
    }

    private String groupName() {
        GroupData group = DataStore.INSTANCE.snapshot().groups().get(groupId);
        return group == null ? groupId : group.getName();
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
        return title(player().orElse(null));
    }

}