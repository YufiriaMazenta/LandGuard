package pers.yufiria.landguard.owner;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AC-1 / TR-3.1：第三方组织只需实现 SPI 即可作为领地所有者；
 * 不同成员按角色解析出不同身份，LandGuard core 不感知测试提供方类型。
 */
public class ClaimOwnerRegistrySpiTest {

    static final OwnerType GUILD_TYPE = new OwnerType("test:guild");
    static final UUID ALICE = UUID.randomUUID();
    static final UUID BOB = UUID.randomUUID();
    static final UUID CAROL = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        ClaimOwnerRegistry.INSTANCE.unregister(GUILD_TYPE);
    }

    @Test
    void thirdPartyProviderResolvesMembersAndRoles() {
        FakeGuild guild = new FakeGuild("g-1", "Builders", Map.of(ALICE, "manager", BOB, "member"));
        FakeGuildProvider provider = new FakeGuildProvider(List.of(guild));
        ClaimOwnerRegistry.INSTANCE.register(provider);

        ClaimOwner resolved = ClaimOwnerRegistry.INSTANCE.resolve(OwnerRef.of(GUILD_TYPE, "g-1"));
        assertSame(guild, resolved);
        assertEquals("manager", resolved.roleOf(ALICE));
        assertEquals("member", resolved.roleOf(BOB));
        assertNull(resolved.roleOf(CAROL), "outsider has no role");
        assertTrue(resolved.isMember(ALICE));
        assertFalse(resolved.isMember(CAROL));
        assertEquals(2, resolved.members().size());

        List<ClaimOwner> aliceOwners = new ArrayList<>(ClaimOwnerRegistry.INSTANCE.ownersOf(ALICE));
        assertEquals(1, aliceOwners.size());
        assertSame(guild, aliceOwners.get(0));
        assertTrue(ClaimOwnerRegistry.INSTANCE.ownersOf(CAROL).isEmpty());

        assertNull(ClaimOwnerRegistry.INSTANCE.resolve("test:guild", "missing"));
        assertNull(ClaimOwnerRegistry.INSTANCE.resolve("unregistered:type", "g-1"));
    }

    @Test
    void replacementAndInvalidationCallbacks() {
        FakeGuild guild = new FakeGuild("g-2", "Knights", Map.of(ALICE, "owner"));
        ClaimOwnerRegistry.INSTANCE.register(new FakeGuildProvider(List.of(guild)));
        FakeGuildProvider replacement = new FakeGuildProvider(List.of(guild));
        ClaimOwnerRegistry.INSTANCE.register(replacement);
        assertSame(replacement, ClaimOwnerRegistry.INSTANCE.provider("test:guild"));

        List<String> events = new ArrayList<>();
        OwnerRef ref = OwnerRef.of(GUILD_TYPE, "g-2");
        MembershipInvalidationListener listener = new MembershipInvalidationListener() {
            @Override
            public void onMembershipChanged(OwnerRef owner) {
                events.add("changed:" + owner.identifier());
            }

            @Override
            public void onOwnerRemoved(OwnerRef owner) {
                events.add("removed:" + owner.identifier());
            }

            @Override
            public void onFullInvalidation() {
                events.add("full");
            }
        };
        ClaimOwnerRegistry.INSTANCE.addListener(listener);
        ClaimOwnerRegistry.INSTANCE.notifyMembershipChanged(ref);
        ClaimOwnerRegistry.INSTANCE.notifyOwnerRemoved(ref);
        ClaimOwnerRegistry.INSTANCE.fireFullInvalidation();
        ClaimOwnerRegistry.INSTANCE.removeListener(listener);
        ClaimOwnerRegistry.INSTANCE.notifyMembershipChanged(ref);

        assertEquals(List.of("changed:g-2", "removed:g-2", "full"), events);
    }

    static final class FakeGuildProvider implements ClaimOwnerProvider {

        private final Map<String, FakeGuild> guilds = new HashMap<>();

        FakeGuildProvider(Collection<FakeGuild> guilds) {
            guilds.forEach(g -> this.guilds.put(g.identifier(), g));
        }

        @Override
        public @NotNull OwnerType type() {
            return GUILD_TYPE;
        }

        @Override
        public @Nullable ClaimOwner getOwner(@NotNull String identifier) {
            return guilds.get(identifier);
        }

        @Override
        public @NotNull Collection<ClaimOwner> ownersOf(@NotNull UUID player) {
            List<ClaimOwner> result = new ArrayList<>();
            for (FakeGuild guild : guilds.values()) {
                if (guild.roleOf(player) != null) {
                    result.add(guild);
                }
            }
            return result;
        }

    }

    static final class FakeGuild implements ClaimOwner {

        private final String id;
        private final String name;
        private final Map<UUID, String> roles;

        FakeGuild(String id, String name, Map<UUID, String> roles) {
            this.id = id;
            this.name = name;
            this.roles = new HashMap<>(roles);
        }

        @Override
        public @NotNull OwnerType type() {
            return GUILD_TYPE;
        }

        @Override
        public @NotNull String identifier() {
            return id;
        }

        @Override
        public @NotNull Component displayName() {
            return Component.text(name);
        }

        @Override
        public @NotNull Set<UUID> members() {
            return Set.copyOf(roles.keySet());
        }

        @Override
        public @Nullable String roleOf(UUID player) {
            return roles.get(player);
        }

    }

}
