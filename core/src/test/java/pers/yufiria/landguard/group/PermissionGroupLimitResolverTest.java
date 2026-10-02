package pers.yufiria.landguard.group;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 权限节点解析：同时授予多个 {@code *_limit.N} 时取最大 N；
 * 未授权、被撤销的负节点、非法后缀与离线玩家一律按 0（默认禁止）。
 */
public class PermissionGroupLimitResolverTest {

    private static final UUID ALICE = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock alice;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("landguard-test");
        alice = new PlayerMock(server, "Alice", ALICE);
        server.addPlayer(alice);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void noPermissionMeansZero() {
        assertEquals(0, PermissionGroupLimitResolver.INSTANCE.joinLimit(ALICE));
        assertEquals(0, PermissionGroupLimitResolver.INSTANCE.ownLimit(ALICE));
    }

    @Test
    void highestGrantedCountWins() {
        alice.addAttachment(plugin, PermissionGroupLimitResolver.JOIN_LIMIT_PREFIX + "1", true);
        alice.addAttachment(plugin, PermissionGroupLimitResolver.JOIN_LIMIT_PREFIX + "3", true);
        alice.addAttachment(plugin, PermissionGroupLimitResolver.JOIN_LIMIT_PREFIX + "2", true);
        alice.addAttachment(plugin, PermissionGroupLimitResolver.OWN_LIMIT_PREFIX + "2", true);
        assertEquals(3, PermissionGroupLimitResolver.INSTANCE.joinLimit(ALICE));
        assertEquals(2, PermissionGroupLimitResolver.INSTANCE.ownLimit(ALICE));
    }

    @Test
    void revokedMalformedAndForeignNodesAreIgnored() {
        alice.addAttachment(plugin, PermissionGroupLimitResolver.JOIN_LIMIT_PREFIX + "9", false);
        alice.addAttachment(plugin, PermissionGroupLimitResolver.JOIN_LIMIT_PREFIX + "abc", true);
        alice.addAttachment(plugin, "landguard.group.other_limit.5", true);
        assertEquals(0, PermissionGroupLimitResolver.INSTANCE.joinLimit(ALICE));
    }

    @Test
    void offlinePlayerMeansZero() {
        UUID offline = UUID.randomUUID();
        assertEquals(0, PermissionGroupLimitResolver.INSTANCE.joinLimit(offline));
        assertEquals(0, PermissionGroupLimitResolver.INSTANCE.ownLimit(offline));
    }

}