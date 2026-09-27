package pers.yufiria.landguard.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.IdentityRegistry;
import pers.yufiria.landguard.identity.PermissionPoint;

import java.util.ArrayList;
import java.util.List;
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
     * 该玩家有指定权限点的用户组标识符（null 表示不限权限，即全部已加入的组）。
     * 判定统一走 {@link IdentityPermissions}，因此领袖即使没有成员行也能补全出来。
     */
    private static List<String> groupsWhere(UUID player, PermissionPoint point) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<String> ids = new ArrayList<>();
        for (String groupId : snapshot.groups().keySet()) {
            if (!IdentityPermissions.isMember(snapshot, groupId, player)) {
                continue;
            }
            if (point == null || IdentityPermissions.has(snapshot, groupId, player, point)) {
                ids.add(groupId);
            }
        }
        return ids;
    }

    /** 该玩家可邀请/踢人/指派身份的用户组标识符。 */
    static List<String> managedGroups(UUID player) {
        return groupsWhere(player, PermissionPoint.GROUP_INVITE);
    }

    /** 该玩家可解散的用户组标识符。 */
    static List<String> ownedGroups(UUID player) {
        return groupsWhere(player, PermissionPoint.GROUP_DISBAND);
    }

    /** 该玩家可放弃/扩张组领地的用户组标识符。 */
    static List<String> expandableGroups(UUID player) {
        return groupsWhere(player, PermissionPoint.CLAIM_EXPAND);
    }

    /** 该玩家当前有待处理邀请的用户组标识符。 */
    static List<String> invitedGroups(UUID player) {
        return new ArrayList<>(GroupService.INSTANCE.pendingInviteGroupIds(player));
    }

    /** 全部可用身份标识（全服统一，与具体用户组无关）。 */
    static List<String> identityIds() {
        return new ArrayList<>(IdentityRegistry.INSTANCE.ids());
    }

}
