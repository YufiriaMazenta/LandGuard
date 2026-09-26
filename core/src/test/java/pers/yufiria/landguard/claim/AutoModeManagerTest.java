package pers.yufiria.landguard.claim;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 行走自动模式的开关语义：认领与放弃互斥，再次切换同一模式即关闭，且各玩家状态互不影响。
 */
public class AutoModeManagerTest {

    @Test
    void modesAreMutuallyExclusiveAndToggleBackToOff() {
        UUID player = UUID.randomUUID();

        assertEquals(AutoModeManager.Mode.OFF, AutoModeManager.INSTANCE.modeOf(player));

        assertEquals(AutoModeManager.Mode.CLAIM,
            AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.CLAIM));

        // 切到放弃模式时自动认领随之关闭
        assertEquals(AutoModeManager.Mode.UNCLAIM,
            AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.UNCLAIM));

        // 再次切换同一模式即关闭
        assertEquals(AutoModeManager.Mode.OFF,
            AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.UNCLAIM));
        assertEquals(AutoModeManager.Mode.OFF, AutoModeManager.INSTANCE.modeOf(player));
    }

    @Test
    void modesAreIsolatedPerPlayer() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        AutoModeManager.INSTANCE.toggle(first, AutoModeManager.Mode.CLAIM);
        AutoModeManager.INSTANCE.toggle(second, AutoModeManager.Mode.UNCLAIM);

        assertEquals(AutoModeManager.Mode.CLAIM, AutoModeManager.INSTANCE.modeOf(first));
        assertEquals(AutoModeManager.Mode.UNCLAIM, AutoModeManager.INSTANCE.modeOf(second));

        AutoModeManager.INSTANCE.toggle(first, AutoModeManager.Mode.CLAIM);
        assertEquals(AutoModeManager.Mode.OFF, AutoModeManager.INSTANCE.modeOf(first));
        assertEquals(AutoModeManager.Mode.UNCLAIM, AutoModeManager.INSTANCE.modeOf(second));

        // 收尾把状态切回关闭：单例状态跨测试类共享，不留残影
        AutoModeManager.INSTANCE.toggle(second, AutoModeManager.Mode.UNCLAIM);
        assertEquals(AutoModeManager.Mode.OFF, AutoModeManager.INSTANCE.modeOf(second));
    }

}