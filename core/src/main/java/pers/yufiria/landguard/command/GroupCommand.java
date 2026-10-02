package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.command.annotation.Subcommand;
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
import pers.yufiria.landguard.group.GroupOpResult;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.identity.Identity;
import pers.yufiria.landguard.identity.IdentityRegistry;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * {@code /land group ...}：用户组建/解散、邀请体系、成员管理、身份指派、个人领地转让给组。
 * 子命令交由框架节点树分派（{@code @Subcommand}）：每个动作独立权限节点、独立补全，
 * 参数列表已去掉动作名（{@code args.get(0)} 即该动作的第一个参数）。
 * 所有写操作走 {@link GroupService}（单写线程原子落库），命令层只做参数解析与反馈。
 */
public final class GroupCommand extends CommandNode {

    private static final String PERM_PREFIX = "landguard.command.group.";
    public static final GroupCommand INSTANCE = new GroupCommand();

    private GroupCommand() {
        super(CommandInfo.builder("group").permission(new PermInfo("landguard.command.group")).build());
    }

    // ================= 子命令节点 =================

    @Subcommand
    CommandNode create = action("create", this::create);
    @Subcommand
    CommandNode disband = action("disband", (player, args) ->
        groupNameOp(player, args, GroupService.INSTANCE::disband, Languages.COMMAND_GROUP_DISBAND_SUCCESS),
        ownedGroups());
    @Subcommand
    CommandNode invite = action("invite", this::invite, managedGroupsThenPlayers());
    @Subcommand
    CommandNode accept = action("accept", (player, args) ->
        groupNameOp(player, args, GroupService.INSTANCE::acceptInvite, Languages.COMMAND_GROUP_ACCEPT_SUCCESS),
        invitedGroups());
    @Subcommand
    CommandNode deny = action("deny", (player, args) ->
        groupNameOp(player, args, GroupService.INSTANCE::denyInvite, Languages.COMMAND_GROUP_DENY_SUCCESS),
        invitedGroups());
    @Subcommand
    CommandNode leave = action("leave", (player, args) ->
        groupNameOp(player, args, GroupService.INSTANCE::leave, Languages.COMMAND_GROUP_LEAVE_SUCCESS),
        memberGroups());
    @Subcommand
    CommandNode kick = action("kick", this::kick, managedGroupsThenPlayers());
    @Subcommand
    CommandNode transfer = action("transfer", this::transfer, ownedGroupsThenPlayers());
    @Subcommand
    CommandNode role = new RoleNode();
    @Subcommand
    CommandNode rename = action("rename", this::rename, managedGroups());
    @Subcommand
    CommandNode list = action("list", (player, args) -> list(player));
    @Subcommand
    CommandNode info = action("info", this::info, (player, args) ->
        args.size() > 1 ? List.of() : CommandCompletions.allGroups());

