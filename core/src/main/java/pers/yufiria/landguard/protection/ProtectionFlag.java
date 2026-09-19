package pers.yufiria.landguard.protection;

import org.jetbrains.annotations.NotNull;

/**
 * 一个受保护行为/自然过程的标识。flag 只表达能力开关，绝不承载身份语义。
 *
 * @param id           稳定标识，落库键（如 place、pvp）；第三方注册建议自带命名空间前缀
 * @param category     行为类/自然类
 * @param defaultValue 全局兜底默认值：行为类表示「无角色矩阵条目时」的取值（未知自定义角色默认拒绝），
 *                     自然类即环境默认（是否允许该过程）
 */
public record ProtectionFlag(
    @NotNull String id,
    @NotNull FlagCategory category,
    boolean defaultValue
) {

    public boolean isBehavior() {
        return category == FlagCategory.BEHAVIOR;
    }

    public boolean isNatural() {
        return category == FlagCategory.NATURAL;
    }

}
