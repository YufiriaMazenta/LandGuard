package pers.yufiria.landguard.ui;

import crypticlib.config.node.ConfigNode;
import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import crypticlib.scheduler.SpigotScheduler;
import crypticlib.ui.display.Icon;
import crypticlib.ui.menu.Menu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.Mockito;
import pers.yufiria.landguard.LandGuard;
import pers.yufiria.landguard.config.EconomyConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.economy.EconomyProvider;
import pers.yufiria.landguard.economy.EconomyService;
import pers.yufiria.landguard.group.GroupOpResult;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TR-8.1 逻辑侧：GUI 各菜单渲染、入口联动、点击写操作全部经既有服务落库
 * （认领/放弃、flag 三态循环、银行存取、成员可见性、组领地下拉）。
 * 真实客户端点击与重启保持仍需真机冒烟补证。
 */
public class ClaimMenuMockTest {

    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();

    @TempDir
    Path tempDir;

    private WorldMock world;
    private Plugin plugin;
    private ConnectionSource connection;
    private PlayerMock alice;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        world = MockBukkit.getMock().addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin("landguard-test");
        // 让写线程回调主线程的 scheduler().sync 在 MockBukkit 下可排队执行
        Field pluginField = SpigotScheduler.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        pluginField.set(SpigotScheduler.INSTANCE, plugin);

        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("menu.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        installPluginInstance();

        MockBukkit.getMock().getPluginManager().addPermission(
            new Permission("landguard.command.claim", PermissionDefault.FALSE));
        MockBukkit.getMock().getPluginManager().addPermission(
            new Permission("landguard.command.unclaim", PermissionDefault.FALSE));

        alice = new PlayerMock(MockBukkit.getMock(), "Alice", ALICE);
        MockBukkit.getMock().addPlayer(alice);
        alice.addAttachment(plugin, "landguard.command.claim", true);
        alice.addAttachment(plugin, "landguard.command.unclaim", true);

        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.playerDao().create(new PlayerData(ALICE, 64, 0, now));
        LandDaoManager.INSTANCE.claimDao().create(new ClaimData(
            "A", world.getUID(), BuiltinOwnerTypes.PLAYER, ALICE.toString(), "Home",
            false, now, now, 0D, false));
        LandDaoManager.INSTANCE.claimChunkDao().create(
            new ClaimChunkData("A", world.getUID(), 0, 0));
        DataStore.INSTANCE.reloadFrom(connection).join();
        alice.setLocation(new Location(world, 0, 64, 0));
    }

