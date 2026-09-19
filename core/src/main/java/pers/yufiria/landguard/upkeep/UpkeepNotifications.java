package pers.yufiria.landguard.upkeep;

import crypticlib.CommonPlayer;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.util.LangUtils;

import java.util.Map;
import java.util.UUID;

/**
 * 回收周期结果的通知分发（可在全局区域线程或玩家实体线程调用）：
 * 在线所有者成员收到对应语言条目，控制台始终记录一份；离线玩家不补发。
 * Folia 上消息发送是线程安全的跨区域操作，故无需逐玩家调度。
 */
public final class UpkeepNotifications {

    private UpkeepNotifications() {
    }

    public static void dispatch(UpkeepCycleResult result) {
        if (result == null || result.notices().isEmpty()) {
            return;
        }
        for (UpkeepNotice notice : result.notices()) {
            Map<String, String> params = Map.of(
                "<name>", notice.claimName() == null ? "" : notice.claimName(),
                "<chunks>", String.valueOf(notice.chunks())
            );
            var entry = switch (notice.cause()) {
                case UPKEEP_DEBT -> notice.release()
                    ? Languages.UPKEEP_DEBT_RELEASED
                    : Languages.UPKEEP_DEBT_WARNING;
                case INACTIVITY -> notice.release()
                    ? Languages.UPKEEP_INACTIVITY_RELEASED
                    : Languages.UPKEEP_INACTIVITY_WARNING;
                case ORPHAN -> notice.release()
                    ? Languages.UPKEEP_ORPHAN_RELEASED
                    : Languages.UPKEEP_ORPHAN_WARNING;
            };
            // 控制台留底（含无人在线时的唯一可见渠道）
            LangUtils.info(entry, params);
            for (UUID uuid : notice.recipients()) {
                // fromUuid 只返回在线玩家，离线者自动跳过
                CommonPlayer.fromUuid(uuid).ifPresent(player -> LangUtils.sendLang(player, entry, params));
            }
        }
    }

}
