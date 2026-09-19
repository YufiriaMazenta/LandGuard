package pers.yufiria.landguard.protection;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerProvider;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TR-5.1 / AC-6：
 * ① 相同 flag、不同角色结果互不影响；② 同角色在两领地的覆盖互不串扰；
 * ③ 角色身份只由所有者实体给出，不从 flag 反推；④ 覆盖经 FlagService 落库并在重建快照后保持。
 */
public class ProtectionChainTest {

    static final OwnerType GUILD_TYPE = new OwnerType("test:guild-chain");
    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();
    static final UUID CAROL = UUID.randomUUID();
    static final UUID DAVE = UUID.randomUUID();
    static final UUID OUTSIDER = UUID.randomUUID();
    static final UUID WORLD = UUID.randomUUID();

    @TempDir
    Path tempDir;
    private ConnectionSource connection;

    @BeforeAll
    static void registerFlags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() {
        ClaimOwnerRegistry.INSTANCE.register(new GuildProvider(new Guild("g", Map.of(
            ALICE, Roles.MANAGER,
            BOB, Roles.MEMBER,
            CAROL, "R1",
            DAVE, "R2"
        ))));
        ClaimOwnerRegistry.INSTANCE.register(pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider.INSTANCE);
    }

    @AfterEach
    void tearDown() throws Exception {
        ClaimOwnerRegistry.INSTANCE.unregister(GUILD_TYPE);
        ClaimOwnerRegistry.INSTANCE.unregister(
            new pers.yufiria.landguard.owner.OwnerType(pers.yufiria.landguard.owner.BuiltinOwnerTypes.PLAYER));
        if (connection != null) {
            DataStore.INSTANCE.joinReload();
            connection.close();
        }
    }

    private DataSnapshot guildSnapshot(Map<String, Map<String, Map<String, Boolean>>> flagsByClaim) {
        long now = System.currentTimeMillis();
        ClaimData c1 = new ClaimData("c1", WORLD, GUILD_TYPE.key(), "g", "C1", false, now, now, 0, false);
        ClaimData c2 = new ClaimData("c2", WORLD, GUILD_TYPE.key(), "g", "C2", false, now, now, 0, false);
        Map<UUID, String> notUsed = Map.of();
        return snapshotWithClaims(List.of(c1, c2), flagsByClaim);
    }

    static DataSnapshot snapshotWithClaims(List<ClaimData> claims,
                                          Map<String, Map<String, Map<String, Boolean>>> flagsByClaim) {
        Map<String, ClaimData> byId = new LinkedHashMap<>();
        Map<ChunkLoc, String> byChunk = new LinkedHashMap<>();
        Map<String, Set<ChunkLoc>> chunksByClaim = new LinkedHashMap<>();
        Map<OwnerRef, Set<String>> byOwner = new LinkedHashMap<>();
        int baseX = 0;
        for (ClaimData claim : claims) {
            byId.put(claim.getClaimId(), claim);
            ChunkLoc loc = ChunkLoc.of(claim.getWorldUuid(), baseX++, 0);
            byChunk.put(loc, claim.getClaimId());
            chunksByClaim.put(claim.getClaimId(), Set.of(loc));
            byOwner.computeIfAbsent(OwnerRef.of(claim.getOwnerType(), claim.getOwnerId()), k -> new java.util.LinkedHashSet<>())
                .add(claim.getClaimId());
        }
        return new DataSnapshot(
            byId, byChunk, chunksByClaim, byOwner, flagsByClaim,
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>()
        );
    }

    @Test
    void sameFlagDifferentRolesAreIndependent() {
        DataSnapshot snapshot = guildSnapshot(new LinkedHashMap<>());

        // manager 默认可开容器；member 默认不可；visitor 不可
        var alice = ProtectionChecker.checkBehavior(snapshot, ALICE, WORLD, 0, 0, BuiltinFlags.CONTAINER);
        var bob = ProtectionChecker.checkBehavior(snapshot, BOB, WORLD, 0, 0, BuiltinFlags.CONTAINER);
        var carol = ProtectionChecker.checkBehavior(snapshot, CAROL, WORLD, 0, 0, BuiltinFlags.CONTAINER);
        assertTrue(alice.allowed());
        assertFalse(bob.allowed());
        assertFalse(carol.allowed());
        assertEquals(Roles.MANAGER, alice.context().memberRole());
        assertEquals(Roles.MEMBER, bob.context().memberRole());
        // CAROL 是组织成员（自定义角色 R1），身份不被降格为 visitor；仅非成员才解析为 visitor
        assertEquals("R1", carol.context().memberRole());

        // 真正的非成员（visitor）默认可用工作台
        var outsiderCraft = ProtectionChecker.checkBehavior(snapshot, OUTSIDER, WORLD, 0, 0, BuiltinFlags.CRAFTING);
        assertTrue(outsiderCraft.allowed());
        assertEquals(Roles.VISITOR, outsiderCraft.context().effectiveBehaviorRole());
        // visitor 默认不能开门/不能开容器
        assertFalse(ProtectionChecker.checkBehavior(snapshot, OUTSIDER, WORLD, 0, 0, BuiltinFlags.DOOR).allowed());
        // member 默认可以放置
        assertTrue(ProtectionChecker.checkBehavior(snapshot, BOB, WORLD, 0, 0, BuiltinFlags.PLACE).allowed());
    }

