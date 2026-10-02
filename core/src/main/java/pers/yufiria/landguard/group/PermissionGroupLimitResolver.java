package pers.yufiria.landguard.group;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 按权限节点解析组织数量上限（生产环境实现）：
 * <ul>
 *   <li>{@code landguard.group.join_group_limit.N}：最多加入 N 个组织（含自己拥有的）；</li>
 *   <li>{@code landguard.group.own_group_limit.N}：最多拥有 N 个组织（组内身份为领袖身份）。</li>
 * </ul>
 * 同时授予多个 N 时取最大者；未授予任何对应节点按 0（默认禁止）。
 * 权限以在线玩家的「有效权限」为准（含权限插件的继承结果），因此需要玩家在线；
 * 离线玩家无法可靠枚举继承权限，同样按 0 处理。
 */
public enum PermissionGroupLimitResolver implements GroupLimitResolver {

    INSTANCE;

    public static final String JOIN_LIMIT_PREFIX = "landguard.group.join_group_limit.";
    public static final String OWN_LIMIT_PREFIX = "landguard.group.own_group_limit.";

    @Override
    public int joinLimit(@NotNull UUID player) {
        return limit(player, JOIN_LIMIT_PREFIX);
    }

    @Override
    public int ownLimit(@NotNull UUID player) {
        return limit(player, OWN_LIMIT_PREFIX);
    }

    private static int limit(@NotNull UUID player, @NotNull String prefix) {
        Player online = onlinePlayer(player);
        if (online == null) {
            return 0;
        }
        int max = NO_LIMIT;
        for (PermissionAttachmentInfo info : online.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission();
            if (node.length() <= prefix.length() || !node.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            int value = parseCount(node.substring(prefix.length()));
            if (value >= 0) {
                max = Math.max(max, value);
            }
        }
        return max < 0 ? 0 : max;
    }

    /** 后缀必须是 0 或正整数；其余（通配符、非法数字、负数）视为未匹配。 */
    private static int parseCount(@NotNull String suffix) {
        try {
            int value = Integer.parseInt(suffix.trim());
            return value >= 0 ? value : -1;
        } catch (NumberFormatException notACount) {
            return -1;
        }
    }

    private static @Nullable Player onlinePlayer(@NotNull UUID player) {
        try {
            return Bukkit.getPlayer(player);
        } catch (Throwable platformUnavailable) {
            // 无 Bukkit 运行环境（如脱离平台的调用）：按无法判定处理
            return null;
        }
    }

}