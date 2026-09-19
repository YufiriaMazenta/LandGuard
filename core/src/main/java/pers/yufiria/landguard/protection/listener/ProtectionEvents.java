package pers.yufiria.landguard.protection.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import pers.yufiria.landguard.protection.BlockPoint;
import pers.yufiria.landguard.protection.CheckResult;
import pers.yufiria.landguard.protection.ProtectionFlag;
import pers.yufiria.landguard.protection.ProtectionQueries;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.util.LangUtils;

/**
 * 监听器共用判定：只做坐标转换、只读查询与统一拒绝反馈，不做任何方块收集/加载。
 */
final class ProtectionEvents {

    private ProtectionEvents() {
    }

    static boolean allowed(Player player, Block block, ProtectionFlag flag) {
        CheckResult result = ProtectionQueries.queryBehavior(
            player, block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4, flag);
        if (result.denied()) {
            LangUtils.sendLang(player, Languages.PROTECTION_DENIED);
        }
        return result.allowed();
    }

    static BlockPoint point(Block block) {
        return new BlockPoint(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

}
