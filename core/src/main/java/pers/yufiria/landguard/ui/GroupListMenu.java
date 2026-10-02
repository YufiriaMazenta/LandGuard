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
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.*;
import java.util.function.Supplier;

/**
 * 组织 GUI 入口：列出玩家已加入（含自己拥有）的用户组，点击进入 {@link GroupDetailMenu}。
 * 底部分页、待处理邀请（{@link GroupInvitesMenu}）、创建组织（聊天输入）与返回领地列表。
 * 所有写操作走 {@link GroupService}，不绕过服务层校验。
 */
public class GroupListMenu extends Menu {

    static final int PAGE_SIZE = 45;
    static final List<String> LAYOUT = List.of(
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "pggicggon"
    );

    private int page;
    private final List<String> pageGroupIds = new ArrayList<>();

    public GroupListMenu(@NotNull Player player) {
        this(player, 0);
    }

    public GroupListMenu(@NotNull Player player, int page) {
        super(player);
        this.page = Math.max(0, page);
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('p', () -> MenuSupport.arrow(player, false, page > 0, () -> changePage(page - 1)));
        icons.put('n', () -> MenuSupport.arrow(player, true, page < maxPage(), () -> changePage(page + 1)));
        icons.put('i', this::invitesIcon);
        icons.put('c', this::createIcon);
        icons.put('o', () -> MenuSupport.backIcon(player, () -> {
            Player viewer = player().orElse(null);
            if (viewer != null) {
                new ClaimListMenu(viewer).openMenu();
            }
        }));
        return new MenuDisplay(title(player), new MenuLayout(LAYOUT, icons));
    }

    private String title(Player player) {
        return MenuSupport.text(player, Languages.MENU_GROUP_LIST_TITLE, Map.of(
            "<page>", String.valueOf(page + 1),
            "<max_page>", String.valueOf(maxPage() + 1)
        ));
    }

    /** 玩家已加入的用户组（拥有视为已加入），按展示名排序保证翻页稳定。 */
    private List<String> visibleGroupIds() {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        UUID viewer = playerId;
        List<GroupData> groups = new ArrayList<>();
        for (GroupData group : snapshot.groups().values()) {
            if (IdentityPermissions.isMember(snapshot, group.getGroupId(), viewer)) {
                groups.add(group);
            }
        }
        groups.sort(Comparator.comparing(GroupData::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(GroupData::getGroupId));
        List<String> ids = new ArrayList<>(groups.size());
        for (GroupData group : groups) {
            ids.add(group.getGroupId());
        }
        return ids;
    }

    private int maxPage() {
        return Math.max(0, (visibleGroupIds().size() - 1) / PAGE_SIZE);
    }

    @Override
    public void onLayoutUpdated() {
        pageGroupIds.clear();
        List<String> all = visibleGroupIds();
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
                String groupId = all.get(index);
                pageGroupIds.add(groupId);
                setIcon(slot, groupEntryIcon(player, snapshot, groupId, index));
            }
        }
        if (all.isEmpty()) {
            setIcon(22, MenuSupport.icon(Material.BARRIER,
                MenuSupport.text(player, Languages.MENU_GROUP_LIST_EMPTY), null));
        }
    }

    private Icon groupEntryIcon(Player player, DataSnapshot snapshot, String groupId, int index) {
        GroupData group = snapshot.groups().get(groupId);
        if (group == null) {
            return MenuSupport.glass();
        }
        UUID viewer = playerId;
        Map<UUID, String> memberRoles = snapshot.groupMembers().getOrDefault(groupId, Map.of());
        // 历史数据中领袖可能没有成员行，计数时补上
        int members = memberRoles.size() + (memberRoles.containsKey(group.getLeaderUuid()) ? 0 : 1);
        List<String> lore = List.of(
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_ID,
                Map.of("<group_id>", group.getGroupId())),
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_LEADER,
                Map.of("<leader>", MenuSupport.displayName(group.getLeaderUuid()))),
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_MEMBERS,
                Map.of("<members>", String.valueOf(members))),
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_ROLE,
                Map.of("<role>", IdentityPermissions.identityOf(snapshot, groupId, viewer).name()))
        );
        // 领袖身份沿用项目惯例（金色头盔），普通成员用盾牌区分
        Material material = IdentityPermissions.isLeader(snapshot, groupId, viewer)
            ? Material.GOLDEN_HELMET : Material.SHIELD;
        Icon icon = MenuSupport.icon(material,
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_ENTRY_NAME, Map.of("<name>", group.getName())), lore);
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker == null || index >= pageGroupIds.size()) {
                return;
            }
            new GroupDetailMenu(clicker, pageGroupIds.get(index), page).openMenu();
        });
        return icon;
    }

    private Icon invitesIcon() {
        Player player = player().orElse(null);
        int count = GroupService.INSTANCE.pendingInviteGroupIds(playerId).size();
        if (count == 0) {
            return MenuSupport.glass();
        }
        Icon icon = MenuSupport.icon(Material.BELL,
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_INVITES_NAME,
                Map.of("<count>", String.valueOf(count))),
            List.of(MenuSupport.text(player, Languages.MENU_GROUP_LIST_INVITES_LORE)));
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker != null) {
                new GroupInvitesMenu(clicker, page).openMenu();
            }
        });
        return icon;
    }

    private Icon createIcon() {
        Player player = player().orElse(null);
        Icon icon = MenuSupport.icon(Material.WRITABLE_BOOK,
            MenuSupport.text(player, Languages.MENU_GROUP_LIST_CREATE_NAME),
            List.of(MenuSupport.text(player, Languages.MENU_GROUP_LIST_CREATE_LORE)));
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker != null) {
                create(clicker);
            }
        });
        return icon;
    }

    /** 聊天输入「标识符 [展示名]」，等价于 {@code /land group create}。 */
    private void create(Player player) {
        ChatPrompt.ask(player, Languages.MENU_GROUP_LIST_CREATE_PROMPT, input -> {
            String[] parts = input.split("\\s+");
            String groupId = parts[0];
            String name = parts.length > 1
                ? String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) : groupId;
            AsyncReply.toPlayer(player, GroupService.INSTANCE.createGroup(player.getUniqueId(), groupId, name),
                result -> {
                    if (result.success()) {
                        LangUtils.sendLang(player, Languages.COMMAND_GROUP_CREATE_SUCCESS,
                            Map.of("<group>", name));
                    } else {
                        GroupCommand.sendFailure(CommandUtils.commonPlayer(player), result);
                    }
                    new GroupListMenu(player).openMenu();
                });
        });
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
        return title(player().orElse(null));
    }

}