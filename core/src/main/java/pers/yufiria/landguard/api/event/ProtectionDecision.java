package pers.yufiria.landguard.api.event;

/**
 * 第三方对一次保护判定的强制结论。
 * DEFAULT 表示不干预，继续走 LandGuard 的角色/flag 判定链。
 */
public enum ProtectionDecision {
    DEFAULT,
    FORCE_ALLOW,
    FORCE_DENY
}
