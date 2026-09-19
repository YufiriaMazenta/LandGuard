package pers.yufiria.landguard.hook.vault;

import crypticlib.CrypticLibPlugin;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.economy.EconomyProvider;
import pers.yufiria.landguard.economy.EconomyService;
import pers.yufiria.landguard.util.LangUtils;

import java.util.Map;

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
            case ACTIVE, RELOAD -> tryHook();
            case DISABLE -> {
                hooked = false;
                EconomyService.INSTANCE.unhook();
            }
            default -> {
            }
        }
    }

    private void tryHook() {
        if (Bukkit.getPluginManager().getPlugin(VAULT_PLUGIN) == null) {
            LangUtils.info(Languages.HOOK_VAULT_MISSING);
            return;
        }
        try {
            Class<?> economyClass = Class.forName(ECONOMY_CLASS);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null) {
                LangUtils.info(Languages.HOOK_VAULT_NO_PROVIDER);
                return;
            }
            Object economy = registration.getProvider();
            Class<?> adapterClass = Class.forName(ADAPTER_CLASS);
            EconomyProvider adapter = (EconomyProvider) adapterClass
                .getDeclaredConstructor(economyClass)
                .newInstance(economy);
            EconomyService.INSTANCE.hook(adapter);
            hooked = true;
            LangUtils.info(Languages.HOOK_VAULT_HOOKED, Map.of("<name>", adapter.name()));
        } catch (ClassNotFoundException vaultMissing) {
            LangUtils.info(Languages.HOOK_VAULT_API_MISSING);
        } catch (ReflectiveOperationException e) {
            LangUtils.info(Languages.HOOK_VAULT_ADAPTER_FAILED,
                Map.of("<reason>", String.valueOf(e.getMessage())));
        }
    }

}