    @Test
    void overrideIsPerClaimAndPerRoleAndNeverRebindsIdentity() {
        // c1：R1 开门允许；c2：无任何覆盖。R1/R2 是不同身份但默认 flag 组合相同
        Map<String, Map<String, Map<String, Boolean>>> flags = new LinkedHashMap<>();
        flags.put("c1", Map.of("R1", Map.of(BuiltinFlags.DOOR.id(), true)));
        DataSnapshot snapshot = guildSnapshot(flags);

        var r1InC1 = ProtectionChecker.checkBehavior(snapshot, CAROL, WORLD, 0, 0, BuiltinFlags.DOOR);
        var r1InC2 = ProtectionChecker.checkBehavior(snapshot, CAROL, WORLD, 1, 0, BuiltinFlags.DOOR);
        var r2InC1 = ProtectionChecker.checkBehavior(snapshot, DAVE, WORLD, 0, 0, BuiltinFlags.DOOR);
        var r2InC2 = ProtectionChecker.checkBehavior(snapshot, DAVE, WORLD, 1, 0, BuiltinFlags.DOOR);
        assertTrue(r1InC1.allowed(), "c1 的 R1 覆盖生效");
        assertFalse(r1InC2.allowed(), "同角色在 c2 不受 c1 覆盖影响");
        assertFalse(r2InC1.allowed(), "同领地另一角色 R2 不受 R1 覆盖影响");
        assertFalse(r2InC2.allowed());

        // 角色身份由所有者解析给出，flag 同值也不改变身份
        assertEquals("R1", r1InC1.context().memberRole());
        assertEquals("R2", r2InC1.context().memberRole());

        // 把 c1 的 R1 覆盖改为 false，R2 仍然不受影响
        Map<String, Map<String, Map<String, Boolean>>> changed = new LinkedHashMap<>();
        changed.put("c1", Map.of("R1", Map.of(BuiltinFlags.DOOR.id(), false)));
        DataSnapshot afterToggle = guildSnapshot(changed);
        assertFalse(ProtectionChecker.checkBehavior(afterToggle, CAROL, WORLD, 0, 0, BuiltinFlags.DOOR).allowed());
        assertFalse(ProtectionChecker.checkBehavior(afterToggle, DAVE, WORLD, 0, 0, BuiltinFlags.DOOR).allowed());
    }

    @Test
    void wildernessAllowedOrphanedDenied() {
        DataSnapshot snapshot = guildSnapshot(new LinkedHashMap<>());
        // 野外坐标（无区块索引）放行
        assertTrue(ProtectionChecker.checkBehavior(snapshot, CAROL, WORLD, 99, 99, BuiltinFlags.BREAK).allowed());
        assertTrue(ProtectionChecker.checkNatural(snapshot, WORLD, 99, 99, BuiltinFlags.PVP).allowed());

        // 提供者注销 → 孤儿领地：行为拒绝，自然类仍可读环境默认
        ClaimOwnerRegistry.INSTANCE.unregister(GUILD_TYPE);
        var orphan = ProtectionChecker.checkBehavior(snapshot, ALICE, WORLD, 0, 0, BuiltinFlags.BREAK);
        assertFalse(orphan.allowed());
        assertTrue(orphan.context().orphaned());
        // 自然默认：pvp 关、怪物生成开
        assertFalse(ProtectionChecker.checkNatural(snapshot, WORLD, 0, 0, BuiltinFlags.PVP).allowed());
        assertTrue(ProtectionChecker.checkNatural(snapshot, WORLD, 0, 0, BuiltinFlags.MOB_SPAWN).allowed());
    }

    @Test
    void naturalOverridesAreEnvironmentScoped() {
        Map<String, Map<String, Map<String, Boolean>>> flags = new LinkedHashMap<>();
        flags.put("c1", Map.of(ProtectionChecker.ENVIRONMENT_ROLE, Map.of(BuiltinFlags.PVP.id(), true)));
        DataSnapshot snapshot = guildSnapshot(flags);

        assertTrue(ProtectionChecker.checkNatural(snapshot, WORLD, 0, 0, BuiltinFlags.PVP).allowed());
        // c2 未覆盖：默认关闭
        assertFalse(ProtectionChecker.checkNatural(snapshot, WORLD, 1, 0, BuiltinFlags.PVP).allowed());
    }

