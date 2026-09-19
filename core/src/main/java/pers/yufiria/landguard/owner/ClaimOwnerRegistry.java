package pers.yufiria.landguard.owner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 所有者 SPI 注册中心（纯 Java，不依赖 Bukkit，可在测试中直接使用）。
 * 提供方注册一次即可；内置提供方与第三方提供方地位完全对等，保护逻辑不特化任何类型。
 */
public enum ClaimOwnerRegistry {

    INSTANCE;

    private final Map<String, ClaimOwnerProvider> providers = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<MembershipInvalidationListener> listeners = new CopyOnWriteArraySet<>();

    /**
     * 注册（或替换）一个提供方。重复注册同类型会替换旧实例，插件重载安全。
     */
    public void register(@NotNull ClaimOwnerProvider provider) {
        providers.put(provider.type().key(), provider);
    }

    public void unregister(@NotNull OwnerType type) {
        providers.remove(type.key());
    }

    public @Nullable ClaimOwnerProvider provider(@NotNull String typeKey) {
        return providers.get(typeKey);
    }

    public @Nullable ClaimOwner resolve(@NotNull OwnerRef ref) {
        ClaimOwnerProvider provider = providers.get(ref.typeKey());
        return provider == null ? null : provider.getOwner(ref.identifier());
    }

    public @Nullable ClaimOwner resolve(@NotNull String typeKey, @NotNull String identifier) {
        return resolve(OwnerRef.of(typeKey, identifier));
    }

    /**
     * 某提供方是否已注册且该所有者实体当前可解析。
     */
    public boolean exists(@NotNull OwnerRef ref) {
        return resolve(ref) != null;
    }

    public @NotNull Collection<ClaimOwner> ownersOf(@NotNull UUID player) {
        List<ClaimOwner> result = new ArrayList<>();
        for (ClaimOwnerProvider provider : providers.values()) {
            result.addAll(provider.ownersOf(player));
        }
        return result;
    }

    public void addListener(@NotNull MembershipInvalidationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NotNull MembershipInvalidationListener listener) {
        listeners.remove(listener);
    }

    /**
     * 提供方在成员加入/退出/角色调整后调用；受影响在线玩家的权限须立即变化。
     */
    public void notifyMembershipChanged(@NotNull OwnerRef owner) {
        for (MembershipInvalidationListener listener : listeners) {
            listener.onMembershipChanged(owner);
        }
    }

    /**
     * 提供方在所有者实体被删除后调用；其名下领地将进入孤儿流程。
     */
    public void notifyOwnerRemoved(@NotNull OwnerRef owner) {
        for (MembershipInvalidationListener listener : listeners) {
            listener.onOwnerRemoved(owner);
        }
    }

    /**
     * 周期性全量校正兜底（防止提供方漏发事件）。
     */
    public void fireFullInvalidation() {
        for (MembershipInvalidationListener listener : listeners) {
            listener.onFullInvalidation();
        }
    }

}
