package pers.yufiria.landguard;

import crypticlib.*;
import crypticlib.chat.BukkitMsgSender;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import pers.yufiria.landguard.LifecycleOrder;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.config.PluginConfigs;
import pers.yufiria.landguard.exception.UnsupportedVersionException;
import pers.yufiria.landguard.metrics.MetricsManager;
import pers.yufiria.landguard.util.LangUtils;

@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, priority = LifecycleOrder.PLUGIN)
    }
)
public final class LandGuard extends BukkitPlugin implements LifecycleTask {

    private static LandGuard INSTANCE;

    public LandGuard() {
        INSTANCE = this;
    }

    @Override
    public void whenLoad() {
        CrypticLib.debug = PluginConfigs.DEBUG.value();
        CrypticLib.info("&7Server Type: " + CrypticLibBukkit.serverAdapter().type() + ", Version: " + MinecraftVersion.current().version());
        if (MinecraftVersion.current().before(MinecraftVersion.V1_20)) {
            BukkitMsgSender.INSTANCE.info("&cUnsupported Version");
            throw new UnsupportedVersionException();
        }
    }

    @Override
    public void whenEnable() {
    }

    @Override
    public void whenReload() {
        CrypticLib.debug = PluginConfigs.DEBUG.value();
    }

    public static LandGuard instance() {
        return INSTANCE;
    }

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase lifeCycle) {
        CrypticLibBukkit.scheduler().sync(() -> {
            MetricsManager.INSTANCE.init(this);
            LangUtils.info(Languages.LOAD_FINISH);
        });
    }

}
