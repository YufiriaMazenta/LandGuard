package pers.yufiria.landguard.protection;

import org.jetbrains.annotations.NotNull;

/**
 * 判定结论。allowed 为最终布尔值；defaultUsed 标明是否未命中任何覆盖、取全局默认。
 */
public record CheckResult(
    @NotNull CheckContext context,
    @NotNull ProtectionFlag flag,
    boolean allowed,
    boolean defaultUsed
) {

    public boolean denied() {
        return !allowed;
    }

}
