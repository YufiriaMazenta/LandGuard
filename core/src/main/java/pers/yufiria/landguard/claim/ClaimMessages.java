package pers.yufiria.landguard.claim;

import crypticlib.Invoker;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.util.LangUtils;

import java.util.Map;

/**
 * 认领/放弃结果到语言文件条目的统一映射。
 */
public final class ClaimMessages {

    private ClaimMessages() {
    }

    public static void claimSuccess(Invoker player, ClaimOpResult result) {
        LangUtils.sendLang(player, Languages.COMMAND_CLAIM_SUCCESS, Map.of(
            "<count>", String.valueOf(result.affectedChunks()),
            "<available>", formatAvailable(result.availableChunks())
        ));
    }

    public static void unclaimSuccess(Invoker player, ClaimOpResult result) {
        LangUtils.sendLang(player, Languages.COMMAND_UNCLAIM_SUCCESS, Map.of(
            "<count>", String.valueOf(result.affectedChunks()),
            "<refunded>", String.valueOf(result.refundedChunks()),
            "<available>", formatAvailable(result.availableChunks())
        ));
    }

    public static void failure(Invoker invoker, ClaimFailureReason reason) {
        if (reason == null) {
            return;
        }
        LangUtils.sendLang(invoker, switch (reason) {
            case OVERLAP -> Languages.COMMAND_FAIL_OVERLAP;
            case NOT_ADJACENT -> Languages.COMMAND_FAIL_NOT_ADJACENT;
            case QUOTA_EXCEEDED -> Languages.COMMAND_FAIL_QUOTA_EXCEEDED;
            case NOT_CLAIMED -> Languages.COMMAND_FAIL_NOT_CLAIMED;
            case NOT_OWNER -> Languages.COMMAND_FAIL_NOT_OWNER;
            case INVALID_TARGETS -> Languages.COMMAND_FAIL_INVALID_TARGETS;
            case INVALID_NAME -> Languages.COMMAND_FAIL_INVALID_NAME;
            case ALREADY_OWNED -> Languages.COMMAND_FAIL_ALREADY_OWNED;
            case TARGET_HAS_CLAIM -> Languages.COMMAND_FAIL_TARGET_HAS_CLAIM;
        });
    }

    private static String formatAvailable(long available) {
        return available == Long.MAX_VALUE ? "∞" : String.valueOf(available);
    }

}
