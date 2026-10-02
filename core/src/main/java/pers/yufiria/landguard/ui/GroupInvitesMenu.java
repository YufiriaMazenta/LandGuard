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
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.*;
import java.util.function.Supplier;

/**
 * 待处理组织邀请：左键接受、右键拒绝，等价于 {@code /land group accept|deny <组标识符>}。
 * 邀请为内存态（重启清空），列表随快照与邀请集合实时派生。
 */
public class GroupInvitesMenu extends Menu {

    static final int PAGE_SIZE = 45;
    static final List<String> LAYOUT = List.of(
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "pggggggnr"
    );

    private final int listPage;
    private int page;
    private final List<String> pageGroupIds = new ArrayList<>();

    public GroupInvitesMenu(@NotNull Player player, int listPage) {
        super(player);
        this.listPage = listPage;
        this.page = 0;
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('p', () -> MenuSupport.arrow(player, false, page > 0, () -> changePage(page - 1)));
        icons.put('n', () -> MenuSupport.arrow(player, true, page < maxPage(),
            () -> changePage(page + 1)));
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new GroupListMenu(player, listPage).openMenu()));
        return new MenuDisplay(title(player), new MenuLayout(LAYOUT, icons));
    }

    private String title(Player player) {
        return MenuSupport.text(player, Languages.MENU_GROUP_INVITES_TITLE, Map.of(
            "<page>", String.valueOf(page + 1),
            "<max_page>", String.valueOf(maxPage() + 1)
        ));
    }

    /** 有待处理邀请且该组仍存在的组织标识符，按展示名排序。 */
    private List<String> invitedGroupIds() {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<GroupData> groups = new ArrayList<>();
        for (String groupId : GroupService.INSTANCE.pendingInviteGroupIds(playerId)) {
            GroupData group = snapshot.groups().get(groupId);
            if (group != null) {
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
        return Math.max(0, (invitedGroupIds().size() - 1) / PAGE_SIZE);
    }

    @Override
    public void onLayoutUpdated() {
        pageGroupIds.clear();
        List<String> all = invitedGroupIds();
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
        }
        int start = page * PAGE_SIZE;
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        Player player = player().orElse(null);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= all.size()) {
                break;
            }
            String groupId = all.get(index);
            pageGroupIds.add(groupId);
            setIcon(slot, inviteIcon(player, snapshot, groupId, slot));
        }
        if (all.isEmpty()) {
            setIcon(22, MenuSupport.icon(Material.BARRIER,
                MenuSupport.text(player, Languages.MENU_GROUP_INVITES_EMPTY), null));
        }
    }

    private Icon inviteIcon(Player player, DataSnapshot snapshot, String groupId, int slot) {
        GroupData group = snapshot.groups().get(groupId);
        String name = group == null ? groupId : group.getName();
        List<String> lore = List.of(
            MenuSupport.text(player, Languages.MENU_GROUP_INVITES_ENTRY_GROUP, Map.of("<name>", name)),
            MenuSupport.text(player, Languages.MENU_GROUP_INVITES_ENTRY_ACTION)
        );
        Icon icon = MenuSupport.icon(Material.WRITABLE_BOOK, name, lore);
        icon.setClickAction(event -> {
            Player clicker = player().orElse(null);
            if (clicker == null || slot >= pageGroupIds.size()) {
                return;
            }
            String invited = pageGroupIds.get(slot);
            boolean deny = event != null && event.isRightClick();
            respond(clicker, invited, !deny, groupName(invited));
        });
        return icon;
    }

    private void respond(Player player, String groupId, boolean accept, String groupName) {
        AsyncReply.toPlayer(player,
            accept ? GroupService.INSTANCE.acceptInvite(player.getUniqueId(), groupId)
                : GroupService.INSTANCE.denyInvite(player.getUniqueId(), groupId),
            result -> {
                if (result.success()) {
                    LangUtils.sendLang(player, accept ? Languages.COMMAND_GROUP_ACCEPT_SUCCESS
                        : Languages.COMMAND_GROUP_DENY_SUCCESS, Map.of("<group>", groupName));
                } else {
                    GroupCommand.sendFailure(CommandUtils.commonPlayer(player), result);
                }
                new GroupInvitesMenu(player, listPage).openMenu();
            });
    }

    private String groupName(String groupId) {
        GroupData group = DataStore.INSTANCE.snapshot().groups().get(groupId);
        return group == null ? groupId : group.getName();
    }

    private void changePage(int target) {
        if (target < 0 || target > maxPage()) {
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