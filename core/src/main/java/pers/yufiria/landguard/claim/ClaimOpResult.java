package pers.yufiria.landguard.claim;

import org.jetbrains.annotations.Nullable;

/**
 * 一次认领/放弃操作的结果（在 DB 写线程内产生，不可变）。
 */
public record ClaimOpResult(
    boolean success,
    @Nullable ClaimFailureReason failureReason,
    @Nullable String claimId,
    int affectedChunks,
    int refundedChunks,
    long availableChunks
) {

    public static ClaimOpResult failed(ClaimFailureReason reason) {
        return new ClaimOpResult(false, reason, null, 0, 0, -1L);
    }

    public static ClaimOpResult claimed(String claimId, int affectedChunks, long availableChunks) {
        return new ClaimOpResult(true, null, claimId, affectedChunks, 0, availableChunks);
    }

    public static ClaimOpResult unclaimed(String claimId, int affectedChunks, int refundedChunks, long availableChunks) {
        return new ClaimOpResult(true, null, claimId, affectedChunks, refundedChunks, availableChunks);
    }

}
