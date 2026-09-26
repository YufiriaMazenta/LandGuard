package pers.yufiria.landguard.claim;

import org.jetbrains.annotations.Nullable;

/**
 * 一次认领/放弃操作的结果（在 DB 写线程内产生，不可变）。
 * {@code skippedChunks} 是批量认领中因已被占用而跳过的区块数，不计入 {@code affectedChunks}。
 */
public record ClaimOpResult(
    boolean success,
    @Nullable ClaimFailureReason failureReason,
    @Nullable String claimId,
    int affectedChunks,
    int skippedChunks,
    int refundedChunks,
    long availableChunks
) {

    public static ClaimOpResult failed(ClaimFailureReason reason) {
        return new ClaimOpResult(false, reason, null, 0, 0, 0, -1L);
    }

    public static ClaimOpResult claimed(String claimId, int affectedChunks, int skippedChunks, long availableChunks) {
        return new ClaimOpResult(true, null, claimId, affectedChunks, skippedChunks, 0, availableChunks);
    }

    public static ClaimOpResult unclaimed(String claimId, int affectedChunks, int refundedChunks, long availableChunks) {
        return new ClaimOpResult(true, null, claimId, affectedChunks, 0, refundedChunks, availableChunks);
    }

    public static ClaimOpResult renamed(String claimId) {
        return new ClaimOpResult(true, null, claimId, 0, 0, 0, -1L);
    }

    public static ClaimOpResult transferred(String claimId, int affectedChunks) {
        return new ClaimOpResult(true, null, claimId, affectedChunks, 0, 0, -1L);
    }

}
