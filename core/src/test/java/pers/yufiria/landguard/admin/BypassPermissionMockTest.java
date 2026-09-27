package pers.yufiria.landguard.admin;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.Mockito;
import pers.yufiria.landguard.LandGuard;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;
import pers.yufiria.landguard.protection.CheckResult;
import pers.yufiria.landguard.protection.ProtectionPermissions;
import pers.yufiria.landguard.protection.ProtectionQueries;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TR-11.1：bypass 是独立权限节点（plugin.yml default=false）。
 * OP 管理员在没有 landguard.bypass 时不能越权；显式授予后才放行。
 */
public class BypassPermissionMockTest {

    static final UUID ALICE = UUID.randomUUID();
    static final UUID ADMIN = UUID.randomUUID();

    private WorldMock world;
    private Plugin plugin;
    private PlayerMock alice;
    private PlayerMock admin;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        world = MockBukkit.getMock().addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin("landguard-test");
        // 与生产 plugin.yml 一致：节点显式注册为 default=FALSE，OP 也不默认拥有
        PluginManager pm = MockBukkit.getMock().getPluginManager();
        pm.addPermission(new Permission(ProtectionPermissions.BYPASS, PermissionDefault.FALSE));
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        DataStore.INSTANCE.publish(playerClaimSnapshot());
        installPluginInstance();
        alice = new PlayerMock(MockBukkit.getMock(), "Alice", ALICE);
        admin = new PlayerMock(MockBukkit.getMock(), "Opal", ADMIN);
        // “管理员”身份：拥有管理命令权限但没有 bypass；不使用 setOp（MockBukkit 4.44 的 setOp 有 NPE 缺陷）
        admin.addAttachment(plugin, "landguard.command.admin", true);
    }

    @AfterEach
    void tearDown() throws Exception {
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        DataStore.INSTANCE.publish(DataSnapshot.empty());
        restorePluginInstance();
        MockBukkit.unmock();
    }

    private static void installPluginInstance() throws Exception {
        LandGuard pluginMock =
            Mockito.mock(LandGuard.class);
        Mockito.when(pluginMock.getDescription()).thenReturn(
            new PluginDescriptionFile("LandGuard", "1.0.0.0", "x.LandGuard"));
        Field instanceField = LandGuard.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, pluginMock);
    }

    /** 还原静态插件实例，避免 mock 残留污染其他测试类。 */
    private static void restorePluginInstance() throws Exception {
        Field instanceField = LandGuard.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private DataSnapshot playerClaimSnapshot() {
        long now = System.currentTimeMillis();
        ClaimData claim = ClaimData.builder("A", world.getUID(), BuiltinOwnerTypes.PLAYER,
            ALICE.toString(), "Home").createdAt(now).lastActiveAt(now).build();
        Map<String, ClaimData> byId = new LinkedHashMap<>();
        byId.put("A", claim);
        Map<ChunkLoc, String> byChunk = new LinkedHashMap<>();
        byChunk.put(ChunkLoc.of(world.getUID(), 0, 0), "A");
        Map<String, Set<ChunkLoc>> chunksByClaim = new LinkedHashMap<>();
        chunksByClaim.put("A", new LinkedHashSet<>(byChunk.keySet()));
        Map<OwnerRef, Set<String>> byOwner = new LinkedHashMap<>();
        byOwner.put(OwnerRef.of(BuiltinOwnerTypes.PLAYER, ALICE.toString()), new LinkedHashSet<>(List.of("A")));
        return new DataSnapshot(
            byId, byChunk, chunksByClaim, byOwner, new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>()
        );
    }

    @Test
    void opAdminWithoutBypassCannotOverrideProtection() {
        // 管理员是 OP 但没有独立 bypass 节点：越权破坏被拒绝
        CheckResult denied = ProtectionQueries.queryBehavior(
            admin, world.getUID(), 0, 0, BuiltinFlags.BREAK);
        assertFalse(denied.allowed(), "无 bypass 节点的 OP 管理员不能越权");

        // 所有者本人始终放行（与 bypass 无关）
        CheckResult ownerAllowed = ProtectionQueries.queryBehavior(
            alice, world.getUID(), 0, 0, BuiltinFlags.BREAK);
        assertTrue(ownerAllowed.allowed(), "所有者可操作自己领地");
    }

    @Test
    void grantedBypassOverridesProtection() {
        var attachment = admin.addAttachment(plugin, ProtectionPermissions.BYPASS, true);
        try {
            CheckResult allowed = ProtectionQueries.queryBehavior(
                admin, world.getUID(), 0, 0, BuiltinFlags.BREAK);
            assertTrue(allowed.allowed(), "显式授予 bypass 后管理员可越权");
        } finally {
            attachment.remove();
        }

        // 移除后恢复拒绝
        CheckResult deniedAgain = ProtectionQueries.queryBehavior(
            admin, world.getUID(), 0, 0, BuiltinFlags.BREAK);
        assertFalse(deniedAgain.allowed(), "移除 bypass 后越权再次被拒绝");
    }

}
