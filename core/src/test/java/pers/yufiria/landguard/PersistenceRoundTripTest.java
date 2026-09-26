package pers.yufiria.landguard;

import crypticlib.database.connection.ConnectionSource;
import crypticlib.database.connection.JdbcConnectionSource;
import crypticlib.database.dao.DaoManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AC-13: SQLite 持久化与“重启恢复”。
 */
public class PersistenceRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void rowsSurviveConnectionRestart() throws Exception {
        Path dbFile = tempDir.resolve("landguard.db");
        UUID world = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        String claimId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();

        ConnectionSource first = new JdbcConnectionSource("jdbc:sqlite:" + dbFile);
        LandDaoManager.INSTANCE.init(first);
        LandDaoManager daos = LandDaoManager.INSTANCE;

        daos.claimDao().create(ClaimData.builder(claimId, world, "player", player.toString(), "home")
            .createdAt(1000L).lastActiveAt(2000L).bankBalance(12.5).build());
        daos.claimChunkDao().create(new ClaimChunkData(claimId, world, 3, -7));
        daos.claimChunkDao().create(new ClaimChunkData(claimId, world, 3, -6));
        daos.roleFlagDao().create(new ClaimRoleFlagData(claimId, "member", "CONTAINER", false));
        daos.claimSettingDao().create(new ClaimSettingData(claimId, "greeting", "hello"));
        daos.playerDao().create(new PlayerData(player, 100, 20, 9999L));
        daos.groupDao().create(new GroupData(groupId, "builders", player, 1L, 50.0));
        daos.groupMemberDao().create(new GroupMemberData(groupId, player, "owner"));
        daos.groupRoleDao().create(new GroupRoleData(groupId, "owner", 100, "Owner"));
        first.close();

        // 模拟服务器重启：全新连接 + 清空 Dao 元数据缓存
        DaoManager.clearCache();
        ConnectionSource second = new JdbcConnectionSource("jdbc:sqlite:" + dbFile);
        LandDaoManager.INSTANCE.init(second);
        LandDaoManager restarted = LandDaoManager.INSTANCE;

        assertEquals(1, restarted.claimDao().queryForAll().size());
        ClaimData claim = restarted.claimDao().queryForId(claimId);
        assertEquals(world, claim.getWorldUuid());
        assertEquals("player", claim.getOwnerType());
        assertEquals(12.5, claim.getBankBalance(), 0.0001);
        assertFalse(claim.isUpkeepExempt());

        List<ClaimChunkData> chunks = restarted.claimChunkDao().queryForAll();
        assertEquals(2, chunks.size());
        assertTrue(chunks.stream().anyMatch(c -> c.getChunkX() == 3 && c.getChunkZ() == -7));

        ClaimRoleFlagData flag = restarted.roleFlagDao().queryForAll().get(0);
        assertEquals("member", flag.getRoleId());
        assertFalse(flag.isValue());
        assertEquals("hello", restarted.claimSettingDao().queryForAll().get(0).getSettingValue());

        PlayerData restoredPlayer = restarted.playerDao().queryForId(player);
        assertEquals(100, restoredPlayer.getAccruedChunks());
        assertEquals(20, restoredPlayer.getBoughtChunks());

        assertEquals("builders", restarted.groupDao().queryForId(groupId).getName());
        assertEquals("owner", restarted.groupMemberDao().queryForAll().get(0).getRoleId());
        assertEquals(100, restarted.groupRoleDao().queryForAll().get(0).getPriority());
        second.close();
    }

}
