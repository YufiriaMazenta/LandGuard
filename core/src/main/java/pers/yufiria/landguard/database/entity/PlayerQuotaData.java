package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Field.ColumnType;
import crypticlib.database.annotation.Table;

import java.util.UUID;

/**
 * 玩家已净消耗的区块额度。
 * 独立成表（lg_player_quota）以加法方式演进，不改动既有 lg_player_data 结构：
 * 可用额度 = lg_player_data.accrued_chunks + bought_chunks - used_chunks；
 * 认领时增加，放弃时按配置返还比例减少。仅由 DB 写线程访问，不进入只读快照。
 */
@Table(name = "lg_player_quota")
public class PlayerQuotaData {

    @Field(id = true, name = "player_uuid")
    private UUID playerUuid;

    @Field(name = "used_chunks", type = ColumnType.INT)
    private int usedChunks;

    public PlayerQuotaData() {
    }

    public PlayerQuotaData(UUID playerUuid, int usedChunks) {
        this.playerUuid = playerUuid;
        this.usedChunks = usedChunks;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public int getUsedChunks() {
        return usedChunks;
    }

    public void setUsedChunks(int usedChunks) {
        this.usedChunks = usedChunks;
    }

}
