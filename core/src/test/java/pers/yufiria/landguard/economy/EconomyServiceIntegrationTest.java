package pers.yufiria.landguard.economy;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.group.GroupOpResult;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TR-9.1 逻辑侧：额度买卖、个人/组银行存取、未授权取款拒绝、经济不可用降级。
 * Vault 真实适配由 hook 模块测试覆盖；此处用内存假提供方验证服务语义。
 */
public class EconomyServiceIntegrationTest {

    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();

    @TempDir
    Path tempDir;

    private ConnectionSource connection;
    private UUID world;
    private FakeEconomy economy;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("economy.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        world = UUID.randomUUID();
        economy = new FakeEconomy();
        economy.set(ALICE, 1000D);
        economy.set(BOB, 1000D);
        EconomyService.INSTANCE.hook(economy);
    }

    @AfterEach
    void tearDown() throws Exception {
        EconomyService.INSTANCE.unhook();
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        DataStore.INSTANCE.joinReload();
        connection.close();
    }

    private void createPersonalClaim(UUID owner, int cx, int cz) throws Exception {
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(new ClaimData(
            claimId, world, BuiltinOwnerTypes.PLAYER, owner.toString(), "Home", false, now, now, 0D, false));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, cx, cz));
        DataStore.INSTANCE.reloadFrom(connection).join();
    }

    @Test
    void buyAndSellQuota() {
        // 购买 5 块：100/块，余额 1000 -> 500
        EconomyOpResult bought = EconomyService.INSTANCE.buyChunks(ALICE, 5).join();
        assertTrue(bought.success());
        assertEquals(500D, bought.accountBalance(), 1e-9);
        assertEquals(5, DataStore.INSTANCE.snapshot().players().get(ALICE).getBoughtChunks());

        // 余额不足拒绝
        assertFalse(EconomyService.INSTANCE.buyChunks(ALICE, 6).join().success());
        assertEquals(EconomyFailureReason.INSUFFICIENT_FUNDS,
            EconomyService.INSTANCE.buyChunks(ALICE, 6).join().failureReason());

        // 出售 2 块：80/块，余额 500 -> 660
        EconomyOpResult sold = EconomyService.INSTANCE.sellChunks(ALICE, 2).join();
        assertTrue(sold.success());
        assertEquals(660D, sold.accountBalance(), 1e-9);
        assertEquals(3, DataStore.INSTANCE.snapshot().players().get(ALICE).getBoughtChunks());

        // 卖空拒绝
        assertEquals(EconomyFailureReason.NOTHING_TO_SELL,
            EconomyService.INSTANCE.sellChunks(ALICE, 4).join().failureReason());
    }

    @Test
    void cannotSellQuotaInUse() throws Exception {
        // 初始额度 64 + 购买 10；认领 70 块占用 70；可卖额度只剩 64+10-70=4
        EconomyService.INSTANCE.buyChunks(ALICE, 10).join();
        List<ChunkLoc> seventy = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            seventy.add(ChunkLoc.of(world, 100 + i, 100));
        }
        var claimResult = ClaimService.INSTANCE.claim(
            OwnerRef.of(BuiltinOwnerTypes.PLAYER, ALICE.toString()),
            world, seventy, "Big", false).join();
        assertTrue(claimResult.success(), claimResult.failureReason() == null ? "" : claimResult.failureReason().name());

        assertEquals(EconomyFailureReason.QUOTA_IN_USE,
            EconomyService.INSTANCE.sellChunks(ALICE, 5).join().failureReason());
        EconomyOpResult sell4 = EconomyService.INSTANCE.sellChunks(ALICE, 4).join();
        assertTrue(sell4.success());
    }

    @Test
    void personalClaimBankDepositAndAuthorizedWithdraw() throws Exception {
        createPersonalClaim(ALICE, 0, 0);
        // 所有者存入 300
        EconomyOpResult deposited = EconomyService.INSTANCE.deposit(ALICE, world, 0, 0, 300D).join();
        assertTrue(deposited.success());
        assertEquals(700D, deposited.accountBalance(), 1e-9);
        assertEquals(300D, deposited.bankBalance(), 1e-9);

        // owner 默认有 BANK flag：取出 100
        EconomyOpResult withdrew = EconomyService.INSTANCE.withdraw(ALICE, world, 0, 0, 100D).join();
        assertTrue(withdrew.success());
        assertEquals(800D, withdrew.accountBalance(), 1e-9);
        assertEquals(200D, withdrew.bankBalance(), 1e-9);

        // 超额取款拒绝且双方余额不变
        EconomyOpResult tooMuch = EconomyService.INSTANCE.withdraw(ALICE, world, 0, 0, 201D).join();
        assertFalse(tooMuch.success());
        assertEquals(EconomyFailureReason.BANK_EMPTY, tooMuch.failureReason());
        assertEquals(800D, economy.balance(ALICE), 1e-9);
        assertEquals(200D,
            EconomyService.INSTANCE.bankBalanceAt(DataStore.INSTANCE.snapshot(), world, 0, 0), 1e-9);
    }

    @Test
    void unauthorizedWithdrawRejectedButDepositAllowed() throws Exception {
        createPersonalClaim(ALICE, 1, 0);
        // 非成员（visitor）存款：允许（钱流入银行）
        EconomyOpResult deposited = EconomyService.INSTANCE.deposit(BOB, world, 1, 0, 50D).join();
        assertTrue(deposited.success());
        assertEquals(50D, deposited.bankBalance(), 1e-9);

        // 非成员取款：BANK flag 拒绝，银行与个人账户都不变
        EconomyOpResult withdrew = EconomyService.INSTANCE.withdraw(BOB, world, 1, 0, 50D).join();
        assertFalse(withdrew.success());
        assertEquals(EconomyFailureReason.BANK_FORBIDDEN, withdrew.failureReason());
        // BOB 此前已存入 50（1000→950），取款被拒后余额保持 950
        assertEquals(950D, economy.balance(BOB), 1e-9);
        assertEquals(50D,
            EconomyService.INSTANCE.bankBalanceAt(DataStore.INSTANCE.snapshot(), world, 1, 0), 1e-9);
    }

    @Test
    void groupClaimUsesGroupBank() throws Exception {
        GroupOpResult created = GroupService.INSTANCE.createGroup(ALICE, "Guild", "Guild").join();
        assertTrue(created.success());
        String groupId = created.groupId();
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(new ClaimData(
            claimId, world, BuiltinOwnerTypes.GROUP, groupId, "GH", false, now, now, 0D, false));
        LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, 2, 0));
        DataStore.INSTANCE.reloadFrom(connection).join();

        EconomyOpResult deposited = EconomyService.INSTANCE.deposit(ALICE, world, 2, 0, 400D).join();
        assertTrue(deposited.success());
        assertEquals(400D, deposited.bankBalance(), 1e-9);
        assertEquals(400D, DataStore.INSTANCE.snapshot().groups().get(groupId).getBankBalance(), 1e-9);
        // 领地自身银行保持 0：钱进了组银行
        ClaimData claim = DataStore.INSTANCE.snapshot().claimsById().get(claimId);
        assertEquals(0D, claim.getBankBalance(), 1e-9);

        EconomyOpResult withdrew = EconomyService.INSTANCE.withdraw(ALICE, world, 2, 0, 250D).join();
        assertTrue(withdrew.success());
        assertEquals(150D, withdrew.bankBalance(), 1e-9);
    }

    @Test
    void unavailableEconomyDisablesEntries() {
        EconomyService.INSTANCE.unhook();
        assertEquals(EconomyFailureReason.UNAVAILABLE,
            EconomyService.INSTANCE.buyChunks(ALICE, 1).join().failureReason());
        assertEquals(EconomyFailureReason.UNAVAILABLE,
            EconomyService.INSTANCE.deposit(ALICE, world, 0, 0, 10D).join().failureReason());
        assertFalse(EconomyService.INSTANCE.available());
    }

    /** 内存假经济：余额操作即时生效、余额不足拒扣。 */
    static final class FakeEconomy implements EconomyProvider {

        private final ConcurrentHashMap<UUID, Double> balances = new ConcurrentHashMap<>();

        void set(UUID player, double balance) {
            balances.put(player, balance);
        }

        @Override
        public String name() {
            return "fake";
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
        public synchronized boolean deposit(UUID player, double amount) {
            balances.merge(player, amount, Double::sum);
            return true;
        }

        @Override
        public synchronized boolean withdraw(UUID player, double amount) {
            double current = balance(player);
            if (current + 1e-9 < amount) {
                return false;
            }
            balances.put(player, current - amount);
            return true;
        }

        @Override
        public String format(double amount) {
            return String.format("%.2f", amount);
        }
    }

}
