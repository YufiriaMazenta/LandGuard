package pers.yufiria.landguard.database;

import crypticlib.database.connection.ConnectionSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 极简结构迁移：createTableIfNotExists 只负责建新表，不会为已存在的旧表补列。
 * 这里通过 JDBC 元数据比对实体声明的列，缺失即 ALTER TABLE ADD COLUMN。
 * 只追加、不删除不改类型，保证旧数据安全；MySQL 与 SQLite 均兼容。
 */
public final class SchemaMigrations {

    /** 表名 -> （列名 -> 列 DDL 片段），按声明顺序执行 */
    private static final Map<String, Map<String, String>> ADDITIVE_COLUMNS = Map.of(
        "lg_claim", ordered(
            "upkeep_charged_at", "BIGINT DEFAULT 0",
            "upkeep_unpaid_since", "BIGINT DEFAULT 0",
            "inactive_warned_at", "BIGINT DEFAULT 0",
            "orphan_since", "BIGINT DEFAULT 0"
        )
    );

    private SchemaMigrations() {
    }

    public static void migrate(ConnectionSource source) throws SQLException {
        Connection connection = source.getConnection();
        try {
            for (Map.Entry<String, Map<String, String>> table : ADDITIVE_COLUMNS.entrySet()) {
                String realTableName = resolveTableName(connection, table.getKey());
                if (realTableName == null) {
                    // 表尚不存在：建表流程会按最新实体创建，无需迁移
                    continue;
                }
                Set<String> existing = readColumns(connection, realTableName);
                for (Map.Entry<String, String> column : table.getValue().entrySet()) {
                    if (!containsIgnoreCase(existing, column.getKey())) {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("ALTER TABLE " + realTableName
                                + " ADD COLUMN " + column.getKey() + " " + column.getValue());
                        }
                    }
                }
            }
        } finally {
            source.releaseConnection(connection);
        }
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }

    private static String resolveTableName(Connection connection, String wanted) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (name != null && name.equalsIgnoreCase(wanted)) {
                    return name;
                }
            }
        }
        return null;
    }

    private static Set<String> readColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet result = metaData.getColumns(connection.getCatalog(), null, tableName, "%")) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private static boolean containsIgnoreCase(Set<String> columns, String wanted) {
        for (String column : columns) {
            if (column.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

}
