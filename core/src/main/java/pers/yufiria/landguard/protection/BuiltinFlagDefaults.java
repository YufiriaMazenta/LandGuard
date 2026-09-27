package pers.yufiria.landguard.protection;

import pers.yufiria.landguard.identity.IdentityRegistry;

/**
 * 全局默认 flag 矩阵：未覆盖项的唯一回退来源。
 * 自然类与角色无关，取 flag 自身的 defaultValue；
 * 行为类按身份（{@link IdentityRegistry}）的配置给出默认值，未在配置中定义的身份按「成员」身份生效。
 */
public final class BuiltinFlagDefaults {

    private BuiltinFlagDefaults() {
    }

    /**
     * 某身份对某行为 flag 的全局默认值。
     */
    public static boolean behaviorDefault(String roleId, ProtectionFlag flag) {
        return IdentityRegistry.INSTANCE.behaviorAllows(roleId, flag.id());
    }

    /**
     * 自然类环境默认值（与角色无关）。
     */
    public static boolean naturalDefault(ProtectionFlag flag) {
        return flag.defaultValue();
    }

}
