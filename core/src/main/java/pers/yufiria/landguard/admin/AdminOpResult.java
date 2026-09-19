package pers.yufiria.landguard.admin;

import org.jetbrains.annotations.Nullable;

/**
 * 管理员管理操作（强制释放/转让/豁免）的结果。
 */
public record AdminOpResult(
    boolean success,
    @Nullable AdminFailureReason failureReason,
    @Nullable String claimId,
    int affectedChunks
) {

    public static AdminOpResult failed(AdminFailureReason reason) {
        return new AdminOpResult(false, reason, null, 0);
    }

    public static AdminOpResult ok(String claimId, int affectedChunks) {
        return new AdminOpResult(true, null, claimId, affectedChunks);
    }

}
