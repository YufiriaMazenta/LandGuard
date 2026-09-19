package pers.yufiria.landguard.group;

import org.jetbrains.annotations.Nullable;

/**
 * 用户组写操作结果。成功时携带组 ID；失败时携带原因，快照保持变更前状态。
 */
public record GroupOpResult(boolean success, @Nullable String groupId, @Nullable GroupFailureReason failureReason) {

    private static final GroupOpResult GENERIC_OK = new GroupOpResult(true, null, null);

    public static GroupOpResult ok() {
        return GENERIC_OK;
    }

    public static GroupOpResult ok(String groupId) {
        return new GroupOpResult(true, groupId, null);
    }

    public static GroupOpResult failed(GroupFailureReason reason) {
        return new GroupOpResult(false, null, reason);
    }

}
