package pers.yufiria.landguard.group;

import crypticlib.CrypticLibPlugin;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import pers.yufiria.landguard.LifecycleOrder;

/**
 * 启动/重载时把基于权限的组织数量上限解析器注入 {@link GroupService}。
 * 权限节点与在线玩家的有效权限绑定，只有 Bukkit 运行环境才能解析，
 * 因此领域层默认（{@link GroupLimitResolver#noLimits()}）不限制，由本任务切换为生产语义。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, priority = LifecycleOrder.REGISTRY),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, priority = LifecycleOrder.REGISTRY)
    }
)
public enum GroupLimitInitializer implements LifecycleTask {

    INSTANCE;

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase phase) {
        GroupService.INSTANCE.setLimitResolver(PermissionGroupLimitResolver.INSTANCE);
    }

}