package pers.yufiria.landguard.group;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 玩家可加入 / 可拥有的用户组数上限来源。
 * 领域层只依赖本接口、不感知权限平台：生产环境由 {@link PermissionGroupLimitResolver}
 * 按权限节点解析（启动期经 {@link GroupLimitInitializer} 注入），
 * 无平台环境（单元测试）沿用 {@link #noLimits()}，避免权限能力缺失阻断领域逻辑。
 * <p>
 * 语义：返回「允许持有的总数」而非余量；未授权返回 0（默认禁止）。
 */
public interface GroupLimitResolver {

    /** 不限制。 */
    int NO_LIMIT = -1;

    /** 该玩家可加入的用户组数上限；自己拥有的组织也视为已加入，一并计入。 */
    int joinLimit(@NotNull UUID player);

    /** 该玩家可拥有的用户组数上限（组内身份为领袖身份的组织）。 */
    int ownLimit(@NotNull UUID player);

    /** 不限制的实现：无权限平台时使用。 */
    static @NotNull GroupLimitResolver noLimits() {
        return new GroupLimitResolver() {
            @Override
            public int joinLimit(@NotNull UUID player) {
                return NO_LIMIT;
            }

            @Override
            public int ownLimit(@NotNull UUID player) {
                return NO_LIMIT;
            }
        };
    }

}