    /** 无子命令或子命令名未命中时，框架回落到本节点：输出用法。 */
    @Override
    public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        LangUtils.sendLang(invoker, Languages.COMMAND_GROUP_USAGE);
    }

    private static CommandNode action(String name, BiConsumer<CommonPlayer, List<String>> handler) {
        return new PlayerOnlyCommand(PERM_PREFIX + name, name, handler);
    }

    private static CommandNode action(String name, BiConsumer<CommonPlayer, List<String>> handler,
                                      PlayerOnlyCommand.TabCompleter completer) {
        return new PlayerOnlyCommand(PERM_PREFIX + name, name, handler, completer);
    }

    // ================= 参数补全 =================

    /** 第一参数为该玩家已加入的用户组名。 */
    private static PlayerOnlyCommand.TabCompleter memberGroups() {
        return (player, args) -> args.size() > 1 ? List.of() : CommandCompletions.memberGroups(player.uniqueId());
    }

    /** 第一参数为该玩家可解散 / 转让领袖的用户组名（组内身份拥有 disband 权限点）。 */
    private static PlayerOnlyCommand.TabCompleter ownedGroups() {
        return (player, args) -> args.size() > 1 ? List.of() : CommandCompletions.ownedGroups(player.uniqueId());
    }

    /** 第一参数为该玩家有待处理邀请的用户组名。 */
    private static PlayerOnlyCommand.TabCompleter invitedGroups() {
        return (player, args) -> args.size() > 1 ? List.of() : CommandCompletions.invitedGroups(player.uniqueId());
    }

    /** 第一参数为该玩家可管理的用户组名（组内身份拥有 invite 权限点）。 */
    private static PlayerOnlyCommand.TabCompleter managedGroups() {
        return (player, args) -> args.size() > 1 ? List.of() : CommandCompletions.managedGroups(player.uniqueId());
    }

    /** 第一参数为可管理的用户组名，第二参数为在线玩家名。 */
    private static PlayerOnlyCommand.TabCompleter managedGroupsThenPlayers() {
        return (player, args) -> switch (args.size()) {
            case 1 -> CommandCompletions.managedGroups(player.uniqueId());
            case 2 -> CommandCompletions.onlinePlayers();
            default -> List.of();
        };
    }

    /** 第一参数为该玩家拥有的用户组名，第二参数为在线玩家名。 */
    private static PlayerOnlyCommand.TabCompleter ownedGroupsThenPlayers() {
        return (player, args) -> switch (args.size()) {
            case 1 -> CommandCompletions.ownedGroups(player.uniqueId());
            case 2 -> CommandCompletions.onlinePlayers();
            default -> List.of();
        };
    }

    /** 第一参数为可管理的用户组名，第二参数为在线玩家名，第三参数为该组可用的身份标识（identities.yml 的 id）。 */
    private static PlayerOnlyCommand.TabCompleter managedGroupsThenPlayerThenRole() {
        return (player, args) -> switch (args.size()) {
            case 1 -> CommandCompletions.managedGroups(player.uniqueId());
            case 2 -> CommandCompletions.onlinePlayers();
            case 3 -> CommandCompletions.identityIds();
            default -> List.of();
        };
    }

    /** {@code /land group role assign|list ...}：二级节点，本身只负责缺参提示。 */
    static final class RoleNode extends CommandNode {

        @Subcommand
        CommandNode assign = new PlayerOnlyCommand(PERM_PREFIX + "role.assign", "assign",
            (player, args) -> INSTANCE.assignRole(player, args), managedGroupsThenPlayerThenRole());

        @Subcommand
        CommandNode list = new PlayerOnlyCommand(PERM_PREFIX + "role.list", "list",
            (player, args) -> INSTANCE.listIdentities(player, args),
            (player, args) -> args.size() > 1 ? List.of() : CommandCompletions.allGroups());

        RoleNode() {
            super(CommandInfo.builder("role").permission(new PermInfo(PERM_PREFIX + "role")).build());
        }

        @Override
        public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
            if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
                return;
            }
            LangUtils.sendLang(invoker, Languages.COMMAND_GROUP_USAGE);
        }

        @Override
        public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
            LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
        }
    }

    // ================= 动作实现 =================

    private void create(CommonPlayer player, List<String> args) {
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        // 第一参数是标识符；展示名可选，缺省时由服务层回退为标识符
        String groupId = args.getFirst();
        String name = args.size() > 1 ? String.join(" ", args.subList(1, args.size())) : groupId;
        run(player, GroupService.INSTANCE.createGroup(player.uniqueId(), groupId, name),
            Languages.COMMAND_GROUP_CREATE_SUCCESS, Map.of("<group>", name));
    }

    private void rename(CommonPlayer player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_RENAME_USAGE);
            return;
        }
        String groupId = args.getFirst();
        String name = String.join(" ", args.subList(1, args.size()));
        run(player, GroupService.INSTANCE.renameGroup(player.uniqueId(), groupId, name),
            Languages.COMMAND_GROUP_RENAME_SUCCESS, Map.of("<group>", name));
    }

    private void invite(CommonPlayer player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String groupName = args.get(0);
        UUID target = resolveTarget(args.get(1));
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
            return;
        }
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        AsyncReply.toPlayer(bukkitPlayer, GroupService.INSTANCE.invite(player.uniqueId(), groupName, target), result -> {
            if (result.success()) {
                LangUtils.sendLang(player, Languages.COMMAND_GROUP_INVITE_SENT, Map.of(
                    "<group>", groupName, "<player>", args.get(1)));
                Player online = Bukkit.getPlayer(target);
                if (online != null) {
                    // 被邀请者需要用标识符执行 accept，故邀请消息里必须带上它
                    GroupData invited = GroupService.findById(DataStore.INSTANCE.snapshot(), groupName);
                    LangUtils.sendLang(online, Languages.COMMAND_GROUP_INVITE_RECEIVED, Map.of(
                        "<group>", invited == null ? groupName : invited.getName(),
                        "<group_id>", groupName,
                        "<leader>", player.name()));
                }
            } else {
                sendFailure(player, result);
            }
        });
    }

    private void kick(CommonPlayer player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        UUID target = resolveTarget(args.get(1));
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
            return;
        }
        run(player, GroupService.INSTANCE.kick(player.uniqueId(), args.get(0), target),
            Languages.COMMAND_GROUP_KICK_SUCCESS, Map.of("<group>", args.get(0), "<player>", args.get(1)));
    }

    private void transfer(CommonPlayer player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        UUID target = resolveTarget(args.get(1));
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
            return;
        }
        run(player, GroupService.INSTANCE.transferLeadership(player.uniqueId(), args.get(0), target),
            Languages.COMMAND_GROUP_TRANSFER_SUCCESS, Map.of("<group>", args.get(0), "<player>", args.get(1)));
    }

    /** {@code /land group role assign <组> <玩家> <roleId>} */
    private void assignRole(CommonPlayer player, List<String> args) {
        if (args.size() < 3) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String groupName = args.get(0);
        UUID target = resolveTarget(args.get(1));
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_TARGET_NOT_FOUND);
            return;
        }
        String roleId = args.get(2);
        run(player, GroupService.INSTANCE.assignRole(player.uniqueId(), groupName, target, roleId),
            Languages.COMMAND_GROUP_ROLE_ASSIGNED,
            Map.of("<group>", groupName, "<player>", args.get(1), "<role>", roleId));
    }

    private void list(CommonPlayer player) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<GroupData> owned = new ArrayList<>();
        for (Map.Entry<String, Map<UUID, String>> entry : snapshot.groupMembers().entrySet()) {
            if (!entry.getValue().containsKey(player.uniqueId())) {
                continue;
            }
            GroupData group = snapshot.groups().get(entry.getKey());
            if (group != null) {
                owned.add(group);
            }
        }
        if (owned.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_LIST_EMPTY);
            return;
        }
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_LIST_HEADER, Map.of("<size>", String.valueOf(owned.size())));
        for (GroupData group : owned) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_LIST_ENTRY, Map.of(
                "<group>", group.getName(), "<group_id>", group.getGroupId()));
        }
    }

    /** {@code /land group role list [组标识符]}：列出全服身份；给定已存在的组时附带各身份的成员数。 */
    private void listIdentities(CommonPlayer player, List<String> args) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<Identity> identities = IdentityRegistry.INSTANCE.all();
        if (!args.isEmpty()) {
            GroupData group = GroupService.findById(snapshot, args.getFirst());
            if (group == null) {
                LangUtils.sendLang(player, Languages.COMMAND_GROUP_FAIL_NOT_FOUND);
                return;
            }
            Map<UUID, String> members = snapshot.groupMembers().getOrDefault(group.getGroupId(), Map.of());
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_ROLE_LIST_HEADER,
                Map.of("<size>", String.valueOf(identities.size())));
            for (Identity identity : identities) {
                long count = members.values().stream().filter(identity.id()::equals).count();
                LangUtils.sendLang(player, Languages.COMMAND_GROUP_ROLE_LIST_GROUP_ENTRY, Map.of(
                    "<id>", identity.id(),
                    "<name>", identity.name(),
                    "<priority>", String.valueOf(identity.priority()),
                    "<members>", String.valueOf(count)));
            }
            return;
        }
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_ROLE_LIST_HEADER,
            Map.of("<size>", String.valueOf(identities.size())));
        for (Identity identity : identities) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_ROLE_LIST_ENTRY, Map.of(
                "<id>", identity.id(),
                "<name>", identity.name(),
                "<priority>", String.valueOf(identity.priority()),
                "<permissions>", String.valueOf(identity.permissions().size()),
                "<behaviors>", String.valueOf(identity.behaviors().size())));
        }
    }

    private void info(CommonPlayer player, List<String> args) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        GroupData group;
        if (!args.isEmpty()) {
            group = GroupService.findById(snapshot, args.getFirst());
        } else {
            group = null;
            for (Map.Entry<String, Map<UUID, String>> entry : snapshot.groupMembers().entrySet()) {
                if (entry.getValue().containsKey(player.uniqueId())) {
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
        StringBuilder memberNames = new StringBuilder();
        for (UUID member : members.keySet()) {
            if (!memberNames.isEmpty()) {
                memberNames.append(", ");
            }
            memberNames.append(nameOf(member));
        }
        StringBuilder roleNames = new StringBuilder();
        for (Identity identity : IdentityRegistry.INSTANCE.all()) {
            if (!roleNames.isEmpty()) {
                roleNames.append(", ");
            }
            roleNames.append(identity.id()).append("(").append(identity.name()).append(")");
        }
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_HEADER);
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_NAME, Map.of(
            "<group>", group.getName(), "<group_id>", group.getGroupId()));
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_LEADER, Map.of("<leader>", nameOf(group.getLeaderUuid())));
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_MEMBERS, Map.of("<members>", memberNames.toString()));
        LangUtils.sendLang(player, Languages.COMMAND_GROUP_INFO_ROLES, Map.of("<roles>", roleNames.toString()));
    }

    private interface GroupNameOp {

        CompletableFuture<GroupOpResult> run(UUID actor, String groupName);

    }

    private void groupNameOp(CommonPlayer player, List<String> args, GroupNameOp op, StringLangEntry successEntry) {
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_GROUP_USAGE);
            return;
        }
        String groupName = args.getFirst();
        run(player, op.run(player.uniqueId(), groupName), successEntry, Map.of("<group>", groupName));
    }

    private void run(CommonPlayer player, CompletableFuture<GroupOpResult> future,
                     StringLangEntry successEntry, Map<String, String> formats) {
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        AsyncReply.toPlayer(bukkitPlayer, future, result -> {
            if (result.success()) {
                LangUtils.sendLang(player, successEntry, formats);
            } else {
                sendFailure(player, result);
            }
        });
    }

    /** 用户组操作失败 → 语言条目的统一映射；命令层与 GUI 转让路径共用。 */
    public static void sendFailure(CommonPlayer player, GroupOpResult result) {
        StringLangEntry entry = null;
        if (result.failureReason() != null) {
            entry = switch (result.failureReason()) {
                case KEY_TAKEN -> Languages.COMMAND_GROUP_FAIL_KEY_TAKEN;
                case INVALID_KEY -> Languages.COMMAND_GROUP_FAIL_INVALID_KEY;
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
                case CANNOT_ASSIGN -> Languages.COMMAND_GROUP_FAIL_CANNOT_ASSIGN;
                case IDENTITY_NOT_FOUND -> Languages.COMMAND_GROUP_FAIL_IDENTITY_NOT_FOUND;
                case CLAIM_NOT_FOUND -> Languages.COMMAND_GROUP_FAIL_CLAIM_NOT_FOUND;
                case NOT_CLAIM_OWNER -> Languages.COMMAND_GROUP_FAIL_NOT_CLAIM_OWNER;
                case GROUP_HAS_CLAIM -> Languages.COMMAND_GROUP_FAIL_GROUP_HAS_CLAIM;
                case GROUP_QUOTA_EXCEEDED -> Languages.COMMAND_GROUP_FAIL_GROUP_QUOTA_EXCEEDED;
                case JOIN_LIMIT_EXCEEDED -> Languages.COMMAND_GROUP_FAIL_JOIN_LIMIT_EXCEEDED;
                case OWN_LIMIT_EXCEEDED -> Languages.COMMAND_GROUP_FAIL_OWN_LIMIT_EXCEEDED;
            };
        }
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
