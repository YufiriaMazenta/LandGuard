package pers.yufiria.landguard.upkeep;

import crypticlib.config.node.ConfigNode;
import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.config.UpkeepConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.data.SnapshotAudit;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.*;
import pers.yufiria.landguard.economy.EconomyProvider;
import pers.yufiria.landguard.economy.EconomyService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.owner.builtin.PlayerClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TR-10.1：合成时钟驱动 {@link UpkeepService#runCycle(long)}，验证
 * 「欠费/不活跃 → 通知 → 释放」时点与豁免名单完全符合配置。
 * 周期/宽限/阈值全部压到秒级短周期；通知顺序与状态均来自真实 sqlite + DAO 落库。
 */
public class UpkeepCycleIntegrationTest {

    // 短周期配置：收费周期 1000s、欠费宽限 500s、不活跃阈值 2000s、警告宽限 500s
    private static final long PERIOD = 1000L;
    private static final long UPKEEP_GRACE = 500L;
    private static final long INACTIVE_THRESHOLD = 2000L;
    private static final long INACTIVE_GRACE = 500L;
    private static final double COST = 1D;

    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();
    static final UUID CAROL = UUID.randomUUID();

    @TempDir
    Path tempDir;

    private ConnectionSource connection;
    private UUID world;

    @BeforeAll
    static void flags() {
        BuiltinFlags.registerAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = new JdbcConnectionSource("jdbc:sqlite:" + tempDir.resolve("upkeep.db"));
        LandDaoManager.INSTANCE.init(connection);
        DataStore.INSTANCE.reloadFrom(connection).join();
        ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
        ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
        world = UUID.randomUUID();
        EconomyService.INSTANCE.hook(new FakeEconomy());

        set(UpkeepConfigs.UPKEEP_ENABLED, true);
        set(UpkeepConfigs.INACTIVITY_ENABLED, true);
        set(UpkeepConfigs.PERIOD_SECONDS, (int) PERIOD);
        set(UpkeepConfigs.GRACE_SECONDS, (int) UPKEEP_GRACE);
        set(UpkeepConfigs.INACTIVITY_THRESHOLD_SECONDS, (int) INACTIVE_THRESHOLD);
        set(UpkeepConfigs.INACTIVITY_GRACE_SECONDS, (int) INACTIVE_GRACE);
        set(UpkeepConfigs.COST_PER_CHUNK, COST);
    }

    /** 无平台环境下 ConfigNode#setValue 依赖 configContainer，直接反射写 value 字段。 */
    private static void set(ConfigNode<?, ?> node, Object value) throws Exception {
        Field field = ConfigNode.class.getDeclaredField("value");
        field.setAccessible(true);
        field.set(node, value);
    }

    @AfterEach
    void tearDown() throws Exception {
        EconomyService.INSTANCE.unhook();
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
        ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
        set(UpkeepConfigs.UPKEEP_ENABLED, null);
        set(UpkeepConfigs.INACTIVITY_ENABLED, null);
        set(UpkeepConfigs.PERIOD_SECONDS, null);
        set(UpkeepConfigs.GRACE_SECONDS, null);
        set(UpkeepConfigs.INACTIVITY_THRESHOLD_SECONDS, null);
        set(UpkeepConfigs.INACTIVITY_GRACE_SECONDS, null);
        set(UpkeepConfigs.COST_PER_CHUNK, null);
        // 增量重载安全网：已发布快照必须与全量重读按值一致
        SnapshotAudit.assertFresh(DataStore.INSTANCE.snapshot());
        DataStore.INSTANCE.joinReload();
        DataStore.INSTANCE.publish(DataSnapshot.empty());
        connection.close();
    }

    /**
     * 合成时钟：以非零基准起算（0 是 upkeep_charged_at 的“未初始化”哨兵，不能作为首个周期时间）。
     */
    private static final long BASE_MS = 1_000_000_000L;

    private long t(long seconds) {
        return BASE_MS + seconds * 1000L;
    }

    private void player(UUID uuid, long lastLogin) throws Exception {
        LandDaoManager.INSTANCE.playerDao().create(new PlayerData(uuid, 64, 0, lastLogin));
    }

    private void quota(UUID uuid, int used) throws Exception {
        LandDaoManager.INSTANCE.playerQuotaDao().create(new PlayerQuotaData(uuid, used));
    }

    private String claim(String ownerType, String ownerId, int chunks, double bank,
                         boolean admin, boolean exempt, int baseX) throws Exception {
        String claimId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LandDaoManager.INSTANCE.claimDao().create(ClaimData.builder(
            claimId, world, ownerType, ownerId, ownerId + "-claim")
            .admin(admin).createdAt(now).lastActiveAt(now).bankBalance(bank).upkeepExempt(exempt).build());
        for (int i = 0; i < chunks; i++) {
            LandDaoManager.INSTANCE.claimChunkDao().create(new ClaimChunkData(claimId, world, baseX + i, 0));
        }
        DataStore.INSTANCE.reloadFrom(connection).join();
        return claimId;
    }

    private String personalClaim(UUID owner, int chunks, double bank, int baseX) throws Exception {
        return claim(BuiltinOwnerTypes.PLAYER, owner.toString(), chunks, bank, false, false, baseX);
    }

    private ClaimData fresh(String claimId) throws Exception {
        return LandDaoManager.INSTANCE.claimDao().queryForId(claimId);
    }

    private void fundClaimBank(String claimId, double amount) {
        DataStore.INSTANCE.mutate(current -> {
            ClaimData c = LandDaoManager.INSTANCE.claimDao().queryForId(claimId);
            c.setBankBalance(amount);
            LandDaoManager.INSTANCE.claimDao().update(c);
            return DataStore.rebuildSnapshot();
        }).join();
    }

    private void setLastLogin(UUID uuid, long lastLogin) {
        DataStore.INSTANCE.mutate(current -> {
            PlayerData data = LandDaoManager.INSTANCE.playerDao().queryForId(uuid);
            data.setLastLogin(lastLogin);
            LandDaoManager.INSTANCE.playerDao().update(data);
            return DataStore.rebuildSnapshot();
        }).join();
    }

    // ================= upkeep =================

    @Test
    void upkeepDeductedFromBankAfterInitialization() throws Exception {
        player(ALICE, t(999999));
        String id = personalClaim(ALICE, 3, 100D, 0);

        // 首次扫描只初始化时间戳，不扣费
        UpkeepCycleResult first = UpkeepService.INSTANCE.runCycle(t(0)).join();
        assertEquals(0, first.fullyCharged());
        assertEquals(0, first.notices().size());
        assertEquals(100D, fresh(id).getBankBalance(), 1e-9);

        // 越过一个周期：3 块 × 1 元
        UpkeepCycleResult second = UpkeepService.INSTANCE.runCycle(t(PERIOD)).join();
        assertEquals(1, second.fullyCharged());
        assertEquals(0, second.released());
        assertEquals(97D, fresh(id).getBankBalance(), 1e-9);
        assertEquals(0L, fresh(id).getUpkeepUnpaidSince());
    }

    @Test
    void debtWarningThenGraceReleaseAtExactBoundary() throws Exception {
        player(ALICE, t(999999));
        quota(ALICE, 2);
        String id = personalClaim(ALICE, 2, 0D, 0);

        UpkeepService.INSTANCE.runCycle(t(0)).join();
        // 欠费首次出现：一条警告
        UpkeepCycleResult indebted = UpkeepService.INSTANCE.runCycle(t(PERIOD)).join();
        assertEquals(1, indebted.notices().size());
        UpkeepNotice warning = indebted.notices().get(0);
        assertFalse(warning.release());
        assertEquals(UpkeepCause.UPKEEP_DEBT, warning.cause());
        assertEquals(t(PERIOD), fresh(id).getUpkeepUnpaidSince());

        // 宽限前 1 秒：本周期无新通知、不释放、不重复警告
        UpkeepCycleResult before = UpkeepService.INSTANCE.runCycle(t(PERIOD + UPKEEP_GRACE - 1)).join();
        assertEquals(0, before.notices().size());
        assertNotNull(fresh(id));
        assertEquals(2, DataStore.INSTANCE.snapshot().chunksByClaim().get(id).size());

        // 宽限届满：整领释放 + 释放通知（本周期仅 1 条释放通知）
        UpkeepCycleResult expired = UpkeepService.INSTANCE.runCycle(t(PERIOD + UPKEEP_GRACE)).join();
        assertEquals(1, expired.notices().size());
        assertTrue(expired.notices().get(0).release());
        assertEquals(UpkeepCause.UPKEEP_DEBT, expired.notices().get(0).cause());
        assertEquals(2, expired.notices().get(0).chunks());
        assertNull(fresh(id));
        assertFalse(DataStore.INSTANCE.snapshot().claimIdByChunk().containsKey(
            ChunkLoc.of(world, 0, 0)));
        // 系统回收不扣额度：used 已按实际持有归零
        assertEquals(0, LandDaoManager.INSTANCE.playerQuotaDao().queryForId(ALICE).getUsedChunks());
    }

    @Test
    void debtClearedAfterDepositBeforeGraceExpires() throws Exception {
        player(ALICE, t(999999));
        String id = personalClaim(ALICE, 2, 0D, 0);

        UpkeepService.INSTANCE.runCycle(t(0)).join();
        UpkeepCycleResult indebted = UpkeepService.INSTANCE.runCycle(t(PERIOD)).join();
        assertEquals(1, indebted.notices().size());

        // 宽限期内存入资金（等价 /land bank deposit 的落库结果）
        fundClaimBank(id, 10D);

        // 下一收费周期足额扣款，欠费解除
        UpkeepService.INSTANCE.runCycle(t(PERIOD * 2)).join();
        ClaimData claim = fresh(id);
        assertNotNull(claim);
        assertEquals(0L, claim.getUpkeepUnpaidSince());
        assertEquals(8D, claim.getBankBalance(), 1e-9);

        // 越过原欠费宽限时点后仍安然无恙
        UpkeepCycleResult later = UpkeepService.INSTANCE.runCycle(t(PERIOD * 3)).join();
        assertNotNull(fresh(id));
        assertEquals(0, later.released());
    }

    @Test
    void adminAndExemptClaimsNeverChargedOrReleased() throws Exception {
        player(ALICE, 0L);
        // 管理领地
        String adminId = claim(BuiltinOwnerTypes.PLAYER, ALICE.toString(), 1, 0D, true, false, 0);
        // 普通领地，随后由服务设置逐领地豁免
        String exemptId = personalClaim(ALICE, 1, 0D, 16);
        assertTrue(UpkeepService.INSTANCE.setExempt(exemptId, true).join());

        long farFuture = t(PERIOD + UPKEEP_GRACE + INACTIVE_THRESHOLD + INACTIVE_GRACE + 9999);
        UpkeepCycleResult result = UpkeepService.INSTANCE.runCycle(farFuture).join();
        assertEquals(0, result.notices().size());
        assertEquals(0, result.released());
        assertNotNull(fresh(adminId));
        assertNotNull(fresh(exemptId));
        assertEquals(0D, fresh(adminId).getBankBalance(), 1e-9);
        assertEquals(0D, fresh(exemptId).getBankBalance(), 1e-9);
        assertTrue(fresh(exemptId).isUpkeepExempt());
    }

    // ================= 不活跃 =================

    @Test
    void inactivityWarningThenReleaseAtExactBoundary() throws Exception {
        player(ALICE, t(0));
        String id = personalClaim(ALICE, 1, 100D, 0);

        // t=0：upkeep 初始化；不活跃为 0
        assertEquals(0, UpkeepService.INSTANCE.runCycle(t(0)).join().notices().size());

        // 越过阈值：警告
        UpkeepCycleResult warned = UpkeepService.INSTANCE.runCycle(t(INACTIVE_THRESHOLD)).join();
        assertEquals(1, warned.notices().size());
        assertFalse(warned.notices().get(0).release());
        assertEquals(UpkeepCause.INACTIVITY, warned.notices().get(0).cause());
        assertEquals(t(INACTIVE_THRESHOLD), fresh(id).getInactiveWarnedAt());

        // 宽限前 1 秒：本周期无新通知、仍未释放
        UpkeepCycleResult before = UpkeepService.INSTANCE
            .runCycle(t(INACTIVE_THRESHOLD + INACTIVE_GRACE - 1)).join();
        assertEquals(0, before.notices().size());
        assertNotNull(fresh(id));

        // 宽限届满释放（本周期仅 1 条释放通知）
        UpkeepCycleResult expired = UpkeepService.INSTANCE
            .runCycle(t(INACTIVE_THRESHOLD + INACTIVE_GRACE)).join();
        assertEquals(1, expired.notices().size());
        assertTrue(expired.notices().get(0).release());
        assertEquals(UpkeepCause.INACTIVITY, expired.notices().get(0).cause());
        assertNull(fresh(id));
    }

    @Test
    void inactivityWarningClearedWhenOwnerLogsIn() throws Exception {
        player(ALICE, t(0));
        String id = personalClaim(ALICE, 1, 100D, 0);

        UpkeepService.INSTANCE.runCycle(t(0)).join();
        UpkeepService.INSTANCE.runCycle(t(INACTIVE_THRESHOLD)).join();
        assertEquals(t(INACTIVE_THRESHOLD), fresh(id).getInactiveWarnedAt());

        // 警告期内登录
        setLastLogin(ALICE, t(INACTIVE_THRESHOLD + 200));
        UpkeepService.INSTANCE.runCycle(t(INACTIVE_THRESHOLD + 300)).join();
        assertEquals(0L, fresh(id).getInactiveWarnedAt());

        // 即使越过「旧警告 + 宽限」时点也不释放
        UpkeepService.INSTANCE.runCycle(t(INACTIVE_THRESHOLD + INACTIVE_GRACE)).join();
        assertNotNull(fresh(id));

        // 再次长期不活跃：重新警告，再越宽限才释放
        UpkeepService.INSTANCE.runCycle(t(INACTIVE_THRESHOLD * 2 + 200)).join();
        assertNotNull(fresh(id));
        UpkeepCycleResult released = UpkeepService.INSTANCE
            .runCycle(t(INACTIVE_THRESHOLD * 2 + 200 + INACTIVE_GRACE)).join();
        assertNull(fresh(id));
        assertEquals(1, released.released());
    }

    @Test
    void groupClaimUsesLatestMemberLoginAndGroupBank() throws Exception {
        String groupId = UUID.randomUUID().toString();
        List<UUID> members = new ArrayList<>(List.of(ALICE, BOB));
        // ALICE 早已不活跃，BOB 最近活跃；组银行 100，2 区块
        LandDaoManager.INSTANCE.groupDao().create(
            new GroupData(groupId, "Guild", ALICE, System.currentTimeMillis(), 100D));
        LandDaoManager.INSTANCE.groupMemberDao().create(new GroupMemberData(groupId, ALICE, Roles.OWNER));
        LandDaoManager.INSTANCE.groupMemberDao().create(new GroupMemberData(groupId, BOB, Roles.MEMBER));
        player(ALICE, 0L);
        player(BOB, t(5000));
        String id = claim(BuiltinOwnerTypes.GROUP, groupId, 2, 0D, false, false, 0);

        // t=5000：最近成员刚活跃，无警告；upkeep 首次只初始化
        UpkeepCycleResult fresh = UpkeepService.INSTANCE.runCycle(t(5000)).join();
        assertEquals(0, fresh.notices().size());

        // t=6000：扣组银行（2 元），仍活跃
        UpkeepService.INSTANCE.runCycle(t(6000)).join();
        assertEquals(98D, LandDaoManager.INSTANCE.groupDao().queryForId(groupId).getBankBalance(), 1e-9);
        assertEquals(0L, LandDaoManager.INSTANCE.claimDao().queryForId(id).getInactiveWarnedAt());

        // BOB 也不再活跃（两人最后登录都为 0）
        setLastLogin(BOB, 0L);
        UpkeepCycleResult warned = UpkeepService.INSTANCE.runCycle(t(6000 + INACTIVE_THRESHOLD)).join();
        assertEquals(1, warned.notices().size());
        assertEquals(UpkeepCause.INACTIVITY, warned.notices().get(0).cause());
        // 通知覆盖全体成员
        assertTrue(warned.notices().get(0).recipients().contains(ALICE));
        assertTrue(warned.notices().get(0).recipients().contains(BOB));

        UpkeepCycleResult released = UpkeepService.INSTANCE
            .runCycle(t(6000 + INACTIVE_THRESHOLD + INACTIVE_GRACE)).join();
        assertNull(LandDaoManager.INSTANCE.claimDao().queryForId(id));
        assertEquals(1, released.released());
        // 组本身保留
        assertNotNull(LandDaoManager.INSTANCE.groupDao().queryForId(groupId));
    }

    @Test
    void orphanedOwnersAreSkippedNotReleased() throws Exception {
        // 玩家行缺失的个人领地（数据异常/旧版遗留）与组行已删除的组领地：孤儿流程归 Task 11
        String playerOrphan = claim(BuiltinOwnerTypes.PLAYER, CAROL.toString(), 1, 0D, false, false, 0);
        String groupOrphan = claim(BuiltinOwnerTypes.GROUP, "ghost-group", 1, 0D, false, false, 16);

        long farFuture = t(PERIOD + UPKEEP_GRACE + INACTIVE_THRESHOLD + INACTIVE_GRACE + 9999);
        UpkeepCycleResult result = UpkeepService.INSTANCE.runCycle(farFuture).join();
        assertEquals(0, result.notices().size());
        assertEquals(0, result.released());
        assertNotNull(fresh(playerOrphan));
        assertNotNull(fresh(groupOrphan));
    }

    /** 仅作为经济可用性闸门使用的内存实现（upkeep 只动内部银行，不调余额方法）。 */
    private static final class FakeEconomy implements EconomyProvider {

        private final ConcurrentHashMap<UUID, Double> balances = new ConcurrentHashMap<>();

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
            return true;
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
            double current = balance(player);
            if (current + 1e-9 < amount) {
                return false;
            }
            balances.put(player, current - amount);
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }

}
