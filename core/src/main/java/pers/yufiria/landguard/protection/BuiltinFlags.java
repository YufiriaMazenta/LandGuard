package pers.yufiria.landguard.protection;

/**
 * 内置 flag 标识与注册。
 * 行为类（FR-3.3）：放置/破坏/容器/门/红石/工作台/载具/动物/展示实体/种植/采收/物品拾取丢弃；
 * 自然类（FR-3.4）：PvP/爆炸破坏/火焰蔓延/流体跨界/活塞跨界/怪物生成/怪物破坏/作物踩踏。
 */
public final class BuiltinFlags {

    // ---------------- 行为类 ----------------
    /** 放置方块 */
    public static final ProtectionFlag PLACE = behavior("place");
    /** 破坏方块 */
    public static final ProtectionFlag BREAK = behavior("break");
    /** 访问容器（箱子、熔炉、木桶等） */
    public static final ProtectionFlag CONTAINER = behavior("container");
    /** 门、活板门、栅栏门 */
    public static final ProtectionFlag DOOR = behavior("door");
    /** 红石机关（按钮、拉杆、压力板等） */
    public static final ProtectionFlag REDSTONE = behavior("redstone");
    /** 工作台类方块交互 */
    public static final ProtectionFlag CRAFTING = behavior("crafting");
    /** 载具使用（船、矿车等） */
    public static final ProtectionFlag VEHICLE = behavior("vehicle");
    /** 伤害/喂养动物 */
    public static final ProtectionFlag ANIMAL = behavior("animal");
    /** 画、物品展示框、盔甲架 */
    public static final ProtectionFlag DISPLAY = behavior("display");
    /** 种植 */
    public static final ProtectionFlag PLANTING = behavior("planting");
    /** 采收 */
    public static final ProtectionFlag HARVEST = behavior("harvest");
    /** 物品拾取/丢弃 */
    public static final ProtectionFlag ITEM = behavior("item");
    /** 领地银行取款（FR-7.1，仅 owner/manager 默认允许） */
    public static final ProtectionFlag BANK = behavior("bank");

    // ---------------- 自然类 ----------------
    /** 玩家间战斗 */
    public static final ProtectionFlag PVP = natural("pvp", false);
    /** 爆炸对方块的破坏 */
    public static final ProtectionFlag EXPLOSION = natural("explosion", false);
    /** 火焰蔓延 */
    public static final ProtectionFlag FIRE_SPREAD = natural("fire_spread", false);
    /** 水/岩浆跨界流动 */
    public static final ProtectionFlag FLUID_FLOW = natural("fluid_flow", false);
    /** 活塞跨界推拉 */
    public static final ProtectionFlag PISTON = natural("piston", false);
    /** 怪物自然生成 */
    public static final ProtectionFlag MOB_SPAWN = natural("mob_spawn", true);
    /** 末影人/苦力怕等怪物对方块的改变 */
    public static final ProtectionFlag MOB_GRIEF = natural("mob_grief", false);
    /** 实体踩踏作物 */
    public static final ProtectionFlag TRAMPLE = natural("trample", false);

    private BuiltinFlags() {
    }

    private static ProtectionFlag behavior(String id) {
        // 行为类未知角色（未配置矩阵的自定义角色）默认拒绝
        return new ProtectionFlag(id, FlagCategory.BEHAVIOR, false);
    }

    private static ProtectionFlag natural(String id, boolean defaultValue) {
        return new ProtectionFlag(id, FlagCategory.NATURAL, defaultValue);
    }

    /**
     * 幂等注册全部内置 flag。ACTIVE/RELOAD 生命周期与测试夹具均可直接调用。
     */
    public static void registerAll() {
        FlagRegistry registry = FlagRegistry.INSTANCE;
        registry.register(PLACE);
        registry.register(BREAK);
        registry.register(CONTAINER);
        registry.register(DOOR);
        registry.register(REDSTONE);
        registry.register(CRAFTING);
        registry.register(VEHICLE);
        registry.register(ANIMAL);
        registry.register(DISPLAY);
        registry.register(PLANTING);
        registry.register(HARVEST);
        registry.register(ITEM);
        registry.register(BANK);
        registry.register(PVP);
        registry.register(EXPLOSION);
        registry.register(FIRE_SPREAD);
        registry.register(FLUID_FLOW);
        registry.register(PISTON);
        registry.register(MOB_SPAWN);
        registry.register(MOB_GRIEF);
        registry.register(TRAMPLE);
    }

}
