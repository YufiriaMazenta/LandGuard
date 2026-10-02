package pers.yufiria.landguard.group;

/**
 * 用户组操作失败原因。与认领失败原因分离，便于命令层精确反馈。
 */
public enum GroupFailureReason {

    /** 标识符已被占用 */
    KEY_TAKEN,
    /** 标识符格式非法（1-32 位小写字母、数字、下划线） */
    INVALID_KEY,
    /** 展示名非法（空或超过长度上限） */
    INVALID_NAME,
    GROUP_NOT_FOUND,
    NOT_LEADER,
    NOT_MANAGER,
    NOT_MEMBER,
    TARGET_NOT_MEMBER,
    ALREADY_MEMBER,
    NO_INVITE,
    LEADER_CANNOT_LEAVE,
    CANNOT_KICK,
    /** 身份不足以指派该成员（越级指派、指派领袖身份或改动自己） */
    CANNOT_ASSIGN,
    IDENTITY_NOT_FOUND,
    CLAIM_NOT_FOUND,
    NOT_CLAIM_OWNER,
    /** 目标用户组在该世界已有领地（不合并，避免同一所有者同世界多块地） */
    GROUP_HAS_CLAIM,
    /** 目标用户组的区块额度不足以接收这块领地 */
    GROUP_QUOTA_EXCEEDED,
    /** 可加入的用户组数已达上限（含自己拥有的组织） */
    JOIN_LIMIT_EXCEEDED,
    /** 可拥有的用户组数已达上限 */
    OWN_LIMIT_EXCEEDED

}
