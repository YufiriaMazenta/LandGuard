package pers.yufiria.landguard.owner.builtin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.OwnerType;
import pers.yufiria.landguard.owner.Roles;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 内置玩家所有者：每个玩家自身即一个所有者实体，角色恒为 owner。
 */
public record PlayerClaimOwner(@NotNull UUID uuid) implements ClaimOwner {

    @Override
    public @NotNull OwnerType type() {
        return new OwnerType(BuiltinOwnerTypes.PLAYER);
    }

    @Override
    public @NotNull String identifier() {
        return uuid.toString();
    }

    @Override
    public @NotNull Component displayName() {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        return Component.text(name != null ? name : uuid.toString());
    }

    @Override
    public @NotNull Set<UUID> members() {
        return Set.of(uuid);
    }

    @Override
    public String roleOf(UUID player) {
        return uuid.equals(player) ? Roles.OWNER : null;
    }

    @Override
    public boolean isMember(UUID player) {
        return uuid.equals(player);
    }

    /**
     * 纯文本名称，供日志与持久化使用，不触发任何网络查询。
     */
    public String plainName() {
        return PlainTextComponentSerializer.plainText().serialize(displayName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerClaimOwner other)) return false;
        return uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(BuiltinOwnerTypes.PLAYER, uuid);
    }

}
