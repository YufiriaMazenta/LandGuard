package pers.yufiria.landguard.identity;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerProvider;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 身份注册表与统一授权入口：种子身份等价历史行为、未注册身份按「成员」回落、
 * 配置整体替换后删除的身份立即失效（对照 FlagRegistry 逐条 put 无 clear() 的残留问题）。
 */
public class IdentityRegistryTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final String GROUP = "guild";
    private static final UUID WORLD = UUID.randomUUID();

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @AfterEach
    void restore() {
        IdentityRegistry.INSTANCE.resetToBuiltins();
    }

    @Test
    void builtinSeedsMatchLegacyBehaviorMatrix() {
        IdentityRegistry registry = IdentityRegistry.INSTANCE;
        assertEquals(Set.of(Roles.OWNER, Roles.MANAGER, Roles.MEMBER, Roles.VISITOR), registry.ids());
        assertEquals(Roles.OWNER, registry.leaderIdentityId());
        assertEquals(Roles.VISITOR, registry.defaultIdentityId());

        // 行为矩阵逐项等价旧 BuiltinFlagDefaults
        assertTrue(registry.behaviorAllows(Roles.OWNER, BuiltinFlags.CONTAINER.id()));
        assertTrue(registry.behaviorAllows(Roles.MANAGER, BuiltinFlags.BANK.id()));
        assertTrue(registry.behaviorAllows(Roles.MEMBER, BuiltinFlags.PLACE.id()));
        assertFalse(registry.behaviorAllows(Roles.MEMBER, BuiltinFlags.CONTAINER.id()));
        assertFalse(registry.behaviorAllows(Roles.MEMBER, BuiltinFlags.BANK.id()));
        assertTrue(registry.behaviorAllows(Roles.VISITOR, BuiltinFlags.CRAFTING.id()));
        assertFalse(registry.behaviorAllows(Roles.VISITOR, BuiltinFlags.PLACE.id()));

        // 管理权限点：MANAGER 拥有扩张与放弃组领地（本次需求），MEMBER 无任何管理权限
        assertTrue(registry.hasPermission(Roles.MANAGER, PermissionPoint.CLAIM_EXPAND));
        assertTrue(registry.hasPermission(Roles.MANAGER, PermissionPoint.CLAIM_UNCLAIM));
        assertFalse(registry.hasPermission(Roles.MANAGER, PermissionPoint.GROUP_DISBAND));
        assertTrue(registry.hasPermission(Roles.OWNER, PermissionPoint.GROUP_DISBAND));
        assertFalse(registry.hasPermission(Roles.MEMBER, PermissionPoint.GROUP_INVITE));
        assertFalse(registry.hasPermission(Roles.VISITOR, PermissionPoint.CLAIM_EXPAND));
    }

    @Test
    void unknownIdentityFallsBackToMember() {
        IdentityRegistry registry = IdentityRegistry.INSTANCE;
        // 旧库遗留的自定义身份：按「成员」生效（比历史的全拒更宽松且可解释）
        assertTrue(registry.behaviorAllows("veteran", BuiltinFlags.PLACE.id()));
        assertFalse(registry.behaviorAllows("veteran", BuiltinFlags.CONTAINER.id()));
        assertFalse(registry.hasPermission("veteran", PermissionPoint.CLAIM_EXPAND));
        assertEquals(Roles.MEMBER, registry.resolve("veteran").id());
        // 未注册身份不在注册表里（供指派合法性校验使用）
        assertFalse(registry.isRegistered("veteran"));
        assertNull(registry.get("veteran"));
    }

    @Test
    void replaceAllDropsRemovedIdentityImmediately() {
        IdentityRegistry.INSTANCE.replaceAll(List.of(
            new Identity("owner", "领袖", 100, true, false, Set.of(), Set.of()),
            new Identity("visitor", "访客", 0, false, true, Set.of(), Set.of()),
            new Identity("scout", "斥候", 10, false, false,
                Set.of(PermissionPoint.CLAIM_EXPAND.key()), Set.of(BuiltinFlags.CRAFTING.id()))
        ));
        assertTrue(IdentityRegistry.INSTANCE.isRegistered("scout"));
        assertFalse(IdentityRegistry.INSTANCE.isRegistered(Roles.MANAGER));

        IdentityRegistry.INSTANCE.replaceAll(List.of(
            new Identity("owner", "领袖", 100, true, false, Set.of(), Set.of()),
            new Identity("visitor", "访客", 0, false, true, Set.of(), Set.of())
        ));
        assertFalse(IdentityRegistry.INSTANCE.isRegistered("scout"), "删除的身份必须立即失效");
        assertTrue(IdentityRegistry.INSTANCE.all().size() == 2);
    }

    @Test
    void anchorsResolveByMarkThenConventionThenPriority() {
        // 显式标记优先（id 改了也认）
        IdentityRegistry.INSTANCE.replaceAll(List.of(
            new Identity("chief", "族长", 10, true, false, Set.of(), Set.of()),
            new Identity("guest", "路人", 1, false, true, Set.of(), Set.of())
        ));
        assertEquals("chief", IdentityRegistry.INSTANCE.leaderIdentityId());
        assertEquals("guest", IdentityRegistry.INSTANCE.defaultIdentityId());

        // 无标记时按 id 约定兜底
        IdentityRegistry.INSTANCE.replaceAll(List.of(
            new Identity(Roles.OWNER, "领袖", 5, false, false, Set.of(), Set.of()),
            new Identity(Roles.VISITOR, "访客", 1, false, false, Set.of(), Set.of())
        ));
        assertEquals(Roles.OWNER, IdentityRegistry.INSTANCE.leaderIdentityId());
        assertEquals(Roles.VISITOR, IdentityRegistry.INSTANCE.defaultIdentityId());

        // 标记与约定都缺失时按优先级取（领袖最高、默认最低）
        IdentityRegistry.INSTANCE.replaceAll(List.of(
            new Identity("high", "高", 90, false, false, Set.of(), Set.of()),
            new Identity("low", "低", 5, false, false, Set.of(), Set.of())
        ));
        assertEquals("high", IdentityRegistry.INSTANCE.leaderIdentityId());
        assertEquals("low", IdentityRegistry.INSTANCE.defaultIdentityId());

        // 空集合保留种子
        IdentityRegistry.INSTANCE.replaceAll(List.of());
        assertEquals(Set.of(Roles.OWNER, Roles.MANAGER, Roles.MEMBER, Roles.VISITOR),
            IdentityRegistry.INSTANCE.ids());
    }

    @Test
    void wildcardBehaviorsAllowEverything() {
        IdentityRegistry.INSTANCE.register(new Identity("god", "全能", 200, false, false,
            Set.of(), Set.of(Identity.ALL_BEHAVIORS)));
        for (var flag : List.of(BuiltinFlags.PLACE, BuiltinFlags.CONTAINER, BuiltinFlags.BANK,
            BuiltinFlags.PVP)) {
            assertTrue(IdentityRegistry.INSTANCE.behaviorAllows("god", flag.id()));
        }
    }

    @Test
    void hierarchyRequiresStrictlyHigherPriority() {
        IdentityRegistry registry = IdentityRegistry.INSTANCE;
        assertTrue(registry.outranks(Roles.OWNER, Roles.MANAGER));
        assertTrue(registry.outranks(Roles.MANAGER, Roles.MEMBER));
        assertFalse(registry.outranks(Roles.MANAGER, Roles.MANAGER), "同优先级互相不可操作");
        assertFalse(registry.outranks(Roles.MEMBER, Roles.MANAGER));
    }

    @Test
    void memberResolutionDistinguishesLeadershipAndUnknownIdentities() {
        DataSnapshot snapshot = guildSnapshot();
        // 领袖：无成员行也按领袖身份
        assertEquals(Roles.OWNER, IdentityPermissions.memberIdentityIdOf(snapshot, GROUP, ALICE));
        assertEquals(Roles.OWNER, IdentityPermissions.identityOf(snapshot, GROUP, ALICE).id());
        assertTrue(IdentityPermissions.isMember(snapshot, GROUP, ALICE));

        // 普通成员：照实返回身份 id
        assertEquals(Roles.MEMBER, IdentityPermissions.memberIdentityIdOf(snapshot, GROUP, BOB));

        // 非成员：不是成员，但身份回落为非成员默认身份
        assertNull(IdentityPermissions.memberIdentityIdOf(snapshot, GROUP, CAROL));
        assertFalse(IdentityPermissions.isMember(snapshot, GROUP, CAROL));
        assertEquals(Roles.VISITOR, IdentityPermissions.identityOf(snapshot, GROUP, CAROL).id());
    }

    @Test
    void unknownStoredIdentityFallsBackToMemberWithoutLosingMembership() {
        DataSnapshot snapshot = guildSnapshotWithMemberRole(BOB, "veteran");
        assertEquals("veteran", IdentityPermissions.memberIdentityIdOf(snapshot, GROUP, BOB));
        assertTrue(IdentityPermissions.isMember(snapshot, GROUP, BOB), "身份未注册不影响成员资格");
        assertEquals(Roles.MEMBER, IdentityPermissions.identityOf(snapshot, GROUP, BOB).id());
        assertFalse(IdentityPermissions.has(snapshot, GROUP, BOB, PermissionPoint.CLAIM_EXPAND));
    }

    @Test
    void groupPermissionPointsFollowConfiguredIdentity() {
        DataSnapshot snapshot = guildSnapshotWithMemberRole(BOB, Roles.MANAGER);
        assertTrue(IdentityPermissions.has(snapshot, GROUP, BOB, PermissionPoint.CLAIM_EXPAND));
        assertTrue(IdentityPermissions.has(snapshot, GROUP, BOB, PermissionPoint.CLAIM_UNCLAIM));
        assertFalse(IdentityPermissions.has(snapshot, GROUP, BOB, PermissionPoint.GROUP_DISBAND));
        assertTrue(IdentityPermissions.outranks(snapshot, GROUP, ALICE, BOB));
        assertFalse(IdentityPermissions.outranks(snapshot, GROUP, BOB, ALICE));
    }

    @Test
    void canActOnClaimCoversPersonalGroupAndServerOwners() {
        DataSnapshot snapshot = guildSnapshotWithMemberRole(BOB, Roles.MANAGER);
        ClaimData personal = ClaimData.builder("p", WORLD, BuiltinOwnerTypes.PLAYER, ALICE.toString(), "P")
            .build();
        ClaimData group = ClaimData.builder("g", WORLD, BuiltinOwnerTypes.GROUP, GROUP, "G").build();
        ClaimData server = ClaimData.builder("s", WORLD, BuiltinOwnerTypes.SERVER, "server", "S")
            .admin(true).build();

        // 个人领地：本人可，他人不可
        assertTrue(IdentityPermissions.canActOnClaim(snapshot, personal, ALICE, PermissionPoint.CLAIM_UNCLAIM));
        assertFalse(IdentityPermissions.canActOnClaim(snapshot, personal, BOB, PermissionPoint.CLAIM_UNCLAIM));

        // 组领地：领袖与管理者可放弃（本次放宽），普通成员不可
        assertTrue(IdentityPermissions.canActOnClaim(snapshot, group, ALICE, PermissionPoint.CLAIM_UNCLAIM));
        assertTrue(IdentityPermissions.canActOnClaim(snapshot, group, BOB, PermissionPoint.CLAIM_UNCLAIM));
        assertFalse(IdentityPermissions.canActOnClaim(snapshot, group, CAROL, PermissionPoint.CLAIM_UNCLAIM));

        // 管理领地：一律不可
        assertFalse(IdentityPermissions.canActOnClaim(snapshot, server, ALICE, PermissionPoint.CLAIM_UNCLAIM));

        // 第三方所有者 SPI：沿用「角色为领袖身份」的历史判定
        ClaimOwnerRegistry.INSTANCE.register(GUILD_PROVIDER);
        try {
            ClaimData thirdParty = ClaimData.builder("t", WORLD, GUILD_TYPE.key(), "org-1", "T").build();
            assertTrue(IdentityPermissions.canActOnClaim(snapshot, thirdParty, ALICE,
                PermissionPoint.CLAIM_RENAME));
            assertFalse(IdentityPermissions.canActOnClaim(snapshot, thirdParty, CAROL,
                PermissionPoint.CLAIM_RENAME));
        } finally {
            ClaimOwnerRegistry.INSTANCE.unregister(GUILD_TYPE);
        }
    }

    private static DataSnapshot guildSnapshot() {
        return guildSnapshotWithMemberRole(BOB, Roles.MEMBER);
    }

    private static DataSnapshot guildSnapshotWithMemberRole(UUID member, String roleId) {
        Map<String, GroupData> groups = new LinkedHashMap<>();
        groups.put(GROUP, new GroupData(GROUP, "Guild", ALICE, 1L, 0D));
        Map<String, Map<UUID, String>> groupMembers = new LinkedHashMap<>();
        Map<UUID, String> members = new LinkedHashMap<>();
        members.put(member, roleId);
        groupMembers.put(GROUP, members);
        return new DataSnapshot(
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            groups, groupMembers);
    }

    /** 最小第三方所有者 SPI：ALICE 为领袖身份，其余非成员。 */
    private static final OwnerType GUILD_TYPE = new OwnerType("test:guild");

    private static final ClaimOwnerProvider GUILD_PROVIDER = new ClaimOwnerProvider() {
        @Override
        public OwnerType type() {
            return GUILD_TYPE;
        }

        @Override
        public ClaimOwner getOwner(String identifier) {
            return new ClaimOwner() {
                @Override
                public OwnerType type() {
                    return GUILD_TYPE;
                }

                @Override
                public String identifier() {
                    return identifier;
                }

                @Override
                public Component displayName() {
                    return Component.text(identifier);
                }

                @Override
                public Set<UUID> members() {
                    return Set.of(ALICE);
                }

                @Override
                public String roleOf(UUID player) {
                    return ALICE.equals(player) ? IdentityRegistry.INSTANCE.leaderIdentityId() : null;
                }
            };
        }

        @Override
        public java.util.Collection<ClaimOwner> ownersOf(UUID player) {
            return List.of();
        }
    };

}
