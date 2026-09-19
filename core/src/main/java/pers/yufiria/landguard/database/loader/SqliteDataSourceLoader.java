package pers.yufiria.landguard.database.loader;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import org.bukkit.configuration.ConfigurationSection;
import pers.yufiria.landguard.config.DatabaseConfigs;

import java.sql.SQLException;

public enum SqliteDataSourceLoader implements DataSourceLoader {

    INSTANCE;

    @Override
    public ConnectionSource load() throws SQLException {
        ConfigurationSection databaseConfig = DatabaseConfigs.SQLITE.value();
        String file = databaseConfig.getString("file", "plugins/LandGuard/data.db");
        String params = databaseConfig.getString("parameters");
        String jdbcUrl = "jdbc:sqlite:" + file;
        if (params != null && !params.isEmpty()) {
            jdbcUrl += "?" + params;
        }
        return new JdbcConnectionSource(jdbcUrl);
    }

}
