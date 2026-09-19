package pers.yufiria.landguard.protection;

/**
 * 保护相关 Bukkit 权限节点。bypass 默认不授予任何人（含 OP 之外的管理员），Task 11 的管理命令再开放切换。
 */
public final class ProtectionPermissions {

    /** 持有者绕过全部行为类保护判定 */
    public static final String BYPASS = "landguard.bypass";

    private ProtectionPermissions() {
    }

}
