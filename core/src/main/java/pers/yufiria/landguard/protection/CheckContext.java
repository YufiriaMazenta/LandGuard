package pers.yufiria.landguard.protection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.identity.IdentityRegistry;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.OwnerRef;

/**
 * 一次判定的静态上下文：区块 → 领地 → 所有者实体 → 成员角色。
 * 角色是成员资格的解析结果，flag 是后续按角色/环境读取的能力；二者在此阶段即分离。
 */
public record CheckContext(
    @Nullable String claimId,
    @Nullable OwnerRef ownerRef,
    @Nullable ClaimOwner owner,
    @Nullable String memberRole
) {

    public static CheckContext freeWilderness() {
        return new CheckContext(null, null, null, null);
    }

    public static CheckContext ofClaimed(@NotNull String claimId,
                                        @NotNull OwnerRef ownerRef,
                                        @Nullable ClaimOwner owner,
                                        @Nullable String memberRole) {
        return new CheckContext(claimId, ownerRef, owner, memberRole);
    }

    /** 区块不属于任何领地（野外）。 */
    public boolean wilderness() {
        return claimId == null;
    }

    /**
     * 领地存在但所有者实体无法解析（提供方缺失/组织解散）：成员资格不可证明。
     */
    public boolean orphaned() {
        return claimId != null && owner == null;
    }

    /**
     * 行为类判定使用的有效角色：非成员一律按非成员默认身份（配置驱动，默认 visitor）；
     * 野外/孤儿无角色语义（调用方先排除）。
     */
    public @NotNull String effectiveBehaviorRole() {
        return memberRole != null ? memberRole : IdentityRegistry.INSTANCE.defaultIdentityId();
    }

}
