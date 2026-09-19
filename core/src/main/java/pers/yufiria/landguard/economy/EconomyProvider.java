package pers.yufiria.landguard.economy;

import java.util.UUID;

/**
 * 经济提供方抽象（FR-7）。core 不依赖任何具体经济插件；
 * hook 模块负责把 Vault Economy 适配成本接口（也可存在测试用内存实现）。
 * 实现应保证余额操作的线程可见性（LandGuard 可能在 DB 写线程查询余额）。
 */
public interface EconomyProvider {

    /** 提供方名称（如 "Vault"），用于日志与反馈 */
    String name();

    /** 当前经济服务是否真正可用 */
    boolean available();

    /** 账户是否存在 */
    boolean hasAccount(UUID player);

    double balance(UUID player);

    /**
     * 存款（向账户加钱）。失败不得部分入账。
     */
    boolean deposit(UUID player, double amount);

    /**
     * 取款（从账户扣钱）；余额不足或失败返回 false，且不得部分扣款。
     */
    boolean withdraw(UUID player, double amount);

    /** 金额展示格式 */
    String format(double amount);

}
