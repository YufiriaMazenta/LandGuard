package pers.yufiria.landguard.owner;

/**
 * LandGuard 内置的所有者类型键。
 */
public final class BuiltinOwnerTypes {

    /** 单个玩家 */
    public static final String PLAYER = "player";
    /** 内置用户组（Task 7 提供实现） */
    public static final String GROUP = "group";
    /** 虚拟服务器实体（管理领地，Task 11 提供实现） */
    public static final String SERVER = "server";

    private BuiltinOwnerTypes() {
    }

}
