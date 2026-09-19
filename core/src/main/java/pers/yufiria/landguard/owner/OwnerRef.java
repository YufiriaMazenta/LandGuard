package pers.yufiria.landguard.owner;

import org.jetbrains.annotations.NotNull;

/**
 * 领地所有权的持久化引用：(所有者类型, 类型内唯一ID)。
 * 不持有成员名单——成员资格一律由对应 {@link ClaimOwnerProvider} 实时解析。
 */
public record OwnerRef(@NotNull String typeKey, @NotNull String identifier) {

    public OwnerRef {
        if (typeKey == null || typeKey.isBlank()) {
            throw new IllegalArgumentException("owner typeKey cannot be blank");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("owner identifier cannot be blank");
        }
    }

    public static OwnerRef of(@NotNull String typeKey, @NotNull String identifier) {
        return new OwnerRef(typeKey, identifier);
    }

    public static OwnerRef of(@NotNull OwnerType type, @NotNull String identifier) {
        return new OwnerRef(type.key(), identifier);
    }

    public OwnerType type() {
        return new OwnerType(typeKey);
    }

}
