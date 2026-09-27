package pers.yufiria.landguard.command;

import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.command.CommandTree;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 子命令节点树结构回归：{@code @Subcommand} 字段由框架 {@code scanNodes()} 递归收集。
 * 保证 /land claim、/land unclaim、/land group 与 /land admin 的每个分支/动作都是独立节点
 * （独立权限节点、独立补全），且二级节点（role）的子动作同样被递归注册。
 */
public class CommandTreeStructureTest {

    private static final List<String> GROUP_ACTIONS = List.of(
        "create", "disband", "invite", "accept", "deny", "leave",
        "kick", "transfer", "rename", "role", "list", "info"
    );

    private static final List<String> ADMIN_ACTIONS = List.of(
        "claim", "unclaim", "transfer", "release", "exempt", "rename", "info", "orphans", "run"
    );

    private static final List<String> CLAIM_ACTIONS = List.of("auto", "radius");

    @Test
    void claimCommandExposesBranchesAsNodes() {
        ClaimCommand claim = ClaimCommand.INSTANCE;
        claim.scanNodes();

        assertEquals(Set.copyOf(CLAIM_ACTIONS), claim.nodes().keySet());
        // 三种形态是同一能力的变体，共用根节点权限，避免只授过根权限的服务器失去子命令
        for (String action : CLAIM_ACTIONS) {
            assertEquals("landguard.command.claim",
                claim.nodes().get(action).commandInfo().permission().permission(),
                "claim 分支 " + action + " 应沿用根节点权限");
        }
    }

    @Test
    void unclaimCommandExposesAutoAsNode() {
        UnclaimCommand unclaim = UnclaimCommand.INSTANCE;
        unclaim.scanNodes();

        assertEquals(Set.of("auto"), unclaim.nodes().keySet());
        assertEquals("landguard.command.unclaim",
            unclaim.nodes().get("auto").commandInfo().permission().permission());
    }

    @Test
    void groupCommandExposesEveryActionAsNode() {
        GroupCommand group = GroupCommand.INSTANCE;
        group.scanNodes();

        assertEquals(Set.copyOf(GROUP_ACTIONS), group.nodes().keySet());
        assertEquals("landguard.command.group", group.commandInfo().permission().permission());
    }

    @Test
    void nestedRoleNodeRegistersItsOwnActions() {
        GroupCommand group = GroupCommand.INSTANCE;
        group.scanNodes();

        CommandNode role = group.nodes().get("role");
        assertEquals(Set.of("assign", "list"), role.nodes().keySet());
        assertEquals("landguard.command.group.role", role.commandInfo().permission().permission());
    }

    @Test
    void adminCommandExposesEveryActionAsNode() {
        AdminCommand admin = AdminCommand.INSTANCE;
        admin.scanNodes();

        assertEquals(Set.copyOf(ADMIN_ACTIONS), admin.nodes().keySet());
        assertEquals("landguard.command.admin", admin.commandInfo().permission().permission());
    }

    @Test
    void frameworkRecursionRegistersNestedNodesFromRoot() {
        CommandTree root = new CommandTree(CommandInfo.builder("testroot").build());
        root.addNode(GroupCommand.INSTANCE);
        root.scanNodes();

        CommandNode group = root.nodes().get("group");
        assertEquals(Set.copyOf(GROUP_ACTIONS), group.nodes().keySet());
        assertEquals(Set.of("assign", "list"), group.nodes().get("role").nodes().keySet());
    }

    @Test
    void everyActionNodeHasItsOwnPermission() {
        GroupCommand group = GroupCommand.INSTANCE;
        group.scanNodes();
        for (String action : GROUP_ACTIONS) {
            assertEquals("landguard.command.group." + action,
                group.nodes().get(action).commandInfo().permission().permission(),
                "group 动作 " + action + " 应使用独立权限节点");
        }
        assertEquals("landguard.command.group.role.assign",
            group.nodes().get("role").nodes().get("assign").commandInfo().permission().permission());
        assertEquals("landguard.command.group.role.list",
            group.nodes().get("role").nodes().get("list").commandInfo().permission().permission());

        AdminCommand admin = AdminCommand.INSTANCE;
        admin.scanNodes();
        for (String action : ADMIN_ACTIONS) {
            assertEquals("landguard.command.admin." + action,
                admin.nodes().get(action).commandInfo().permission().permission(),
                "admin 动作 " + action + " 应使用独立权限节点");
        }
    }

}
