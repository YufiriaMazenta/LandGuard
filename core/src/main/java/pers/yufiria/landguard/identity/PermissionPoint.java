package pers.yufiria.landguard.identity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 组管理功能权限点（与领地内的行为 flag 是两类不同维度）：
 * 行为 flag 描述「在世界里能做什么」，本枚举描述「对用户组与组领地能做什么」。
 * key 即配置文件里 {@code identities.<id>.permissions} 的取值。
 */
public enum PermissionPoint {

    /** 邀请成员加入 */
    GROUP_INVITE("group.invite"),
    /** 踢出成员（层级另由身份 priority 判定） */
    GROUP_KICK("group.kick"),
    /** 修改用户组展示名 */
    GROUP_RENAME("group.rename"),
    /** 解散用户组 */
    GROUP_DISBAND("group.disband"),
    /** 转让领袖 */
    GROUP_TRANSFER("group.transfer_leadership"),
    /** 指派成员身份 */
    GROUP_ASSIGN("group.assign_identity"),
    /** 把自己的个人领地赠予用户组 */
    GROUP_GIVE_CLAIM("group.give_claim"),
    /** 以用户组身份扩张组领地（新增区块） */
    CLAIM_EXPAND("claim.expand"),
    /** 放弃组领地 */
    CLAIM_UNCLAIM("claim.unclaim"),
    /** 重命名领地 */
    CLAIM_RENAME("claim.rename"),
    /** 转让领地 */
    CLAIM_TRANSFER("claim.transfer"),
    /** 修改领地的 flag 覆盖 */
    CLAIM_FLAGS("claim.flags");

    private final String key;

    PermissionPoint(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** 配置里的权限串 → 权限点；未知串返回 null，由调用方告警并忽略。 */
    public static @Nullable PermissionPoint byKey(@NotNull String key) {
        for (PermissionPoint point : values()) {
            if (point.key.equalsIgnoreCase(key)) {
                return point;
            }
        }
        return null;
    }

}
