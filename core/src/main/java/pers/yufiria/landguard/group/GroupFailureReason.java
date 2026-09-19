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
    ROLE_EXISTS,
    ROLE_NOT_FOUND,
    ROLE_ID_INVALID,
    ROLE_BUILTIN,
    CLAIM_NOT_FOUND,
    NOT_CLAIM_OWNER

}
