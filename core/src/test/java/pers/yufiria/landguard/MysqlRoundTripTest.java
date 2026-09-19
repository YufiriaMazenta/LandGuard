package pers.yufiria.landguard;

import crypticlib.config.node.ConfigNode;
import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.dao.DaoManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import pers.yufiria.landguard.config.DatabaseConfigs;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimChunkData;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.ClaimRoleFlagData;
import pers.yufiria.landguard.database.entity.ClaimSettingData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.GroupMemberData;
import pers.yufiria.landguard.database.entity.GroupRoleData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.database.entity.PlayerQuotaData;
import pers.yufiria.landguard.database.loader.MysqlDataSourceLoader;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TR-2.2：真实 MySQL 后端的建表 / 认领落库 / 重启恢复 / 加法迁移往返验证。
 * 走生产同款 {@link MysqlDataSourceLoader}（PooledConnectionSource）路径。
 * 连接参数可用环境变量覆盖：LG_MYSQL_HOST / LG_MYSQL_PORT / LG_MYSQL_USER / LG_MYSQL_PASSWORD。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MysqlRoundTripTest {

    private static final String HOST = System.getenv().getOrDefault("LG_MYSQL_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("LG_MYSQL_PORT", "3306"));
    private static final String USER = System.getenv().getOrDefault("LG_MYSQL_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("LG_MYSQL_PASSWORD", "123456");
    private static final String TEST_DATABASE = "landguard_test";
    private static final String PARAMETERS =
        "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";

    private static final List<String> EXPECTED_TABLES = List.of(
        "lg_claim", "lg_claim_chunk", "lg_role_flag", "lg_claim_setting",
        "lg_player_data", "lg_player_quota",
        "lg_group", "lg_group_member", "lg_group_role"
    );

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();
    private static final String CLAIM_ID = UUID.randomUUID().toString();
    private static final String GROUP_ID = UUID.randomUUID().toString();

    @BeforeAll
    static void prepareSchema() throws Exception {
        assumeTrue(serverReachable(), "MySQL 不可达，跳过真实后端测试");
        try (Connection connection = openServerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + TEST_DATABASE
                + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
        // 清空遗留的 lg_ 表，确保本次 DDL 建表流程被真实执行
        try (Connection connection = openDatabaseConnection(); Statement statement = connection.createStatement()) {
            for (String table : EXPECTED_TABLES) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            }
            System.out.println("[MYSQL] server=" + connection.getMetaData().getDatabaseProductVersion()
                + ", database=" + TEST_DATABASE);
        }
    }

    @AfterAll
    static void restoreConfig() throws Exception {
        set(DatabaseConfigs.MYSQL, null);
        System.out.println("[MYSQL] 测试库 " + TEST_DATABASE + " 保留供人工核对（如需清理请手动 DROP DATABASE）");
    }

    @Test
    @Order(1)
    void schemaAndClaimRowsSurviveRestart() throws Exception {
        ConnectionSource first = productionConnectionSource();
        LandDaoManager.INSTANCE.init(first);

        try (Connection raw = openDatabaseConnection()) {
            Set<String> tables = readTableNames(raw);
            for (String expected : EXPECTED_TABLES) {
                assertTrue(tables.contains(expected), "缺少表 " + expected + "，实际: " + tables);
            }
            System.out.println("[MYSQL] 9 张表建表成功: " + tables);
        }

        LandDaoManager daos = LandDaoManager.INSTANCE;
        daos.claimDao().create(new ClaimData(CLAIM_ID, WORLD, "player", PLAYER.toString(),
            "home", false, 1000L, 2000L, 12.5, false));
        daos.claimChunkDao().create(new ClaimChunkData(CLAIM_ID, WORLD, 3, -7));
        daos.claimChunkDao().create(new ClaimChunkData(CLAIM_ID, WORLD, 3, -6));
        daos.roleFlagDao().create(new ClaimRoleFlagData(CLAIM_ID, "member", "CONTAINER", false));
        daos.claimSettingDao().create(new ClaimSettingData(CLAIM_ID, "greeting", "hello"));
        daos.playerDao().create(new PlayerData(PLAYER, 100, 20, 9999L));
        daos.playerQuotaDao().create(new PlayerQuotaData(PLAYER, 4));
        daos.groupDao().create(new GroupData(GROUP_ID, "builders", PLAYER, 1L, 50.0));
        daos.groupMemberDao().create(new GroupMemberData(GROUP_ID, PLAYER, "owner"));
        daos.groupRoleDao().create(new GroupRoleData(GROUP_ID, "owner", 100, "Owner"));
        first.close();

        // 模拟服务器重启：关闭连接池 + 清空 Dao 元数据缓存 + 全新连接池
        DaoManager.clearCache();
        ConnectionSource second = productionConnectionSource();
        LandDaoManager.INSTANCE.init(second);
        LandDaoManager restarted = LandDaoManager.INSTANCE;

        assertEquals(1, restarted.claimDao().queryForAll().size());
        ClaimData claim = restarted.claimDao().queryForId(CLAIM_ID);
        assertNotNull(claim);
        assertEquals(WORLD, claim.getWorldUuid());
        assertEquals("player", claim.getOwnerType());
        assertEquals(PLAYER.toString(), claim.getOwnerId());
        assertEquals(12.5, claim.getBankBalance(), 0.0001);
        assertFalse(claim.isAdmin());
        assertFalse(claim.isUpkeepExempt());

        List<ClaimChunkData> chunks = restarted.claimChunkDao().queryForAll();
        assertEquals(2, chunks.size());
        assertTrue(chunks.stream().anyMatch(c -> c.getChunkX() == 3 && c.getChunkZ() == -7));
        assertTrue(chunks.stream().anyMatch(c -> c.getChunkX() == 3 && c.getChunkZ() == -6));

        ClaimRoleFlagData flag = restarted.roleFlagDao().queryForAll().get(0);
        assertEquals("member", flag.getRoleId());
        assertEquals("CONTAINER", flag.getFlagKey());
        assertFalse(flag.isValue());
        assertEquals("hello", restarted.claimSettingDao().queryForAll().get(0).getSettingValue());

        PlayerData restoredPlayer = restarted.playerDao().queryForId(PLAYER);
        assertEquals(100, restoredPlayer.getAccruedChunks());
        assertEquals(20, restoredPlayer.getBoughtChunks());
        assertEquals(4, restarted.playerQuotaDao().queryForId(PLAYER).getUsedChunks());

        assertEquals("builders", restarted.groupDao().queryForId(GROUP_ID).getName());
        assertEquals("owner", restarted.groupMemberDao().queryForAll().get(0).getRoleId());
        assertEquals(100, restarted.groupRoleDao().queryForAll().get(0).getPriority());
        second.close();

        System.out.println("[MYSQL] 重启恢复往返通过：claim=1, chunk=2, player/quota/group/member/role 全部一致");
    }

    @Test
    @Order(2)
    void additiveMigrationRestoresDroppedColumns() throws Exception {
        // 构造“旧库”形态：删除后期新增的生命周期列
        try (Connection raw = openDatabaseConnection(); Statement statement = raw.createStatement()) {
            statement.execute("ALTER TABLE lg_claim DROP COLUMN upkeep_charged_at");
            statement.execute("ALTER TABLE lg_claim DROP COLUMN orphan_since");
            Set<String> columns = readColumnNames(raw, "lg_claim");
            assertFalse(columns.contains("upkeep_charged_at"));
            assertFalse(columns.contains("orphan_since"));
        }

        ConnectionSource source = productionConnectionSource();
        LandDaoManager.INSTANCE.init(source);

        try (Connection raw = openDatabaseConnection()) {
            Set<String> columns = readColumnNames(raw, "lg_claim");
            assertTrue(columns.contains("upkeep_charged_at"), "迁移未补列，实际: " + columns);
            assertTrue(columns.contains("orphan_since"), "迁移未补列，实际: " + columns);
            System.out.println("[MYSQL] 加法迁移补列成功: " + columns);
        }

        // 补列默认值为 0，旧行读取不得为空
        ClaimData legacy = LandDaoManager.INSTANCE.claimDao().queryForId(CLAIM_ID);
        assertNotNull(legacy);
        assertEquals(0L, legacy.getUpkeepChargedAt());
        assertEquals(0L, legacy.getOrphanSince());
        source.close();
        System.out.println("[MYSQL] 旧行补列后默认值读取正常（upkeepChargedAt=0, orphanSince=0）");
    }

    /** 走生产同款加载器：由 database.yml 同构配置构造连接池。 */
    private static ConnectionSource productionConnectionSource() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("host", HOST);
        yaml.set("port", PORT);
        yaml.set("database", TEST_DATABASE);
        yaml.set("username", USER);
        yaml.set("password", PASSWORD);
        yaml.set("parameters", PARAMETERS);
        yaml.set("pool.max_connections", 4);
        yaml.set("pool.max_idle_time_ms", 60000);
        yaml.set("pool.max_lifetime_ms", 1800000);
        yaml.set("pool.check_connections_every_ms", 5000);
        yaml.set("pool.test_before_get", true);
        set(DatabaseConfigs.MYSQL, yaml);
        return MysqlDataSourceLoader.INSTANCE.load();
    }

    private static boolean serverReachable() {
        try (Connection ignored = openServerConnection()) {
            return true;
        } catch (SQLException e) {
            System.out.println("[MYSQL] 连接失败，跳过: " + e.getMessage());
            return false;
        }
    }

    private static Connection openServerConnection() throws SQLException {
        return DriverManager.getConnection(
            "jdbc:mysql://" + HOST + ":" + PORT + "/?" + PARAMETERS, USER, PASSWORD);
    }

    private static Connection openDatabaseConnection() throws SQLException {
        return DriverManager.getConnection(
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + TEST_DATABASE + "?" + PARAMETERS, USER, PASSWORD);
    }

    private static Set<String> readTableNames(Connection connection) throws SQLException {
        return readSingleColumn(connection,
            "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()");
    }

    private static Set<String> readColumnNames(Connection connection, String table) throws SQLException {
        return readSingleColumn(connection,
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = '" + table + "'");
    }

    private static Set<String> readSingleColumn(Connection connection, String sql) throws SQLException {
        Set<String> values = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                values.add(result.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    /** 无平台环境下 ConfigNode#setValue 依赖 configContainer，直接反射写 value 字段。 */
    private static void set(ConfigNode<?, ?> node, Object value) throws Exception {
        Field field = ConfigNode.class.getDeclaredField("value");
        field.setAccessible(true);
        field.set(node, value);
    }

}
