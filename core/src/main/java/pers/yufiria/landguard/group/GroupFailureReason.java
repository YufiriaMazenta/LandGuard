package pers.yufiria.landguard.group;

/**
 * 用户组操作失败原因。与认领失败原因分离，便于命令层精确反馈。
 */
public enum GroupFailureReason {

    NAME_TAKEN,
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
