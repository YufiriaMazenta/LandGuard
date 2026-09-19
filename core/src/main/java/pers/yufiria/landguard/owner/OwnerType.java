package pers.yufiria.landguard.owner;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 所有者类型标识。
 * 内置类型：{@code player}、{@code group}、{@code server}；
 * 第三方插件应使用命名空间形式（如 {@code myguild:guild}）避免冲突。
 */
public record OwnerType(@NotNull String key) {

    public OwnerType {
        Objects.requireNonNull(key, "owner type key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("owner type key cannot be blank");
        }
    }

    public static OwnerType of(String key) {
        return new OwnerType(key);
    }

    @Override
    public String toString() {
        return key;
    }

}
