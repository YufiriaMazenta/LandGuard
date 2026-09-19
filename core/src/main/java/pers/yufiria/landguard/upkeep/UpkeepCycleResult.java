package pers.yufiria.landguard.upkeep;

import java.util.List;

/**
 * 一次回收扫描周期的结果（纯数据，无 Bukkit 依赖）。
 *
 * @param notices           警告与释放通知（顺序即产生顺序，测试据此校验时点）
 * @param fullyCharged      银行足额扣费的领地数
 * @param partiallyCharged  银行余额不足、被清零抵费的领地数
 * @param released          本期自动释放的领地数
 */
public record UpkeepCycleResult(
    List<UpkeepNotice> notices,
    int fullyCharged,
    int partiallyCharged,
    int released
) {

    public static UpkeepCycleResult empty() {
        return new UpkeepCycleResult(List.of(), 0, 0, 0);
    }

    /**
     * 合并两个周期结果（upkeep/不活跃周期 + 孤儿周期），通知按参数顺序保序拼接。
     */
    public static UpkeepCycleResult merge(UpkeepCycleResult first, UpkeepCycleResult second) {
        List<UpkeepNotice> notices = new java.util.ArrayList<>(first.notices());
        notices.addAll(second.notices());
        return new UpkeepCycleResult(
            List.copyOf(notices),
            first.fullyCharged() + second.fullyCharged(),
            first.partiallyCharged() + second.partiallyCharged(),
            first.released() + second.released()
        );
    }

}
