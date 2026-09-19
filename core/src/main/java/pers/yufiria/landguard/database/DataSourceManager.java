package pers.yufiria.landguard.database;

import crypticlib.CrypticLibPlugin;
import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.dao.DaoManager;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import pers.yufiria.landguard.config.DatabaseConfigs;
import pers.yufiria.landguard.database.exception.DatabaseLoadException;
import pers.yufiria.landguard.database.loader.DataSourceLoader;
import pers.yufiria.landguard.database.loader.MysqlDataSourceLoader;
import pers.yufiria.landguard.database.loader.SqliteDataSourceLoader;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, isAsync = true, priority = -2),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, isAsync = true, priority = -2),
        @LifecycleSchedule(phase = LifecyclePhase.DISABLE, priority = Integer.MAX_VALUE)
    }
)
public enum DataSourceManager implements LifecycleTask {

    INSTANCE;

    private final Map<String, DataSourceLoader> databaseLoaderMap = new ConcurrentHashMap<>();
    private volatile ConnectionSource databaseConnection;

    DataSourceManager() {
        registerDatabaseLoader("mysql", MysqlDataSourceLoader.INSTANCE);
        registerDatabaseLoader("sqlite", SqliteDataSourceLoader.INSTANCE);
    }

    @Override
    public void onLifecycle(CrypticLibPlugin crypticLibPlugin, LifecyclePhase lifecyclePhase) {
        switch (lifecyclePhase) {
            case ACTIVE -> databaseConnection = loadDatabaseConnection();
            case RELOAD -> {
                ConnectionSource old = databaseConnection;
                if (old != null) {
                    old.close();
                }
                DaoManager.clearCache();
                databaseConnection = loadDatabaseConnection();
            }
            case DISABLE -> {
                ConnectionSource old = databaseConnection;
                if (old != null) {
                    old.close();
                }
                databaseLoaderMap.clear();
            }
        }
    }

    private ConnectionSource loadDatabaseConnection() {
        String databaseType = DatabaseConfigs.TYPE.value().toLowerCase();
        DataSourceLoader dataSourceLoader = databaseLoaderMap.get(databaseType);
        if (dataSourceLoader == null) {
            throw new DatabaseLoadException("Unknown database type: " + databaseType);
        }
        try {
            return dataSourceLoader.load();
        } catch (SQLException e) {
            throw new DatabaseLoadException(e);
        }
    }

    public void registerDatabaseLoader(String type, DataSourceLoader dataSourceLoader) {
        databaseLoaderMap.put(type, dataSourceLoader);
    }

    public ConnectionSource databaseConnection() {
        ConnectionSource current = databaseConnection;
        if (current == null) {
            synchronized (this) {
                current = databaseConnection;
                if (current == null || !current.isOpen()) {
                    if (current != null) {
                        current.close();
                    }
                    databaseConnection = current = loadDatabaseConnection();
                }
            }
        }
        return current;
    }

}
