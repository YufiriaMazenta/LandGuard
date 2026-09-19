package pers.yufiria.landguard.claim;

/**
 * 认领/放弃操作的失败原因，供语言文件映射为明确提示。
 */
public enum ClaimFailureReason {
    /** 目标区块集合为空或跨世界 */
    INVALID_TARGETS,
    /** 目标区块已被任意领地占用 */
    OVERLAP,
    /** 与操作者已有领地不相邻（或区块组未与已有领地连通） */
    NOT_ADJACENT,
    /** 区块额度不足 */
    QUOTA_EXCEEDED,
    /** 目标区块不在任何领地内 */
    NOT_CLAIMED,
    /** 目标区块属于其他所有者 */
    NOT_OWNER
}