    @Test
    void overridesPersistAndRebuildWithoutCrossTalk() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("chain.db"));
        LandDaoManager.INSTANCE.init(connection);
        long now = System.currentTimeMillis();
        // 两领地归测试组织所有；BOB 在其中是 member（容器默认拒绝），用于验证覆盖只作用于 c1
        LandDaoManager.INSTANCE.claimDao().create(
            new ClaimData("c1", WORLD, GUILD_TYPE.key(), "g", "C1", false, now, now, 0, false));
        LandDaoManager.INSTANCE.claimDao().create(
            new ClaimData("c2", WORLD, GUILD_TYPE.key(), "g", "C2", false, now, now, 0, false));
        LandDaoManager.INSTANCE.claimChunkDao().create(
            new pers.yufiria.landguard.database.entity.ClaimChunkData("c1", WORLD, 0, 0));
        LandDaoManager.INSTANCE.claimChunkDao().create(
            new pers.yufiria.landguard.database.entity.ClaimChunkData("c2", WORLD, 1, 0));
        DataStore.INSTANCE.reloadFrom(connection).join();

        // c1 的 member 容器覆盖为允许（member 默认矩阵中容器为拒绝）
        assertTrue(FlagService.INSTANCE.setBehaviorOverride("c1", Roles.MEMBER, BuiltinFlags.CONTAINER, true).join());
        DataSnapshot snap = DataStore.INSTANCE.snapshot();
        assertTrue(ProtectionChecker.checkBehavior(snap, BOB, WORLD, 0, 0, BuiltinFlags.CONTAINER).allowed());
        assertFalse(ProtectionChecker.checkBehavior(snap, BOB, WORLD, 1, 0, BuiltinFlags.CONTAINER).allowed());
        // 自定义角色不受 member 覆盖影响
        assertFalse(snap.roleFlagsByClaim().get("c1").containsKey("R1"));

        // 自然类覆盖独立于行为类
        assertTrue(FlagService.INSTANCE.setNaturalOverride("c2", BuiltinFlags.PVP, true).join());
        snap = DataStore.INSTANCE.snapshot();
        assertTrue(ProtectionChecker.checkNatural(snap, WORLD, 1, 0, BuiltinFlags.PVP).allowed());
        assertFalse(ProtectionChecker.checkNatural(snap, WORLD, 0, 0, BuiltinFlags.PVP).allowed());

        // 落库行数正确：c1/member/container + c2/#natural/pvp
        assertEquals(2, LandDaoManager.INSTANCE.roleFlagDao().queryForAll().size());

        // 重置后回到默认（member 容器默认拒绝）
        assertTrue(FlagService.INSTANCE.resetBehaviorOverride("c1", Roles.MEMBER, BuiltinFlags.CONTAINER).join());
        snap = DataStore.INSTANCE.snapshot();
        assertFalse(ProtectionChecker.checkBehavior(snap, BOB, WORLD, 0, 0, BuiltinFlags.CONTAINER).allowed());
        assertEquals(1, LandDaoManager.INSTANCE.roleFlagDao().queryForAll().size());

        // 不存在的领地拒绝写入
        assertFalse(FlagService.INSTANCE.setBehaviorOverride("nope", Roles.MEMBER, BuiltinFlags.CONTAINER, true).join());
    }

    // ---------------- 测试用所有者实体 ----------------

    record GuildProvider(Guild guild) implements ClaimOwnerProvider {

        @Override
        public @NotNull OwnerType type() {
            return GUILD_TYPE;
        }

        @Override
        public @Nullable ClaimOwner getOwner(@NotNull String identifier) {
            return "g".equals(identifier) ? guild : null;
        }

        @Override
        public @NotNull Collection<ClaimOwner> ownersOf(@NotNull UUID player) {
            return guild.roleOf(player) != null ? List.of(guild) : List.of();
        }
    }

    record Guild(String id, Map<UUID, String> roles) implements ClaimOwner {

        @Override
        public @NotNull OwnerType type() {
            return GUILD_TYPE;
        }

        @Override
        public @NotNull String identifier() {
            return id;
        }

        @Override
        public @NotNull Component displayName() {
            return Component.text("Guild");
        }

        @Override
        public @NotNull Set<UUID> members() {
            return Set.copyOf(roles.keySet());
        }

        @Override
        public @Nullable String roleOf(UUID player) {
            return roles.get(player);
        }
    }

}
