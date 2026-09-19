package pers.yufiria.landguard.admin;

/**
 * 管理员操作失败原因，供语言文件映射。
 */
public enum AdminFailureReason {
    /** 指定 claimId 不存在 */
    CLAIM_NOT_FOUND,
    /** 目标玩家无法解析（UUID 格式非法等） */
    INVALID_PLAYER,
    /** 领地已经属于该玩家，无需转让 */
    ALREADY_OWNED,
    /** 参数为空或格式错误 */
    INVALID_ARGUMENT
}
