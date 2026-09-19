package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Field.ColumnType;
import crypticlib.database.annotation.Table;

import java.util.UUID;

@Table(name = "lg_group")
public class GroupData {

    @Field(id = true, name = "group_id", length = 36)
    private String groupId;

    @Field(name = "name", nullable = false)
    private String name;

    @Field(name = "leader_uuid", nullable = false)
    private UUID leaderUuid;

    @Field(name = "created_at", type = ColumnType.BIGINT)
    private long createdAt;

    @Field(name = "bank_balance", type = ColumnType.DOUBLE)
    private double bankBalance;

    public GroupData() {
    }

    public GroupData(String groupId, String name, UUID leaderUuid, long createdAt, double bankBalance) {
        this.groupId = groupId;
        this.name = name;
        this.leaderUuid = leaderUuid;
        this.createdAt = createdAt;
        this.bankBalance = bankBalance;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = bankBalance;
    }

}
