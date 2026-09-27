package pers.yufiria.landguard.identity;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * 一个身份（用户组内的角色）的完整定义。全服统一由配置文件提供，用户组不能自定义。
 *
 * @param id          稳定标识，落库在 lg_group_member.role_id；改动它会让已有 flag 覆盖行失效
 * @param name        展示名（允许色码），不回退语言文件
 * @param priority    层级，决定踢人与指派的可行方向；同值身份互相不可操作
 * @param leader      是否为领袖身份（唯一）：只能经转让领袖获得，不可被指派、不可被踢
 * @param isDefault   是否为非成员默认身份（唯一）：不在组内的玩家按它判定
 * @param permissions 组管理功能权限点的 key 集合
 * @param behaviors   领地内行为 flag 的 id 集合；含 {@link #ALL_BEHAVIORS} 表示全部
 */
public record Identity(
    @NotNull String id,
    @NotNull String name,
    int priority,
    boolean leader,
    boolean isDefault,
    @NotNull Set<String> permissions,
    @NotNull Set<String> behaviors
) {

    /** 行为列表里的通配符：表示全部已注册的行为 flag。 */
    public static final String ALL_BEHAVIORS = "*";

    public boolean has(@NotNull PermissionPoint point) {
        return permissions.contains(point.key());
    }

    public boolean allowsAllBehaviors() {
        return behaviors.contains(ALL_BEHAVIORS);
    }

    /** 该身份是否允许某行为 flag。 */
    public boolean allowsBehavior(@NotNull String flagId) {
        return allowsAllBehaviors() || behaviors.contains(flagId);
    }

}
