package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Field.ColumnType;
import crypticlib.database.annotation.Table;

import java.util.UUID;

@Table(name = "lg_player_data")
public class PlayerData {

    @Field(id = true, name = "player_uuid")
    private UUID playerUuid;

    @Field(name = "accrued_chunks", type = ColumnType.INT)
    private int accruedChunks;

    @Field(name = "bought_chunks", type = ColumnType.INT)
    private int boughtChunks;

    @Field(name = "last_login", type = ColumnType.BIGINT)
    private long lastLogin;

    public PlayerData() {
    }

    public PlayerData(UUID playerUuid, int accruedChunks, int boughtChunks, long lastLogin) {
        this.playerUuid = playerUuid;
        this.accruedChunks = accruedChunks;
        this.boughtChunks = boughtChunks;
        this.lastLogin = lastLogin;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getAccruedChunks() {
        return accruedChunks;
    }

    public void setAccruedChunks(int accruedChunks) {
        this.accruedChunks = accruedChunks;
    }

    public int getBoughtChunks() {
        return boughtChunks;
    }

    public void setBoughtChunks(int boughtChunks) {
        this.boughtChunks = boughtChunks;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

}
