package pers.yufiria.landguard.database.dao;

import crypticlib.CrypticLib;
import crypticlib.CrypticLibPlugin;
import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.dao.Dao;
import crypticlib.database.dao.DaoManager;
import crypticlib.database.table.TableUtils;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import pers.yufiria.landguard.LifecycleOrder;
import pers.yufiria.landguard.database.DataSourceManager;
import pers.yufiria.landguard.database.SchemaMigrations;
import pers.yufiria.landguard.database.entity.*;

import java.sql.SQLException;

/**
 * 全部表的建表与 Dao 持有。
 * 生命周期优先级 -1：必须在 DataSourceManager(-2) 建连之后、DataStore(0) 读取之前执行。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, isAsync = true, priority = LifecycleOrder.DAO),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, isAsync = true, priority = LifecycleOrder.DAO)
    }
)
public enum LandDaoManager implements LifecycleTask {

    INSTANCE;

    private Dao<ClaimData> claimDao;
    private Dao<ClaimChunkData> claimChunkDao;
    private Dao<ClaimRoleFlagData> roleFlagDao;
    private Dao<ClaimSettingData> claimSettingDao;
    private Dao<PlayerData> playerDao;
    private Dao<PlayerQuotaData> playerQuotaDao;
    private Dao<GroupData> groupDao;
    private Dao<GroupMemberData> groupMemberDao;

    @Override
    public void onLifecycle(CrypticLibPlugin crypticLibPlugin, LifecyclePhase lifecyclePhase) {
        init(DataSourceManager.INSTANCE.databaseConnection());
    }

    public void init(ConnectionSource connectionSource) {
        try {
            claimDao = create(connectionSource, ClaimData.class);
            claimChunkDao = create(connectionSource, ClaimChunkData.class);
            roleFlagDao = create(connectionSource, ClaimRoleFlagData.class);
            claimSettingDao = create(connectionSource, ClaimSettingData.class);
            playerDao = create(connectionSource, PlayerData.class);
            playerQuotaDao = create(connectionSource, PlayerQuotaData.class);
            groupDao = create(connectionSource, GroupData.class);
            groupMemberDao = create(connectionSource, GroupMemberData.class);
            SchemaMigrations.migrate(connectionSource);
        } catch (SQLException e) {
            CrypticLib.info("&cFailed to initialize LandGuard tables: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private <T> Dao<T> create(ConnectionSource connectionSource, Class<T> entityClass) throws SQLException {
        Dao<T> dao = DaoManager.createDao(connectionSource, entityClass);
        TableUtils.createTableIfNotExists(connectionSource, entityClass);
        return dao;
    }

    public Dao<ClaimData> claimDao() {
        return claimDao;
    }

    public Dao<ClaimChunkData> claimChunkDao() {
        return claimChunkDao;
    }

    public Dao<ClaimRoleFlagData> roleFlagDao() {
        return roleFlagDao;
    }

    public Dao<ClaimSettingData> claimSettingDao() {
        return claimSettingDao;
    }

    public Dao<PlayerData> playerDao() {
        return playerDao;
    }

    public Dao<PlayerQuotaData> playerQuotaDao() {
        return playerQuotaDao;
    }

    public Dao<GroupData> groupDao() {
        return groupDao;
    }

    public Dao<GroupMemberData> groupMemberDao() {
        return groupMemberDao;
    }

}
