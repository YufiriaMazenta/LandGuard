package pers.yufiria.landguard.owner.builtin.server;

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
 * 内置 {@code server} 类型提供方。服务器实体只有一个，且不属于任何玩家。
 */
public enum ServerClaimOwnerProvider implements ClaimOwnerProvider {

    INSTANCE;

    private static final OwnerType TYPE = new OwnerType(BuiltinOwnerTypes.SERVER);

    @Override
    public @NotNull OwnerType type() {
        return TYPE;
    }

    @Override
    public @Nullable ClaimOwner getOwner(@NotNull String identifier) {
        return ServerClaimOwner.ID.equals(identifier) ? ServerClaimOwner.INSTANCE : null;
    }

    @Override
    public @NotNull Collection<ClaimOwner> ownersOf(@NotNull UUID player) {
        return List.of();
    }

}
