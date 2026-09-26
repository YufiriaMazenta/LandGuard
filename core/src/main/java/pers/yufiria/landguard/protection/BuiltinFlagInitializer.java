package pers.yufiria.landguard.protection;

import crypticlib.CrypticLibPlugin;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import pers.yufiria.landguard.LifecycleOrder;

/**
 * 幂等注册内置 flag。同步优先任务，确保任何保护判定与 DataStore 装载前注册表就绪。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, priority = LifecycleOrder.REGISTRY),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, priority = LifecycleOrder.REGISTRY)
    }
)
public enum BuiltinFlagInitializer implements LifecycleTask {

    INSTANCE;

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase phase) {
        BuiltinFlags.registerAll();
    }

}
