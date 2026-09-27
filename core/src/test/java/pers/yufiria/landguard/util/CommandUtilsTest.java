package pers.yufiria.landguard.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 剥标志工具 {@link CommandUtils#withoutValueFlag}：标志可出现在任意位置时先做位置解析。
 */
public class CommandUtilsTest {

    @Test
    void stripsFlagAndItsValueKeepingOrder() {
        assertEquals(List.of("radius", "3"),
            CommandUtils.withoutValueFlag(List.of("radius", "3", "--group", "g"), "--group"));
        assertEquals(List.of("radius", "2"),
            CommandUtils.withoutValueFlag(List.of("--group", "g", "radius", "2"), "--group"));
    }

    @Test
    void trailingBareFlagIsDropped() {
        assertEquals(List.of(), CommandUtils.withoutValueFlag(List.of("--group"), "--group"));
        assertEquals(List.of("radius", "3"),
            CommandUtils.withoutValueFlag(List.of("radius", "3", "--group"), "--group"));
    }

    @Test
    void repeatedFlagsAreAllRemoved() {
        assertEquals(List.of("radius", "3"),
            CommandUtils.withoutValueFlag(List.of("--group", "a", "radius", "3", "--group", "b"), "--group"));
    }

    @Test
    void flagMatchIsCaseInsensitive() {
        assertEquals(List.of("radius", "3"),
            CommandUtils.withoutValueFlag(List.of("radius", "3", "--GROUP", "g"), "--group"));
    }

    @Test
    void noFlagReturnsInputUnchanged() {
        List<String> args = List.of("radius", "3");
        assertEquals(args, CommandUtils.withoutValueFlag(args, "--group"));
    }

}
