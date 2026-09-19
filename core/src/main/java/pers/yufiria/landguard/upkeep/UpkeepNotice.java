package pers.yufiria.landguard.upkeep;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 一个周期内产生的通知事件：警告或最终释放。
 * recipients 为应收到通知的所有者成员 UUID（个人=所有者本人，组=全体成员）；
 * 控制台始终另外记录一份，离线玩家不补发。
 */
public record UpkeepNotice(
    String claimId,
    String claimName,
    UpkeepCause cause,
    boolean release,
    int chunks,
    Set<UUID> recipients
) {

    public static UpkeepNotice warning(String claimId, String claimName, UpkeepCause cause,
                                       Collection<UUID> recipients) {
        return new UpkeepNotice(claimId, claimName, cause, false, 0,
            Set.copyOf(new LinkedHashSet<>(recipients)));
    }

    public static UpkeepNotice released(String claimId, String claimName, UpkeepCause cause, int chunks,
                                        Collection<UUID> recipients) {
        return new UpkeepNotice(claimId, claimName, cause, true, chunks,
            Set.copyOf(new LinkedHashSet<>(recipients)));
    }

}
