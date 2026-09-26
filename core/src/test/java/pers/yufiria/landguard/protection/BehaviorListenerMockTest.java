package pers.yufiria.landguard.protection;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
import pers.yufiria.landguard.protection.listener.BlockProtectionListener;
import pers.yufiria.landguard.protection.listener.EntityProtectionListener;
import pers.yufiria.landguard.protection.listener.NaturalProtectionListener;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TR-6.2 向量矩阵的行为侧事件证据：所有者/非成员/野外 × 放拆/容器/工作台 的事件取消。
 */
public class BehaviorListenerMockTest {

    static final UUID ALICE = UUID.randomUUID();
    static final UUID OUTSIDER = UUID.randomUUID();

    private WorldMock world;
    private Plugin plugin;
    private PlayerMock alice;
    private PlayerMock outsider;
    private final List<Listener> registered = new ArrayList<>();

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        world = MockBukkit.getMock().addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin("landguard-test");
        PluginManager pm = MockBukkit.getMock().getPluginManager();
        register(pm, BlockProtectionListener.INSTANCE);
        register(pm, EntityProtectionListener.INSTANCE);
        register(pm, NaturalProtectionListener.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        publishSnapshot(playerClaimSnapshot());
        installPluginInstance();
        alice = new PlayerMock(MockBukkit.getMock(), "Alice", ALICE);
        outsider = new PlayerMock(MockBukkit.getMock(), "Dave", OUTSIDER);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Listener listener : registered) {
            HandlerList.unregisterAll(listener);
        }
        registered.clear();
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        publishSnapshot(DataSnapshot.empty());
        MockBukkit.unmock();
    }

    private void register(PluginManager pm, Listener listener) {
        pm.registerEvents(listener, plugin);
        registered.add(listener);
    }

    private static void publishSnapshot(DataSnapshot snapshot) throws Exception {
        Field field = DataStore.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        field.set(DataStore.INSTANCE, snapshot);
    }

    private static void installPluginInstance() throws Exception {
        // 拒绝反馈路径需要 LandGuard.instance()；mock 提供描述，语言节点未加载时回退空文本
        LandGuard pluginMock =
            Mockito.mock(LandGuard.class);
        Mockito.when(pluginMock.getDescription()).thenReturn(
            new PluginDescriptionFile("LandGuard", "1.0.0.0", "x.LandGuard"));
        Field instanceField = LandGuard.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, pluginMock);
    }

    private DataSnapshot playerClaimSnapshot() {
        long now = System.currentTimeMillis();
        ClaimData claim = new ClaimData("A", world.getUID(), BuiltinOwnerTypes.PLAYER,
            ALICE.toString(), "Home", false, now, now, 0, false);
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
            new LinkedHashMap<>(), new LinkedHashMap<>()
        );
    }

    private BlockBreakEvent breakEvent(Player player, int x, Material type) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(type);
        return new BlockBreakEvent(block, player);
    }

    @Test
    void ownerCanBreakOutsiderBlockedWildernessFree() {
        PluginManager pm = MockBukkit.getMock().getPluginManager();

        BlockBreakEvent ownerBreak = breakEvent(alice, 5, Material.STONE);
        pm.callEvent(ownerBreak);
        assertFalse(ownerBreak.isCancelled(), "所有者可破坏自己领地方块");

        BlockBreakEvent outsiderBreak = breakEvent(outsider, 6, Material.STONE);
        pm.callEvent(outsiderBreak);
        assertTrue(outsiderBreak.isCancelled(), "非成员破坏默认拒绝（break=false for visitor）");

        BlockBreakEvent cropBreak = breakEvent(outsider, 7, Material.WHEAT);
        pm.callEvent(cropBreak);
        assertTrue(cropBreak.isCancelled(), "非成员采收走 harvest 向量并拒绝");

        BlockBreakEvent wildBreak = breakEvent(outsider, 85, Material.STONE);
        pm.callEvent(wildBreak);
        assertFalse(wildBreak.isCancelled(), "野外不拦截");
    }

    @Test
    void interactVectorContainerBlockedCraftingAllowedForVisitor() {
        PluginManager pm = MockBukkit.getMock().getPluginManager();

        Block chest = world.getBlockAt(8, 64, 0);
        chest.setType(Material.CHEST);
        PlayerInteractEvent openChest = new PlayerInteractEvent(
            outsider, Action.RIGHT_CLICK_BLOCK, null, chest, BlockFace.UP, EquipmentSlot.HAND);
        pm.callEvent(openChest);
        assertTrue(openChest.useInteractedBlock() == Event.Result.DENY,
            "非成员开容器拒绝");

        Block table = world.getBlockAt(9, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        PlayerInteractEvent craft = new PlayerInteractEvent(
            outsider, Action.RIGHT_CLICK_BLOCK, null, table, BlockFace.UP, EquipmentSlot.HAND);
        pm.callEvent(craft);
        assertFalse(craft.useInteractedBlock() == Event.Result.DENY,
            "visitor 默认可用工作台（crafting=true）");

        Block wildChest = world.getBlockAt(85, 64, 0);
        wildChest.setType(Material.CHEST);
        PlayerInteractEvent wild = new PlayerInteractEvent(
            outsider, Action.RIGHT_CLICK_BLOCK, null, wildChest, BlockFace.UP, EquipmentSlot.HAND);
        pm.callEvent(wild);
        assertFalse(wild.useInteractedBlock() == Event.Result.DENY,
            "野外容器不拦截");
    }

}
