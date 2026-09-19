package pers.yufiria.landguard.command;

import crypticlib.CrypticLibBukkit;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.lang.entry.StringLangEntry;
import crypticlib.perm.PermInfo;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.GroupRoleData;
import pers.yufiria.landguard.group.GroupFailureReason;
import pers.yufiria.landguard.group.GroupOpResult;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /land group ...}：用户组建/解散、邀请体系、成员管理、自定义角色、个人领地转让给组。
 * 所有写操作走 {@link GroupService}（单写线程原子落库），命令层只做参数解析与反馈。
 */
public final class GroupCommand extends CommandNode {

    public static final GroupCommand INSTANCE = new GroupCommand();

    private GroupCommand() {
        super(CommandInfo.builder("group").permission(new PermInfo("landguard.command.group")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        Player player = (Player) CommandUtils.invoker2Sender(invoker);
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String sub = args.get(0).toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "create" -> create(player, args);
            case "disband" -> groupNameOp(player, args, GroupService.INSTANCE::disband, Languages.COMMAND_GROUP_DISBAND_SUCCESS);
            case "invite" -> invite(player, args);
            case "accept" -> groupNameOp(player, args, GroupService.INSTANCE::acceptInvite, Languages.COMMAND_GROUP_ACCEPT_SUCCESS);
            case "deny" -> groupNameOp(player, args, GroupService.INSTANCE::denyInvite, Languages.COMMAND_GROUP_DENY_SUCCESS);
            case "leave" -> groupNameOp(player, args, GroupService.INSTANCE::leave, Languages.COMMAND_GROUP_LEAVE_SUCCESS);
            case "kick" -> kick(player, args);
            case "transfer" -> transfer(player, args);
            case "role" -> role(player, args);
            case "giveclaim" -> giveClaim(player, args);
            case "list" -> list(player);
            case "info" -> info(player, args);
            default -> LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
        }
    }

    private void create(Player player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String name = args.get(1);
        run(player, GroupService.INSTANCE.createGroup(player.getUniqueId(), name),
            Languages.COMMAND_GROUP_CREATE_SUCCESS, Map.of("group", name));
    }

    private void invite(Player player, List<String> args) {
        if (args.size() < 3) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String groupName = args.get(1);
        UUID target = resolveTarget(args.get(2));
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
            return;
        }
        GroupService.INSTANCE.invite(player.getUniqueId(), groupName, target)
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_GROUP_INVITE_SENT, Map.of(
                        "group", groupName, "player", args.get(2)));
                    Player online = Bukkit.getPlayer(target);
                    if (online != null) {
                        LangUtils.sendLang(online, Languages.COMMAND_GROUP_INVITE_RECEIVED, Map.of(
                            "group", groupName,
                            "leader", player.getName()));
                    }
                } else {
                    sendFailure(player, result);
                }
            }));
    }

    private void kick(Player player, List<String> args) {
        if (args.size() < 3) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        UUID target = resolveTarget(args.get(2));
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
            return;
        }
        run(player, GroupService.INSTANCE.kick(player.getUniqueId(), args.get(1), target),
            Languages.COMMAND_GROUP_KICK_SUCCESS, Map.of("group", args.get(1), "player", args.get(2)));
    }

    private void transfer(Player player, List<String> args) {
        if (args.size() < 3) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        UUID target = resolveTarget(args.get(2));
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
            return;
        }
        run(player, GroupService.INSTANCE.transferLeadership(player.getUniqueId(), args.get(1), target),
            Languages.COMMAND_GROUP_TRANSFER_SUCCESS, Map.of("group", args.get(1), "player", args.get(2)));
    }

    private void role(Player player, List<String> args) {
        // /land group role create <组> <roleId> <priority> [显示名...]
        // /land group role assign <组> <玩家> <roleId>
        if (args.size() < 3) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String action = args.get(1).toLowerCase(java.util.Locale.ROOT);
        if ("create".equals(action)) {
            if (args.size() < 6) {
                LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
                return;
            }
            int priority;
            try {
                priority = Integer.parseInt(args.get(4));
            } catch (NumberFormatException e) {
                LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_ROLE_ID_INVALID);
                return;
            }
            String displayName = String.join(" ", args.subList(5, args.size()));
            String roleId = args.get(3);
            String groupName = args.get(2);
            run(player, GroupService.INSTANCE.createRole(player.getUniqueId(), groupName, roleId, priority, displayName),
                Languages.COMMAND_GROUP_ROLE_CREATED, Map.of("group", groupName, "role", roleId));
        } else if ("assign".equals(action)) {
            // /land group role assign <组> <玩家> <roleId>
            if (args.size() < 5) {
                LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
                return;
            }
            String groupName = args.get(2);
            UUID target = resolveTarget(args.get(3));
            if (target == null) {
                LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
                return;
            }
            String roleId = args.get(4);
            run(player, GroupService.INSTANCE.assignRole(player.getUniqueId(), groupName, target, roleId),
                Languages.COMMAND_GROUP_ROLE_ASSIGNED,
                Map.of("group", groupName, "player", args.get(3), "role", roleId));
        } else {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
        }
    }

    private void giveClaim(Player player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String groupName = args.get(1);
        run(player, GroupService.INSTANCE.giveClaim(
                player.getUniqueId(), groupName,
                player.getWorld().getUID(),
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4),
            Languages.COMMAND_GROUP_GIVECLAIM_SUCCESS, Map.of("group", groupName));
    }

    private void list(Player player) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<String> owned = new java.util.ArrayList<>();
        for (Map.Entry<String, Map<UUID, String>> entry : snapshot.groupMembers().entrySet()) {
            if (!entry.getValue().containsKey(player.getUniqueId())) {
                continue;
            }
            GroupData group = snapshot.groups().get(entry.getKey());
            if (group != null) {
                owned.add(group.getName());
            }
        }
        if (owned.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_LIST_EMPTY);
            return;
        }
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_LIST_HEADER, Map.of("size", String.valueOf(owned.size())));
        for (String name : owned) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_LIST_ENTRY, Map.of("group", name));
        }
    }

    private void info(Player player, List<String> args) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        GroupData group = null;
        if (args.size() >= 2) {
            for (GroupData candidate : snapshot.groups().values()) {
                if (candidate.getName().equalsIgnoreCase(args.get(1))) {
                    group = candidate;
                    break;
                }
            }
        } else {
            for (Map.Entry<String, Map<UUID, String>> entry : snapshot.groupMembers().entrySet()) {
                if (entry.getValue().containsKey(player.getUniqueId())) {
                    group = snapshot.groups().get(entry.getKey());
                    break;
                }
            }
        }
        if (group == null) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_NOT_FOUND);
            return;
        }
        Map<UUID, String> members = snapshot.groupMembers().getOrDefault(group.getGroupId(), Map.of());
        Map<String, GroupRoleData> roles = snapshot.groupRoles().getOrDefault(group.getGroupId(), Map.of());
        StringBuilder memberNames = new StringBuilder();
        for (UUID member : members.keySet()) {
            if (memberNames.length() > 0) {
                memberNames.append(", ");
            }
            memberNames.append(nameOf(member));
        }
        StringBuilder roleNames = new StringBuilder();
        roleNames.append(Roles.OWNER).append(", ").append(Roles.MANAGER).append(", ").append(Roles.MEMBER);
        for (GroupRoleData role : roles.values()) {
            roleNames.append(", ").append(role.getName());
        }
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_HEADER);
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_NAME, Map.of("group", group.getName()));
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_LEADER, Map.of("leader", nameOf(group.getLeaderUuid())));
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_MEMBERS, Map.of("members", memberNames.toString()));
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_ROLES, Map.of("roles", roleNames.toString()));
    }

    private interface GroupNameOp {

        java.util.concurrent.CompletableFuture<GroupOpResult> run(UUID actor, String groupName);

    }

    private void groupNameOp(Player player, List<String> args, GroupNameOp op, StringLangEntry successEntry) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String groupName = args.get(1);
        run(player, op.run(player.getUniqueId(), groupName), successEntry, Map.of("group", groupName));
    }

    private void run(Player player, java.util.concurrent.CompletableFuture<GroupOpResult> future,
                     StringLangEntry successEntry, Map<String, String> formats) {
        future.whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
            if (!player.isOnline() || throwable != null || result == null) {
                return;
            }
            if (result.success()) {
                LangUtils.sendLang(player, successEntry, formats);
            } else {
                sendFailure(player, result);
            }
        }));
    }

    static void sendFailure(Player player, GroupOpResult result) {
        StringLangEntry entry = switch (result.failureReason()) {
            case NAME_TAKEN -> Languages.COMMAND_GROUP_FAIL_NAME_TAKEN;
            case INVALID_NAME -> Languages.COMMAND_GROUP_FAIL_INVALID_NAME;
            case GROUP_NOT_FOUND -> Languages.COMMAND_GROUP_FAIL_NOT_FOUND;
            case NOT_LEADER -> Languages.COMMAND_GROUP_FAIL_NOT_LEADER;
            case NOT_MANAGER -> Languages.COMMAND_GROUP_FAIL_NOT_MANAGER;
            case NOT_MEMBER -> Languages.COMMAND_GROUP_FAIL_NOT_MEMBER;
            case TARGET_NOT_MEMBER -> Languages.COMMAND_GROUP_FAIL_TARGET_NOT_MEMBER;
            case ALREADY_MEMBER -> Languages.COMMAND_GROUP_FAIL_ALREADY_MEMBER;
            case NO_INVITE -> Languages.COMMAND_GROUP_FAIL_NO_INVITE;
            case LEADER_CANNOT_LEAVE -> Languages.COMMAND_GROUP_FAIL_LEADER_CANNOT_LEAVE;
            case CANNOT_KICK -> Languages.COMMAND_GROUP_FAIL_CANNOT_KICK;
            case ROLE_EXISTS -> Languages.COMMAND_GROUP_FAIL_ROLE_EXISTS;
            case ROLE_NOT_FOUND -> Languages.COMMAND_GROUP_FAIL_ROLE_NOT_FOUND;
            case ROLE_ID_INVALID -> Languages.COMMAND_GROUP_FAIL_ROLE_ID_INVALID;
            case ROLE_BUILTIN -> Languages.COMMAND_GROUP_FAIL_ROLE_BUILTIN;
            case CLAIM_NOT_FOUND -> Languages.COMMAND_GROUP_FAIL_CLAIM_NOT_FOUND;
            case NOT_CLAIM_OWNER -> Languages.COMMAND_GROUP_FAIL_NOT_CLAIM_OWNER;
        };
        LangUtils.sendLang(player, entry);
    }

    /**
     * 只解析在线玩家或本地缓存中的离线玩家，绝不触发阻塞式网络查询。
     */
    private static @Nullable UUID resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayer(uuid);
        return cached.getName() != null ? cached.getName() : uuid.toString().substring(0, 8);
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
