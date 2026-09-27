package pers.yufiria.landguard.claim;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 行走自动状态的开关语义：认领与放弃互斥；状态由「模式 + 身份」共同决定，
 * 同模式同身份再次切换即关闭、换身份则保留模式切换身份，且各玩家状态互不影响。
 */
public class AutoModeManagerTest {

    @Test
    void twoArgToggleIsEquivalentToPersonalIdentity() {
        UUID player = UUID.randomUUID();

        assertEquals(AutoModeManager.AutoState.OFF, AutoModeManager.INSTANCE.stateOf(player));

        AutoModeManager.AutoState on = AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.CLAIM);
        assertEquals(AutoModeManager.Mode.CLAIM, on.mode());
        assertNull(on.groupId(), "兼容重载等价于以本人身份认领");
        assertEquals(AutoModeManager.Mode.CLAIM, AutoModeManager.INSTANCE.modeOf(player));

        // 收尾把状态切回关闭：单例状态跨测试类共享，不留残影
        assertEquals(AutoModeManager.AutoState.OFF,
            AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.CLAIM));
    }

    @Test
    void sameModeAndSameIdentityTogglesBackToOff() {
        UUID player = UUID.randomUUID();
        String groupId = "guild";

        assertEquals(AutoModeManager.Mode.CLAIM,
            AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.CLAIM, groupId).mode());

        // 同模式 + 同身份：再次切换即关闭
        assertEquals(AutoModeManager.AutoState.OFF,
            AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.CLAIM, groupId));
        assertEquals(AutoModeManager.Mode.OFF, AutoModeManager.INSTANCE.modeOf(player));
    }

    @Test
    void switchingIdentityKeepsModeAndSwitchesState() {
        UUID player = UUID.randomUUID();
        AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.CLAIM, "guild-a");

        // 同模式、身份不同：切换身份而不是关闭
        AutoModeManager.AutoState switched = AutoModeManager.INSTANCE.toggle(
            player, AutoModeManager.Mode.CLAIM, "guild-b");
        assertEquals(AutoModeManager.Mode.CLAIM, switched.mode());
        assertEquals("guild-b", switched.groupId());

        // 换到放弃模式（本人身份）：同样切走
        AutoModeManager.AutoState unclaim = AutoModeManager.INSTANCE.toggle(
            player, AutoModeManager.Mode.UNCLAIM);
        assertEquals(AutoModeManager.Mode.UNCLAIM, unclaim.mode());

        // 收尾
        AutoModeManager.INSTANCE.toggle(player, AutoModeManager.Mode.UNCLAIM);
    }

    @Test
    void statesAreIsolatedPerPlayer() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        AutoModeManager.INSTANCE.toggle(first, AutoModeManager.Mode.CLAIM, "g1");
        AutoModeManager.INSTANCE.toggle(second, AutoModeManager.Mode.UNCLAIM);

        assertEquals(AutoModeManager.Mode.CLAIM, AutoModeManager.INSTANCE.stateOf(first).mode());
        assertEquals("g1", AutoModeManager.INSTANCE.stateOf(first).groupId());
        assertEquals(AutoModeManager.Mode.UNCLAIM, AutoModeManager.INSTANCE.modeOf(second));
        assertNull(AutoModeManager.INSTANCE.stateOf(second).groupId());

        AutoModeManager.INSTANCE.toggle(first, AutoModeManager.Mode.CLAIM, "g1");
        assertEquals(AutoModeManager.Mode.OFF, AutoModeManager.INSTANCE.modeOf(first));
        assertEquals(AutoModeManager.Mode.UNCLAIM, AutoModeManager.INSTANCE.modeOf(second));

        // 收尾把状态切回关闭
        AutoModeManager.INSTANCE.toggle(second, AutoModeManager.Mode.UNCLAIM);
        assertEquals(AutoModeManager.Mode.OFF, AutoModeManager.INSTANCE.stateOf(second).mode());
    }

}
