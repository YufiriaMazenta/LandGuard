package pers.yufiria.landguard.owner;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * 领地所有者实体。
 * 实现可以是单个玩家、插件内置用户组，或第三方插件提供的任意组织。
 * 实现者应保证成员解析尽量轻量（保护判定热路径会高频调用），自行做好缓存，
 * 并在成员变化时通过注册中心发出失效通知。
 */
public interface ClaimOwner {

    /**
     * 所有者类型，必须与提供方的 {@link ClaimOwnerProvider#type()} 一致。
     */
    @NotNull
    OwnerType type();

    /**
     * 类型内唯一标识（玩家 UUID 字符串、组 ID 等）。
     */
    @NotNull
    String identifier();

    /**
     * 用于界面与日志展示的名称。
     */
    @NotNull
    Component displayName();

    /**
     * 当前成员 UUID 快照。仅用于展示/枚举；权限判定请走 {@link #roleOf(UUID)}。
     */
    @NotNull
    Set<UUID> members();

    /**
     * 解析某玩家在此所有者实体中的角色标识；不是成员返回 null。
     * 角色标识对应 {@link Roles} 内置值或用户组自定义角色。
     */
    @Nullable
    String roleOf(UUID player);

    /**
     * 成员资格判断。
     */
    default boolean isMember(UUID player) {
        return roleOf(player) != null;
    }

}
