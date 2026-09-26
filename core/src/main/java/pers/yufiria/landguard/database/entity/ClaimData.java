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

    /** 具名构造入口：必填五项之外都可省略。 */
    public static Builder builder(String claimId, UUID worldUuid, String ownerType, String ownerId, String name) {
        return new Builder(claimId, worldUuid, ownerType, ownerId, name);
    }

    /**
     * 领地的具名构造器，取代原先的伸缩构造器。
     * 缺省值与「新建领地」语义一致：非管理领地、创建/活跃时间取当前时间、银行余额 0、
     * 不豁免维护费、生命周期时间戳全 0（未扣费、未欠费、未警告、非孤儿）。
     */
    public static final class Builder {

        private final String claimId;
        private final UUID worldUuid;
        private final String ownerType;
        private final String ownerId;
        private final String name;
        private boolean admin;
        private long createdAt;
        private long lastActiveAt;
        private double bankBalance;
        private boolean upkeepExempt;
        private long upkeepChargedAt;
        private long upkeepUnpaidSince;
        private long inactiveWarnedAt;
        private long orphanSince;

        private Builder(String claimId, UUID worldUuid, String ownerType, String ownerId, String name) {
            this.claimId = claimId;
            this.worldUuid = worldUuid;
            this.ownerType = ownerType;
            this.ownerId = ownerId;
            this.name = name;
            long now = System.currentTimeMillis();
            this.createdAt = now;
            this.lastActiveAt = now;
        }

        public Builder admin(boolean admin) {
            this.admin = admin;
            return this;
        }

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastActiveAt(long lastActiveAt) {
            this.lastActiveAt = lastActiveAt;
            return this;
        }

        public Builder bankBalance(double bankBalance) {
            this.bankBalance = bankBalance;
            return this;
        }

        public Builder upkeepExempt(boolean upkeepExempt) {
            this.upkeepExempt = upkeepExempt;
            return this;
        }

        public Builder upkeepChargedAt(long upkeepChargedAt) {
            this.upkeepChargedAt = upkeepChargedAt;
            return this;
        }

        public Builder upkeepUnpaidSince(long upkeepUnpaidSince) {
            this.upkeepUnpaidSince = upkeepUnpaidSince;
            return this;
        }

        public Builder inactiveWarnedAt(long inactiveWarnedAt) {
            this.inactiveWarnedAt = inactiveWarnedAt;
            return this;
        }

        public Builder orphanSince(long orphanSince) {
            this.orphanSince = orphanSince;
            return this;
        }

        public ClaimData build() {
            ClaimData data = new ClaimData();
            data.claimId = claimId;
            data.worldUuid = worldUuid;
            data.ownerType = ownerType;
            data.ownerId = ownerId;
            data.name = name;
            data.admin = admin;
            data.createdAt = createdAt;
            data.lastActiveAt = lastActiveAt;
            data.bankBalance = bankBalance;
            data.upkeepExempt = upkeepExempt;
            data.upkeepChargedAt = upkeepChargedAt;
            data.upkeepUnpaidSince = upkeepUnpaidSince;
            data.inactiveWarnedAt = inactiveWarnedAt;
            data.orphanSince = orphanSince;
            return data;
        }

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
