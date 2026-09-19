package pers.yufiria.landguard.hook.vault;

import crypticlib.CrypticLibPlugin;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import pers.yufiria.landguard.economy.EconomyProvider;
import pers.yufiria.landguard.economy.EconomyService;

import java.util.logging.Logger;

/**
 * Vault 经济接入生命周期任务。
 * 本类刻意不直接引用任何 {@code net.milkbowl.vault} 类型：未安装 Vault 时类扫描器
 * 仍会加载本类，但不会触发 NoClassDefFoundError；真正的适配类
 * {@link VaultEconomyAdapter} 仅在检测到 Vault 服务后反射实例化。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, priority = 20),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, priority = 20),
        @LifecycleSchedule(phase = LifecyclePhase.DISABLE)
    }
)
public enum VaultHook implements LifecycleTask {

    INSTANCE;

    private static final String VAULT_PLUGIN = "Vault";
    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";
    private static final String ADAPTER_CLASS = "pers.yufiria.landguard.hook.vault.VaultEconomyAdapter";

    private volatile boolean hooked;

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase phase) {
        switch (phase) {
            case ACTIVE, RELOAD -> tryHook(Logger.getLogger("LandGuard"));
            case DISABLE -> {
                hooked = false;
                EconomyService.INSTANCE.unhook();
            }
            default -> {
            }
        }
    }

    private void tryHook(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin(VAULT_PLUGIN) == null) {
            logger.info("[LandGuard] 未检测到 Vault，经济功能（买卖额度/领地银行）自动禁用，其余功能不受影响");
            return;
        }
        try {
            Class<?> economyClass = Class.forName(ECONOMY_CLASS);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null) {
                logger.info("[LandGuard] 已安装 Vault 但没有任何经济服务提供者，经济功能保持禁用");
                return;
            }
            Object economy = registration.getProvider();
            Class<?> adapterClass = Class.forName(ADAPTER_CLASS);
            EconomyProvider adapter = (EconomyProvider) adapterClass
                .getDeclaredConstructor(economyClass)
                .newInstance(economy);
            EconomyService.INSTANCE.hook(adapter);
            hooked = true;
            logger.info("[LandGuard] 已接入 Vault 经济：" + adapter.name());
        } catch (ClassNotFoundException vaultMissing) {
            logger.info("[LandGuard] Vault API 不存在，经济功能保持禁用");
        } catch (ReflectiveOperationException e) {
            logger.warning("[LandGuard] Vault 经济适配器初始化失败，经济功能保持禁用: " + e.getMessage());
        }
    }

}
