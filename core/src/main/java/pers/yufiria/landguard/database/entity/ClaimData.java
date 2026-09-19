package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Field.ColumnType;
import crypticlib.database.annotation.Table;

import java.util.UUID;

@Table(name = "lg_claim")
public class ClaimData {

    @Field(id = true, name = "claim_id", length = 36)
    private String claimId;

    @Field(name = "world_uuid", nullable = false)
    private UUID worldUuid;

    @Field(name = "owner_type", nullable = false)
    private String ownerType;

    @Field(name = "owner_id", nullable = false)
    private String ownerId;

    @Field(name = "name")
    private String name;

    @Field(name = "admin", type = ColumnType.BOOLEAN)
    private boolean admin;

    @Field(name = "created_at", type = ColumnType.BIGINT)
    private long createdAt;

    @Field(name = "last_active_at", type = ColumnType.BIGINT)
    private long lastActiveAt;

    @Field(name = "bank_balance", type = ColumnType.DOUBLE)
    private double bankBalance;

    @Field(name = "upkeep_exempt", type = ColumnType.BOOLEAN)
    private boolean upkeepExempt;

    /** 上次 upkeep 扣费尝试时间（0=尚未初始化，下一次周期只记录时间不扣费） */
    @Field(name = "upkeep_charged_at", type = ColumnType.BIGINT)
    private long upkeepChargedAt;

    /** 欠费起算时间（0=不欠费）；越过宽限期仍欠费则自动释放 */
    @Field(name = "upkeep_unpaid_since", type = ColumnType.BIGINT)
    private long upkeepUnpaidSince;

    /** 不活跃警告时间（0=未警告）；警告后越过宽限期仍不活跃则自动释放 */
    @Field(name = "inactive_warned_at", type = ColumnType.BIGINT)
    private long inactiveWarnedAt;

    /** 成为孤儿领地（所有者实体无法解析）的起算时间（0=非孤儿）；越过孤儿宽限则自动释放 */
    @Field(name = "orphan_since", type = ColumnType.BIGINT)
    private long orphanSince;

    public ClaimData() {
    }

    public ClaimData(String claimId, UUID worldUuid, String ownerType, String ownerId, String name,
                     boolean admin, long createdAt, long lastActiveAt, double bankBalance, boolean upkeepExempt) {
        this(claimId, worldUuid, ownerType, ownerId, name, admin, createdAt, lastActiveAt,
            bankBalance, upkeepExempt, 0L, 0L, 0L, 0L);
    }

    public ClaimData(String claimId, UUID worldUuid, String ownerType, String ownerId, String name,
                     boolean admin, long createdAt, long lastActiveAt, double bankBalance, boolean upkeepExempt,
                     long upkeepChargedAt, long upkeepUnpaidSince, long inactiveWarnedAt) {
        this(claimId, worldUuid, ownerType, ownerId, name, admin, createdAt, lastActiveAt,
            bankBalance, upkeepExempt, upkeepChargedAt, upkeepUnpaidSince, inactiveWarnedAt, 0L);
    }

    public ClaimData(String claimId, UUID worldUuid, String ownerType, String ownerId, String name,
                     boolean admin, long createdAt, long lastActiveAt, double bankBalance, boolean upkeepExempt,
                     long upkeepChargedAt, long upkeepUnpaidSince, long inactiveWarnedAt, long orphanSince) {
        this.claimId = claimId;
        this.worldUuid = worldUuid;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.name = name;
        this.admin = admin;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.bankBalance = bankBalance;
        this.upkeepExempt = upkeepExempt;
        this.upkeepChargedAt = upkeepChargedAt;
        this.upkeepUnpaidSince = upkeepUnpaidSince;
        this.inactiveWarnedAt = inactiveWarnedAt;
        this.orphanSince = orphanSince;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public UUID getWorldUuid() {
        return worldUuid;
    }

    public void setWorldUuid(UUID worldUuid) {
        this.worldUuid = worldUuid;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = bankBalance;
    }

    public boolean isUpkeepExempt() {
        return upkeepExempt;
    }

    public void setUpkeepExempt(boolean upkeepExempt) {
        this.upkeepExempt = upkeepExempt;
    }

    public long getUpkeepChargedAt() {
        return upkeepChargedAt;
    }

    public void setUpkeepChargedAt(long upkeepChargedAt) {
        this.upkeepChargedAt = upkeepChargedAt;
    }

    public long getUpkeepUnpaidSince() {
        return upkeepUnpaidSince;
    }

    public void setUpkeepUnpaidSince(long upkeepUnpaidSince) {
        this.upkeepUnpaidSince = upkeepUnpaidSince;
    }

    public long getInactiveWarnedAt() {
        return inactiveWarnedAt;
    }

    public void setInactiveWarnedAt(long inactiveWarnedAt) {
        this.inactiveWarnedAt = inactiveWarnedAt;
    }

    public long getOrphanSince() {
        return orphanSince;
    }

    public void setOrphanSince(long orphanSince) {
        this.orphanSince = orphanSince;
    }

}
