package pers.yufiria.landguard.owner.builtin.server;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.OwnerType;

import java.util.Set;
import java.util.UUID;

/**
 * 虚拟服务器所有者：管理领地（{@code /land admin claim}）的归属实体。
 * 没有任何成员——任何人都不以成员身份获得权限，管理领地的写操作只能靠显式 bypass 节点。
 */
public final class ServerClaimOwner implements ClaimOwner {

    /** server 类型内的唯一标识 */
    public static final String ID = "server";

    public static final ServerClaimOwner INSTANCE = new ServerClaimOwner();

    private ServerClaimOwner() {
    }

    @Override
    public @NotNull OwnerType type() {
        return new OwnerType(BuiltinOwnerTypes.SERVER);
    }

    @Override
    public @NotNull String identifier() {
        return ID;
    }

    @Override
    public @NotNull Component displayName() {
        return Component.text("Server");
    }

    @Override
    public @NotNull Set<UUID> members() {
        return Set.of();
    }

    @Override
    public String roleOf(UUID player) {
        return null;
    }

    @Override
    public boolean isMember(UUID player) {
        return false;
    }

}
