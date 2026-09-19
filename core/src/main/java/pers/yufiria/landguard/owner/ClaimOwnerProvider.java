package pers.yufiria.landguard.owner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * 所有者提供方 SPI。
 * 第三方插件实现本接口并调用 {@link ClaimOwnerRegistry#register(ClaimOwnerProvider)} 即可
 * 把自己的组织系统接入领地所有权，无需修改 LandGuard。
 * 成员发生变化时应调用 {@link ClaimOwnerRegistry#notifyMembershipChanged(OwnerRef)}；
 * 组织被删除时应调用 {@link ClaimOwnerRegistry#notifyOwnerRemoved(OwnerRef)}。
 */
public interface ClaimOwnerProvider {

    /**
     * 本提供方处理的所有者类型，全局唯一。
     */
    @NotNull
    OwnerType type();

    /**
     * 按类型内 ID 解析所有者；实体不存在或本插件尚未加载完成时返回 null。
     */
    @Nullable
    ClaimOwner getOwner(@NotNull String identifier);

    /**
     * 查询某玩家作为成员所属的全部所有者实体（用于玩家的领地列表）。
     * 玩家不属于任何实体时返回空集合。
     */
    @NotNull
    Collection<ClaimOwner> ownersOf(java.util.UUID player);

}