    @AfterEach
    void tearDown() throws Exception {
        EconomyService.INSTANCE.unhook();
        // 还原为“配置尚未加载”状态，避免污染后续依赖默认值的纯平台测试
        setConfig(EconomyConfigs.ENABLED, null);
        Field pluginField = SpigotScheduler.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        pluginField.set(SpigotScheduler.INSTANCE, null);
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        DataStore.INSTANCE.joinReload();
        connection.close();
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

    private static void setConfig(ConfigNode<?, ?> node, Object value) throws Exception {
        Field field = ConfigNode.class.getDeclaredField("value");
        field.setAccessible(true);
        field.set(node, value);
    }

    /** 等待 DataStore 写线程发布新快照，顺带推动主线程任务（菜单刷新回调）。 */
    private boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                MockBukkit.getMock().getScheduler().performTicks(1);
                return true;
            }
            MockBukkit.getMock().getScheduler().performTicks(1);
            Thread.sleep(25);
        }
        return condition.getAsBoolean();
    }

    private static void click(MenuLike menu, int slot) {
        Icon icon = menu.iconAt(slot);
        assertNotNull(icon, "槽位 " + slot + " 应当存在图标");
        assertNotNull(icon.clickAction(), "槽位 " + slot + " 图标应当可点击");
        icon.clickAction().accept(null);
    }

    /** 让测试同时接受 crypticlib Menu 子类而不直接耦合类型名的小适配。 */
    private interface MenuLike {
        @Nullable Icon iconAt(int slot);
    }

    private static MenuLike wrap(Menu menu) {
        menu.getInventory();
        return slot -> menu.getIcon(slot).orElse(null);
    }

    @Test
    void listShowsOwnedClaimAndOutsiderSeesEmpty() {
        MenuLike aliceList = wrap(new ClaimListMenu(alice));
        assertEquals(Material.PAPER, aliceList.iconAt(0).display().getType(),
            "所有者列表出现领地条目");
        assertEquals(Material.GOLDEN_SHOVEL, aliceList.iconAt(49).display().getType(),
            "底部出现认领入口");

        PlayerMock bob = new PlayerMock(MockBukkit.getMock(), "Bob", BOB);
        MockBukkit.getMock().addPlayer(bob);
        ClaimListMenu bobMenu = new ClaimListMenu(bob);
        assertTrue(bobMenu.getInventory().getItem(0).getType().isAir(), "外人列表无条目");
        assertEquals(Material.BARRIER, wrap(bobMenu).iconAt(22).display().getType(),
            "空列表显示占位屏障");
    }

    @Test
    void detailAndRoleMenuRender() {
        MenuLike detail = wrap(new ClaimDetailMenu(alice, "A", 0));
        assertEquals(Material.WRITABLE_BOOK, detail.iconAt(13).display().getType());
        assertEquals(Material.COMPARATOR, detail.iconAt(30).display().getType());
        assertEquals(Material.PLAYER_HEAD, detail.iconAt(31).display().getType());
        assertNull(detail.iconAt(32), "经济不可用时不注册银行槽位");
        assertEquals(Material.BARRIER, detail.iconAt(33).display().getType());

        MenuLike roles = wrap(new FlagRoleMenu(alice, "A", 0));
        assertEquals(Material.GOLDEN_HELMET, roles.iconAt(19).display().getType());
        assertEquals(Material.IRON_HELMET, roles.iconAt(20).display().getType());
        assertEquals(Material.CHAINMAIL_HELMET, roles.iconAt(21).display().getType());
        assertEquals(Material.LEATHER_HELMET, roles.iconAt(23).display().getType());
        assertEquals(Material.CLOCK, roles.iconAt(24).display().getType());
    }

    @Test
    void flagCyclesNullAllowDenyNullAndPersists() throws Exception {
        FlagListMenu menu = new FlagListMenu(alice, "A", Roles.OWNER, false, 0);
        MenuLike flags = wrap(menu);
        assertEquals(Material.GRASS_BLOCK, flags.iconAt(0).display().getType(),
            "首个行为 flag 为 place");

        // null -> 允许
        click(flags, 0);
        assertTrue(await(() -> Boolean.TRUE.equals(ownerPlaceOverride())),
            "第一次点击写入 true 覆盖");

        // true -> 拒绝（回调刷新后取新图标）
        click(wrap(menu), 0);
        assertTrue(await(() -> Boolean.FALSE.equals(ownerPlaceOverride())),
            "第二次点击写入 false 覆盖");

        // false -> 清除
        click(wrap(menu), 0);
        assertTrue(await(() -> ownerPlaceOverride() == null),
            "第三次点击清除覆盖回到默认矩阵");
    }

    @Test
    void outsiderCannotToggleFlags() {
        PlayerMock bob = new PlayerMock(MockBukkit.getMock(), "Bob", BOB);
        MockBukkit.getMock().addPlayer(bob);
        MenuLike flags = wrap(new FlagListMenu(bob, "A", Roles.OWNER, false, 0));
        click(flags, 0);
        assertNull(ownerPlaceOverride(), "非管理者点击不产生任何写入");
        assertNotNull(bob.nextComponentMessage(), "非管理者应收到拒绝提示");
    }

    @Test
    void claimHereAndUnclaimViaIcons() throws Exception {
        alice.setLocation(new Location(world, 16, 64, 16));
        ClaimListMenu list = new ClaimListMenu(alice);
        click(wrap(list), 49);
        ChunkLoc adjacent = ChunkLoc.of(world.getUID(), 1, 0);
        assertTrue(await(() -> DataStore.INSTANCE.snapshot().claimIdByChunk().containsKey(adjacent)),
            "点击认领入口等价于 /land claim，新区块落库");

        // 回到脚下领地放弃它（A 只有 (0,0)，放弃后整块领地消失）
        alice.setLocation(new Location(world, 0, 64, 0));
        click(wrap(new ClaimDetailMenu(alice, "A", 0)), 33);
        ChunkLoc origin = ChunkLoc.of(world.getUID(), 0, 0);
        assertTrue(await(() -> !DataStore.INSTANCE.snapshot().claimIdByChunk().containsKey(origin)),
            "点击放弃入口等价于 /land unclaim，区块释放");
        assertFalse(DataStore.INSTANCE.snapshot().claimsById().containsKey("A"),
            "最后一个区块释放后领地删除");
    }

    @Test
    void membersMenuPersonalEmptyAndGroupListsLeader() throws Exception {
        MenuLike personal = wrap(new MembersMenu(alice, "A", 0));
        assertEquals(Material.BARRIER, personal.iconAt(22).display().getType(),
            "个人领地成员页为空提示");

        GroupOpResult created = GroupService.INSTANCE.createGroup(ALICE, "Guild", "Guild").join();
        assertTrue(created.success());
        GroupOpResult given = GroupService.INSTANCE.giveClaim(
            ALICE, "Guild", world.getUID(), 0, 0).join();
        assertTrue(given.success(), "把 A 转让给组织");
        assertTrue(await(() -> BuiltinOwnerTypes.GROUP.equals(
            DataStore.INSTANCE.snapshot().claimsById().get("A").getOwnerType())));

        MenuLike group = wrap(new MembersMenu(alice, "A", 0));
        assertEquals(Material.PLAYER_HEAD, group.iconAt(0).display().getType(),
            "组领地成员页列出组长");

        // 组领地下拉后仍出现在本人领地列表
        MenuLike list = wrap(new ClaimListMenu(alice));
        assertEquals(Material.PAPER, list.iconAt(0).display().getType());
    }

    @Test
    void bankMenuTransfersViaService() throws Exception {
        setConfig(EconomyConfigs.ENABLED, true);
        FakeEconomy economy = new FakeEconomy();
        economy.set(ALICE, 1000D);
        EconomyService.INSTANCE.hook(economy);

        // 经济可用且站在领地内：详情页出现银行入口
        MenuLike detail = wrap(new ClaimDetailMenu(alice, "A", 0));
        assertEquals(Material.GOLD_INGOT, detail.iconAt(32).display().getType());

        BankMenu bank = new BankMenu(alice, "A", 0);
        MenuLike bankView = wrap(bank);
        assertEquals(Material.GOLD_BLOCK, bankView.iconAt(4).display().getType());

        click(bankView, 10); // 存入 10
        assertTrue(await(() -> DataStore.INSTANCE.snapshot().claimsById().get("A")
            .getBankBalance() == 10D), "存款 10 入账领地银行");
        assertEquals(990D, economy.balance(ALICE), 1e-6);

        click(wrap(bank), 14); // 取出 10
        assertTrue(await(() -> DataStore.INSTANCE.snapshot().claimsById().get("A")
            .getBankBalance() == 0D), "取款 10 回到玩家余额");
        assertEquals(1000D, economy.balance(ALICE), 1e-6);
    }

    private Boolean ownerPlaceOverride() {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        Map<String, Map<String, Boolean>> byClaim = snapshot.roleFlagsByClaim().get("A");
        if (byClaim == null) {
            return null;
        }
        Map<String, Boolean> byRole = byClaim.get(Roles.OWNER);
        return byRole == null ? null : byRole.get("place");
    }

    static final class FakeEconomy implements EconomyProvider {

        private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

        void set(UUID player, double value) {
            balances.put(player, value);
        }

        @Override
        public String name() {
            return "Fake";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean hasAccount(UUID player) {
            return balances.containsKey(player);
        }

        @Override
        public double balance(UUID player) {
            return balances.getOrDefault(player, 0D);
        }

        @Override
        public boolean deposit(UUID player, double amount) {
            balances.merge(player, amount, Double::sum);
            return true;
        }

        @Override
        public boolean withdraw(UUID player, double amount) {
            Double current = balances.get(player);
            if (current == null || current < amount) {
                return false;
            }
            balances.put(player, current - amount);
            return true;
        }

        @Override
        public String format(double amount) {
            return String.format(Locale.ROOT, "%.2f", amount);
        }
    }
}
