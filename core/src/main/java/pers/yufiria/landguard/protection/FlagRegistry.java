package pers.yufiria.landguard.protection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * flag 注册表（纯 Java）。内置 flag 与第三方注册的 flag 地位对等；
 * 监听套件只认 flag 标识，不硬编码行为集合。
 */
public enum FlagRegistry {

    INSTANCE;

    private final Map<String, ProtectionFlag> flags = new ConcurrentHashMap<>();

    /**
     * 注册（或替换）同标识 flag。重复注册用于插件重载安全。
     */
    public void register(@NotNull ProtectionFlag flag) {
        flags.put(flag.id(), flag);
    }

    public void unregister(@NotNull String id) {
        flags.remove(id);
    }

    public @Nullable ProtectionFlag get(@NotNull String id) {
        return flags.get(id);
    }

    public boolean isRegistered(@NotNull String id) {
        return flags.containsKey(id);
    }

    /**
     * 全部已注册 flag，插入顺序视图（内置先于第三方）。
     */
    public @NotNull Collection<ProtectionFlag> all() {
        return Collections.unmodifiableCollection(new LinkedHashMap<>(flags).values());
    }

}
