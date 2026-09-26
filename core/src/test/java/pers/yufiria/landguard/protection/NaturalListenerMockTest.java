package pers.yufiria.landguard.protection;

import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.protection.listener.BlockProtectionListener;
import pers.yufiria.landguard.protection.listener.EntityProtectionListener;
import pers.yufiria.landguard.protection.listener.NaturalProtectionListener;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TR-6.1 事件级证据（MockBukkit）：真实 Bukkit 事件派发给已注册监听，
 * 断言活塞/岩浆/爆炸/火焰/踩踏四类跨界在事件取消状态与爆炸影响列表上的前后差异。
 */
public class NaturalListenerMockTest {

    private static UUID worldId;
    private static Plugin plugin;

    private WorldMock world;
    private final List<Object> registeredListeners = new ArrayList<>();

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        world = MockBukkit.getMock().addSimpleWorld("world");
        worldId = world.getUID();
        plugin = MockBukkit.createMockPlugin("landguard-test");
        PluginManager pm = MockBukkit.getMock().getPluginManager();
        register(pm, NaturalProtectionListener.INSTANCE);
        register(pm, BlockProtectionListener.INSTANCE);
        register(pm, EntityProtectionListener.INSTANCE);
        DataStore.INSTANCE.publish(snapshotWithClaims());
    }

    private void register(PluginManager pm, Listener listener) {
        pm.registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Object listener : registeredListeners) {
            HandlerList.unregisterAll((Listener) listener);
        }
        registeredListeners.clear();
        DataStore.INSTANCE.publish(DataSnapshot.empty());
        MockBukkit.unmock();
    }

    private DataSnapshot snapshotWithClaims() {
        // 区块 0 = 领地A，区块 2 = 领地B（自然默认全保护）
        Map<ChunkLoc, String> byChunk = new LinkedHashMap<>();
        byChunk.put(ChunkLoc.of(worldId, 0, 0), "A");
        byChunk.put(ChunkLoc.of(worldId, 2, 0), "B");
        return new DataSnapshot(
            new LinkedHashMap<>(), byChunk, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>()
        );
    }

    @Test
    void pistonCrossingIntoClaimIsCancelled() {
        Block piston = world.getBlockAt(40, 64, 0);
        piston.setType(Material.PISTON);
        Block inside = world.getBlockAt(41, 64, 0);
        inside.setType(Material.STONE);
        BlockPistonExtendEvent insideEvent = new BlockPistonExtendEvent(
            piston, List.of(inside), BlockFace.EAST);
        MockBukkit.getMock().getPluginManager().callEvent(insideEvent);
        assertTrue(insideEvent.isCancelled(), "领地内活塞推拉受 piston flag 保护");

        // 从领地推出到野外：放行
        Block edge = world.getBlockAt(15, 64, 0);
        edge.setType(Material.STONE);
        // 活塞位于区块边界，把方块推向野外
        Block pistonEdge = world.getBlockAt(14, 64, 0);
        pistonEdge.setType(Material.PISTON);
        BlockPistonExtendEvent outEvent = new BlockPistonExtendEvent(
            pistonEdge, List.of(edge), BlockFace.EAST);
        MockBukkit.getMock().getPluginManager().callEvent(outEvent);
        assertFalse(outEvent.isCancelled(), "推入野外不拦截");
    }

    @Test
    void lavaCannotFlowIntoClaim() {
        Block source = world.getBlockAt(31, 64, 0);
        source.setType(Material.LAVA);
        Block to = world.getBlockAt(32, 64, 0);
        to.setType(Material.AIR);
        BlockFromToEvent event = new BlockFromToEvent(source, to);
        MockBukkit.getMock().getPluginManager().callEvent(event);
        assertTrue(event.isCancelled(), "岩浆跨界进入领地必须取消，领地方块零变化");
        assertEquals(Material.AIR, to.getType());

        // 野外→野外放行
        Block wildFrom = world.getBlockAt(80, 64, 0);
        wildFrom.setType(Material.WATER);
        Block wildTo = world.getBlockAt(81, 64, 0);
        BlockFromToEvent wild = new BlockFromToEvent(wildFrom, wildTo);
        MockBukkit.getMock().getPluginManager().callEvent(wild);
        assertFalse(wild.isCancelled());
    }

    @Test
    void explosionStripsOnlyProtectedBlocks() {
        Block claimBlock = world.getBlockAt(40, 64, 0);
        claimBlock.setType(Material.STONE);
        Block wildBlock = world.getBlockAt(80, 64, 0);
        wildBlock.setType(Material.DIRT);
        List<Block> blocks = new ArrayList<>(List.of(claimBlock, wildBlock));
        EntityExplodeEvent event = new EntityExplodeEvent(
            null, claimBlock.getLocation(), blocks, 0F, ExplosionResult.DESTROY);
        MockBukkit.getMock().getPluginManager().callEvent(event);
        // 领地方块从影响列表剔除（零变化），野外方块保留
        assertEquals(List.of(wildBlock), event.blockList());
        assertEquals(Material.STONE, claimBlock.getType());
    }

    @Test
    @SuppressWarnings("removal")
    void fireSpreadAndTrampleBlocked() {
        Block source = world.getBlockAt(31, 65, 0);
        source.setType(Material.FIRE);
        Block target = world.getBlockAt(32, 65, 0);
        target.setType(Material.AIR);
        BlockSpreadEvent spread = new BlockSpreadEvent(source, source, target.getState());
        MockBukkit.getMock().getPluginManager().callEvent(spread);
        assertTrue(spread.isCancelled(), "火焰跨界蔓延取消");
        assertEquals(Material.AIR, target.getType());

        BlockBurnEvent burn = new BlockBurnEvent(world.getBlockAt(40, 64, 0));
        MockBukkit.getMock().getPluginManager().callEvent(burn);
        assertTrue(burn.isCancelled(), "领地内方块不得自然起火");

        Block farmland = world.getBlockAt(41, 64, 0);
        farmland.setType(Material.FARMLAND);
        EntityInteractEvent trample = new EntityInteractEvent(null, farmland);
        MockBukkit.getMock().getPluginManager().callEvent(trample);
        assertTrue(trample.isCancelled(), "作物踩踏取消");
    }

}
