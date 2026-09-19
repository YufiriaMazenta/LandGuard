package pers.yufiria.landguard.protection;

import pers.yufiria.landguard.owner.Roles;

import java.util.Map;
import java.util.Set;

/**
 * 全局默认 flag 矩阵：未覆盖项的唯一回退来源。
 * 自然类与角色无关，取 flag 自身的 defaultValue；
 * 行为类按内置角色给出默认矩阵，矩阵未覆盖的角色（如用户组自定义角色）回退 flag.defaultValue（默认拒绝）。
 */
public final class BuiltinFlagDefaults {

    /** owner：全部行为默认允许 */
    private static final Set<String> OWNER_ALLOWED = Set.of(
        BuiltinFlags.PLACE.id(), BuiltinFlags.BREAK.id(), BuiltinFlags.CONTAINER.id(),
        BuiltinFlags.DOOR.id(), BuiltinFlags.REDSTONE.id(), BuiltinFlags.CRAFTING.id(),
        BuiltinFlags.VEHICLE.id(), BuiltinFlags.ANIMAL.id(), BuiltinFlags.DISPLAY.id(),
        BuiltinFlags.PLANTING.id(), BuiltinFlags.HARVEST.id(), BuiltinFlags.ITEM.id(),
        BuiltinFlags.BANK.id()
    );

    /** manager：全部行为默认允许 */
    private static final Set<String> MANAGER_ALLOWED = OWNER_ALLOWED;

    /** member：容器默认拒绝，其余日常行为允许 */
    private static final Set<String> MEMBER_ALLOWED = Set.of(
        BuiltinFlags.PLACE.id(), BuiltinFlags.BREAK.id(), BuiltinFlags.DOOR.id(),
        BuiltinFlags.REDSTONE.id(), BuiltinFlags.CRAFTING.id(), BuiltinFlags.VEHICLE.id(),
        BuiltinFlags.ANIMAL.id(), BuiltinFlags.DISPLAY.id(), BuiltinFlags.PLANTING.id(),
        BuiltinFlags.HARVEST.id(), BuiltinFlags.ITEM.id()
    );

    /** visitor：仅工作台类交互默认允许 */
    private static final Set<String> VISITOR_ALLOWED = Set.of(BuiltinFlags.CRAFTING.id());

    private static final Map<String, Set<String>> MATRIX = Map.of(
        Roles.OWNER, OWNER_ALLOWED,
        Roles.MANAGER, MANAGER_ALLOWED,
        Roles.MEMBER, MEMBER_ALLOWED,
        Roles.VISITOR, VISITOR_ALLOWED
    );

    private BuiltinFlagDefaults() {
    }

    /**
     * 某角色对某行为 flag 的全局默认值。
     */
    public static boolean behaviorDefault(String roleId, ProtectionFlag flag) {
        Set<String> allowed = MATRIX.get(roleId);
        return allowed != null && allowed.contains(flag.id());
    }

    /**
     * 自然类环境默认值（与角色无关）。
     */
    public static boolean naturalDefault(ProtectionFlag flag) {
        return flag.defaultValue();
    }

}
