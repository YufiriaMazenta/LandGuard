package pers.yufiria.landguard.owner;

/**
 * 内置角色标识。角色是离散身份，与 flag（能力集合）分离，不可互相推导。
 * 用户组可在这些标识之外定义自定义角色（见 Task 7）。
 */
public final class Roles {

    /** 所有者/领袖：领地的最高身份 */
    public static final String OWNER = "owner";
    /** 管理者：被所有者授予管理权限的成员 */
    public static final String MANAGER = "manager";
    /** 普通成员 */
    public static final String MEMBER = "member";
    /** 访客：非成员的默认身份 */
    public static final String VISITOR = "visitor";

    private Roles() {
    }

}
