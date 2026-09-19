package pers.yufiria.landguard.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.GroupRoleData;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.Roles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 命令参数补全的候选来源：统一从数据快照/在线玩家派生。
 * 候选只做「可选值提示」，权限与合法性仍由各命令自身校验。
 */
final class CommandCompletions {

    private CommandCompletions() {
    }

    /** 在线玩家名。 */
    static List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    /** 全部用户组标识符。 */
    static List<String> allGroups() {
        return new ArrayList<>(DataStore.INSTANCE.snapshot().groups().keySet());
    }

    /** 该玩家已加入的用户组标识符。 */
    static List<String> memberGroups(UUID player) {
        return groupsWhere(player, null);
    }

    /**
     * 该玩家可管理的用户组标识符：组内角色为 owner 或 manager。
     * 传入 null 表示不限定角色（即全部已加入的组）。
     */
    private static List<String> groupsWhere(UUID player, List<String> allowedRoles) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<String> ids = new ArrayList<>();
        snapshot.groupMembers().forEach((groupId, members) -> {
            String role = members.get(player);
            if (role == null || (allowedRoles != null && !allowedRoles.contains(role))) {
                return;
            }
            if (snapshot.groups().containsKey(groupId)) {
                ids.add(groupId);
            }
        });
        return ids;
    }

    /** 该玩家在组内角色为 owner 或 manager 的用户组标识符。 */
    static List<String> managedGroups(UUID player) {
        return groupsWhere(player, List.of(Roles.OWNER, Roles.MANAGER));
    }

    /** 该玩家在组内角色为 owner 的用户组标识符。 */
    static List<String> ownedGroups(UUID player) {
        return groupsWhere(player, List.of(Roles.OWNER));
    }

    /** 该玩家当前有待处理邀请的用户组标识符。 */
    static List<String> invitedGroups(UUID player) {
        return new ArrayList<>(GroupService.INSTANCE.pendingInviteGroupIds(player));
    }

    /** 指定用户组的可选角色标识：内置角色 + 该组自定义角色。 */
    static List<String> groupRoles(String groupId) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        GroupData group = GroupService.findById(snapshot, groupId);
        if (group == null) {
            return List.of();
        }
        List<String> roles = new ArrayList<>(List.of(Roles.OWNER, Roles.MANAGER, Roles.MEMBER));
        for (GroupRoleData role : snapshot.groupRoles().getOrDefault(group.getGroupId(), Map.of()).values()) {
            roles.add(role.getRoleId());
        }
        return roles;
    }

}
