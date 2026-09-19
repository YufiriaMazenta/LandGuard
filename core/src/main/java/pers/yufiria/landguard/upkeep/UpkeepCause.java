package pers.yufiria.landguard.upkeep;

/**
 * 领地被系统警告/释放的原因。
 */
public enum UpkeepCause {

    /** 维护费欠费越过宽限期 */
    UPKEEP_DEBT,
    /** 所有者长期不活跃越过警告宽限期 */
    INACTIVITY,
    /** 所有者实体无法解析（孤儿）越过宽限期 */
    ORPHAN

}
