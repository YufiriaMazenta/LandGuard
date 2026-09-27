package pers.yufiria.landguard.identity;

import crypticlib.CrypticLib;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.Roles;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组内身份与权限的唯一判定入口。所有组务与组领地操作的授权都经这里，
 * 不再各自比较角色字符串（历史实现散落 16 处）。
 * 纯函数：只读传入的快照，可在主线程与 GUI 中直接调用。
 */
public final class IdentityPermissions {

    /** 已告警过的未注册身份 id，避免每次判定都刷屏。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private IdentityPermissions() {
    }

    /** 是否为该组成员（成员行存在，或是领袖）。 */
    public static boolean isMember(@NotNull DataSnapshot snapshot, @NotNull String groupId, @NotNull UUID player) {
        return memberIdentityIdOf(snapshot, groupId, player) != null;
    }

    /**
     * 成员在组内的身份 id（未注册的原始值也照实返回，便于排查）。
     * 返回 null 表示「不是成员」—— 成员资格与身份解析必须区分开。
     */
    public static @Nullable String memberIdentityIdOf(@NotNull DataSnapshot snapshot, @NotNull String groupId,
                                                      @NotNull UUID player) {
        String stored = snapshot.groupMembers().getOrDefault(groupId, Map.of()).get(player);
        if (stored != null) {
            return stored;
        }
        return isLeader(snapshot, groupId, player) ? IdentityRegistry.INSTANCE.leaderIdentityId() : null;
    }

    /**
     * 实际生效的身份：领袖 → 领袖身份；成员 → 其身份（未注册回落 member）；非成员 → 非成员默认身份。
     * 永不返回 null，便于行为判定取得一个维度键。
     */
    public static @NotNull Identity identityOf(@NotNull DataSnapshot snapshot, @NotNull String groupId,
                                               @NotNull UUID player) {
        String raw = memberIdentityIdOf(snapshot, groupId, player);
        if (raw == null) {
            return IdentityRegistry.INSTANCE.defaultIdentity();
        }
        if (!IdentityRegistry.INSTANCE.isRegistered(raw)) {
            warnUnknown(raw);
        }
        return IdentityRegistry.INSTANCE.resolve(raw);
    }

    /** 是否拥有某组管理权限点。 */
    public static boolean has(@NotNull DataSnapshot snapshot, @NotNull String groupId, @NotNull UUID player,
                              @NotNull PermissionPoint point) {
        return identityOf(snapshot, groupId, player).has(point);
    }

    /** actor 的身份层级是否严格高于 target。 */
    public static boolean outranks(@NotNull DataSnapshot snapshot, @NotNull String groupId,
                                   @NotNull UUID actor, @NotNull UUID target) {
        return identityOf(snapshot, groupId, actor).priority() > identityOf(snapshot, groupId, target).priority();
    }

    public static boolean isLeader(@NotNull DataSnapshot snapshot, @NotNull String groupId, @NotNull UUID player) {
        GroupData group = snapshot.groups().get(groupId);
        return group != null && player.equals(group.getLeaderUuid());
    }

    /**
     * 对某块领地执行某权限点操作是否被允许。
     * 个人领地 = 所有者本人；用户组领地 = 组内权限点；服务器/管理领地 = 不允许；
     * 第三方所有者类型回退历史语义「其角色为领袖身份」。
     */
    public static boolean canActOnClaim(@NotNull DataSnapshot snapshot, @Nullable ClaimData claim,
                                        @Nullable UUID actor, @NotNull PermissionPoint point) {
        if (claim == null || actor == null) {
            return false;
        }
        String ownerType = claim.getOwnerType();
        if (BuiltinOwnerTypes.PLAYER.equals(ownerType)) {
            return claim.getOwnerId().equals(actor.toString());
        }
        if (BuiltinOwnerTypes.GROUP.equals(ownerType)) {
            return has(snapshot, claim.getOwnerId(), actor, point);
        }
        if (BuiltinOwnerTypes.SERVER.equals(ownerType)) {
            return false;
        }
        // 第三方所有者 SPI：沿用「角色即领袖身份」的旧判定
        ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(OwnerRef.of(ownerType, claim.getOwnerId()));
        return owner != null && IdentityRegistry.INSTANCE.leaderIdentityId().equals(owner.roleOf(actor));
    }

    /** 组长的身份 id 是否就是领袖身份（配置可能把 id 改掉，故不直接比较 {@link Roles#OWNER}）。 */
    public static boolean isLeaderIdentity(@Nullable String identityId) {
        return IdentityRegistry.INSTANCE.leaderIdentityId().equals(identityId);
    }

    private static void warnUnknown(String raw) {
        if (WARNED.add(raw)) {
            try {
                CrypticLib.info("&e成员身份 " + raw + " 未在 identities.yml 中定义，暂按 "
                    + Roles.MEMBER + " 身份生效");
            } catch (Throwable platformUnavailable) {
                // 无平台环境（单测）忽略
            }
        }
    }

}
