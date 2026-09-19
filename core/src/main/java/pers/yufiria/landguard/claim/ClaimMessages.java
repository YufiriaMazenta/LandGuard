package pers.yufiria.landguard.claim;

import org.bukkit.command.CommandSender;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.util.LangUtils;

import java.util.Map;

/**
 * 认领/放弃结果到语言文件条目的统一映射。
 */
public final class ClaimMessages {

    private ClaimMessages() {
    }

    public static void claimSuccess(CommandSender sender, ClaimOpResult result) {
        LangUtils.sendLang(sender, Languages.COMMAND_CLAIM_SUCCESS, Map.of(
            "count", String.valueOf(result.affectedChunks()),
            "available", formatAvailable(result.availableChunks())
        ));
    }

    public static void unclaimSuccess(CommandSender sender, ClaimOpResult result) {
        LangUtils.sendLang(sender, Languages.COMMAND_UNCLAIM_SUCCESS, Map.of(
            "count", String.valueOf(result.affectedChunks()),
            "refunded", String.valueOf(result.refundedChunks()),
            "available", formatAvailable(result.availableChunks())
        ));
    }

    public static void failure(CommandSender sender, ClaimFailureReason reason) {
        if (reason == null) {
            return;
        }
        LangUtils.sendLang(sender, switch (reason) {
            case OVERLAP -> Languages.COMMAND_FAIL_OVERLAP;
            case NOT_ADJACENT -> Languages.COMMAND_FAIL_NOT_ADJACENT;
            case QUOTA_EXCEEDED -> Languages.COMMAND_FAIL_QUOTA_EXCEEDED;
            case NOT_CLAIMED -> Languages.COMMAND_FAIL_NOT_CLAIMED;
            case NOT_OWNER -> Languages.COMMAND_FAIL_NOT_OWNER;
            case INVALID_TARGETS -> Languages.COMMAND_FAIL_INVALID_TARGETS;
        });
    }

    private static String formatAvailable(long available) {
        return available == Long.MAX_VALUE ? "∞" : String.valueOf(available);
    }

}
