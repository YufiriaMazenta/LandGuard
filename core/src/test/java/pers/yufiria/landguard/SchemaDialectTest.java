package pers.yufiria.landguard;

import crypticlib.database.dialect.MysqlDialect;
import crypticlib.database.dialect.SqliteDialect;
import crypticlib.database.table.TableInfo;
import org.junit.jupiter.api.Test;
import pers.yufiria.landguard.database.entity.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-13 补充：无 MySQL 服务端环境下，验证 8 张表在 MySQL/SQLite 两种方言下
 * 均能生成完整合法的建表语句（真实 MySQL 连通测试待部署环境执行）。
 */
public class SchemaDialectTest {

    private static final List<Class<?>> ENTITIES = List.of(
        ClaimData.class,
        ClaimChunkData.class,
        ClaimRoleFlagData.class,
        ClaimSettingData.class,
        PlayerData.class,
        GroupData.class,
        GroupMemberData.class,
        GroupRoleData.class
    );

    @Test
    void mysqlDdlContainsTableAndColumns() {
        MysqlDialect mysql = new MysqlDialect();
        for (Class<?> entity : ENTITIES) {
            TableInfo info = TableInfo.of(entity);
            String sql = mysql.generateCreateTableSql(info);
            System.out.println("[MYSQL] " + sql);
            assertTrue(sql.contains("CREATE TABLE"), entity.getSimpleName());
            assertTrue(sql.contains(info.getTableName()), entity.getSimpleName());
            for (var column : info.getColumns()) {
                assertTrue(sql.contains(column.getColumnName()),
                    entity.getSimpleName() + " missing column " + column.getColumnName());
            }
            assertFalse(sql.contains("null"), entity.getSimpleName() + " ddl has null token");
        }
    }

    @Test
    void sqliteDdlContainsTableAndColumns() {
        SqliteDialect sqlite = new SqliteDialect();
        for (Class<?> entity : ENTITIES) {
            TableInfo info = TableInfo.of(entity);
            String sql = sqlite.generateCreateTableSql(info);
            System.out.println("[SQLITE] " + sql);
            assertTrue(sql.contains("CREATE TABLE"), entity.getSimpleName());
            assertTrue(sql.contains(info.getTableName()), entity.getSimpleName());
            for (var column : info.getColumns()) {
                assertTrue(sql.contains(column.getColumnName()),
                    entity.getSimpleName() + " missing column " + column.getColumnName());
            }
            assertFalse(sql.contains("null"), entity.getSimpleName() + " ddl has null token");
        }
    }

}
