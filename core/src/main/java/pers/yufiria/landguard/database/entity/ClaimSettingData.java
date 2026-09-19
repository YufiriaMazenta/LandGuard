package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Table;

/**
 * 领地级杂项设置（自然类 flag 之外的键值扩展），行存在即表示覆盖。
 */
@Table(name = "lg_claim_setting")
public class ClaimSettingData {

    @Field(id = true, generated = true)
    private long id;

    @Field(name = "claim_id", nullable = false)
    private String claimId;

    @Field(name = "setting_key", nullable = false)
    private String settingKey;

    @Field(name = "setting_value")
    private String settingValue;

    public ClaimSettingData() {
    }

    public ClaimSettingData(String claimId, String settingKey, String settingValue) {
        this.claimId = claimId;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
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

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

}
