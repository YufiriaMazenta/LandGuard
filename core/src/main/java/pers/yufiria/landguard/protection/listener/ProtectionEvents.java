package pers.yufiria.landguard.protection.listener;

import crypticlib.BukkitPlayer;
import crypticlib.CommonPlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.protection.BlockPoint;
import pers.yufiria.landguard.protection.CheckResult;
import pers.yufiria.landguard.protection.ProtectionFlag;
import pers.yufiria.landguard.protection.ProtectionQueries;
import pers.yufiria.landguard.util.LangUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听器共用判定：只做坐标转换、只读查询与统一拒绝反馈，不做任何方块收集/加载。
 */
public final class ProtectionEvents {

    private ProtectionEvents() {
    }

    private static final Map<UUID, Long> playerLastSendMsgTime = new ConcurrentHashMap<>();

    static boolean allowed(Player player, Block block, ProtectionFlag flag) {

        CheckResult result = ProtectionQueries.queryBehavior(
            player, block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4, flag);
        if (result.denied()) {
            long current = System.currentTimeMillis();
            if (current - playerLastSendMsgTime.getOrDefault(player.getUniqueId(), 0L) < 1000) {
                return result.allowed();
            }
            LangUtils.sendActionBar(BukkitPlayer.byPlayer(player), Languages.PROTECTION_DENIED);
            playerLastSendMsgTime.put(player.getUniqueId(), current);
        }
        return result.allowed();
    }

    static BlockPoint point(Block block) {
        return new BlockPoint(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

}
