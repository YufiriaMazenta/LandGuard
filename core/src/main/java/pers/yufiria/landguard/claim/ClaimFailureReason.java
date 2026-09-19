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
    NOT_OWNER,
    /** 领地名非法（空或超过长度上限） */
    INVALID_NAME,
    /** 领地已属于该玩家，无需转让 */
    ALREADY_OWNED,
    /** 目标玩家在该世界已有领地（同一所有者每个世界只允许一块） */
    TARGET_HAS_CLAIM
}
