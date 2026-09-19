package pers.yufiria.landguard.owner.builtin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerProvider;
import pers.yufiria.landguard.owner.OwnerType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 内置 {@code player} 类型提供方。
 * 玩家是自身唯一的成员，不需要任何成员失效事件（成员永远只有自己）。
 */
public enum PlayerClaimOwnerProvider implements ClaimOwnerProvider {

    INSTANCE;

    private static final OwnerType TYPE = new OwnerType(BuiltinOwnerTypes.PLAYER);

    @Override
    public @NotNull OwnerType type() {
        return TYPE;
    }

    @Override
    public @Nullable ClaimOwner getOwner(@NotNull String identifier) {
        UUID uuid = parseUuid(identifier);
        if (uuid == null) {
            return null;
        }
        return new PlayerClaimOwner(uuid);
    }

    @Override
    public @NotNull Collection<ClaimOwner> ownersOf(@NotNull UUID player) {
        return List.of(new PlayerClaimOwner(player));
    }

    /**
     * 玩家 UUID 形式的所有者引用（工具方法，供认领服务使用）。
     */
    public static @NotNull String identifierOf(@NotNull UUID player) {
        return player.toString();
    }

    private static @Nullable UUID parseUuid(String identifier) {
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
