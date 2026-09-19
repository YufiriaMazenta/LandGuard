package pers.yufiria.landguard.protection;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.api.event.EventCaller;
import pers.yufiria.landguard.api.event.LandProtectionQueryEvent;
import pers.yufiria.landguard.api.event.ProtectionDecision;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;

import java.util.UUID;

/**
 * 监听套件的判定入口（Task 6 使用）：先触发 {@link LandProtectionQueryEvent} 让第三方干预，
 * 未干预时回落到 {@link ProtectionChecker} 纯判定链。自然类事件无玩家主体，直接走 checker。
 */
public final class ProtectionQueries {

    private ProtectionQueries() {
    }

    public static @NotNull CheckResult queryBehavior(@NotNull Player player,
                                                     @NotNull UUID worldUuid,
                                                     int chunkX,
                                                     int chunkZ,
                                                     @NotNull ProtectionFlag flag) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        CheckContext context = ProtectionChecker.context(snapshot, player.getUniqueId(), worldUuid, chunkX, chunkZ);
        LandProtectionQueryEvent event = new LandProtectionQueryEvent(
            player, worldUuid, chunkX, chunkZ, flag.id(), context.owner(), context.memberRole()
        );
        EventCaller.call(event);
        ProtectionDecision decision = event.decision();
        if (decision == ProtectionDecision.FORCE_ALLOW) {
            return new CheckResult(context, flag, true, false);
        }
        if (decision == ProtectionDecision.FORCE_DENY) {
            return new CheckResult(context, flag, false, false);
        }
        // 第三方未干预时，bypass 权限节点（默认不授予）才生效；FORCE_DENY 可压制 bypass
        if (player.hasPermission(ProtectionPermissions.BYPASS)) {
            return new CheckResult(context, flag, true, false);
        }
        return ProtectionChecker.decideBehavior(context, snapshot, flag);
    }

    public static @NotNull CheckResult queryNatural(@NotNull UUID worldUuid,
                                                    int chunkX,
                                                    int chunkZ,
                                                    @NotNull ProtectionFlag flag) {
        return ProtectionChecker.checkNatural(DataStore.INSTANCE.snapshot(), worldUuid, chunkX, chunkZ, flag);
    }

}
