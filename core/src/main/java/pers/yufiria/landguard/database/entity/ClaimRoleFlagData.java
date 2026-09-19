package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Field.ColumnType;
import crypticlib.database.annotation.Table;

@Table(name = "lg_role_flag")
public class ClaimRoleFlagData {

    @Field(id = true, generated = true)
    private long id;

    @Field(name = "claim_id", nullable = false)
    private String claimId;

    @Field(name = "role_id", nullable = false)
    private String roleId;

    @Field(name = "flag_key", nullable = false)
    private String flagKey;

    @Field(name = "value", type = ColumnType.BOOLEAN)
    private boolean value;

    public ClaimRoleFlagData() {
    }

    public ClaimRoleFlagData(String claimId, String roleId, String flagKey, boolean value) {
        this.claimId = claimId;
        this.roleId = roleId;
        this.flagKey = flagKey;
        this.value = value;
    }

    public long getId() {
        return id;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public boolean isValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

}
