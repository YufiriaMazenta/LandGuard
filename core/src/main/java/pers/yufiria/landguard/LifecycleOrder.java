package pers.yufiria.landguard;

/**
 * 生命周期优先级：数值越小越早执行。
 * 顺序约束：建连(-2) → 建表/DAO(-1) → 内存快照(0) → 内置注册表与出厂数据(1) → 插件本体(2)
 * → 经济挂钩(20) → 周期任务(40)；关闭阶段用 {@link #LAST} 让数据源最后断开。
 * 这些常量会内联进 {@code @LifecycleSchedule}，因此改动数值等于改变启动顺序，务必保持语义命名。
 */
public final class LifecycleOrder {

    /** 数据库连接（DataSourceManager）。 */
    public static final int DATABASE_SOURCE = -2;

    /** DAO 与建表迁移（LandDaoManager）。 */
    public static final int DAO = -1;

    /** 内存快照（DataStore）。 */
    public static final int SNAPSHOT = 0;

    /** 内置注册表与出厂数据（FlagRegistry、所有者 provider）。 */
    public static final int REGISTRY = 1;

    /** 插件本体与玩家状态跟踪。 */
    public static final int PLUGIN = 2;

    /** 经济挂钩（VaultHook）。 */
    public static final int ECONOMY_HOOK = 20;

    /** 周期任务（维护费等调度器）。 */
    public static final int PERIODIC = 40;

    /** 关闭阶段最后执行。 */
    public static final int LAST = Integer.MAX_VALUE;

    private LifecycleOrder() {
    }

}