package pers.yufiria.landguard.owner.builtin;

import crypticlib.CrypticLibBukkit;
import crypticlib.CrypticLibPlugin;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import crypticlib.scheduler.CrypticLibRunnable;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.LifecycleOrder;
import pers.yufiria.landguard.api.event.EventCaller;
import pers.yufiria.landguard.api.event.OwnerMembershipChangedEvent;
import pers.yufiria.landguard.config.PluginConfigs;
import pers.yufiria.landguard.owner.*;
import pers.yufiria.landguard.owner.builtin.group.GroupClaimOwnerProvider;
import pers.yufiria.landguard.owner.builtin.server.ServerClaimOwnerProvider;

/**
 * 注册内置 player 提供方；把 SPI 失效回调桥接为 Bukkit 事件；启动周期全量校正。
 * ACTIVE 同步任务，先于业务读路径就绪。
 */
@LifecycleTaskConfig(
    schedules = {
        @LifecycleSchedule(phase = LifecyclePhase.ACTIVE, priority = LifecycleOrder.REGISTRY),
        @LifecycleSchedule(phase = LifecyclePhase.RELOAD, priority = LifecycleOrder.REGISTRY),
        @LifecycleSchedule(phase = LifecyclePhase.DISABLE)
    }
)
public enum BuiltinOwnerInitializer implements LifecycleTask, MembershipInvalidationListener {

    INSTANCE;

    private @Nullable CrypticLibRunnable revalidateTask;

    @Override
    public void onLifecycle(CrypticLibPlugin plugin, LifecyclePhase phase) {
        switch (phase) {
            case ACTIVE, RELOAD -> {
                ClaimOwnerRegistry.INSTANCE.register(PlayerClaimOwnerProvider.INSTANCE);
                ClaimOwnerRegistry.INSTANCE.register(GroupClaimOwnerProvider.INSTANCE);
                ClaimOwnerRegistry.INSTANCE.register(ServerClaimOwnerProvider.INSTANCE);
                ClaimOwnerRegistry.INSTANCE.addListener(this);
                restartRevalidateTask();
            }
            case DISABLE -> {
                stopRevalidateTask();
                ClaimOwnerRegistry.INSTANCE.removeListener(this);
                ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.PLAYER));
                ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.GROUP));
                ClaimOwnerRegistry.INSTANCE.unregister(new OwnerType(BuiltinOwnerTypes.SERVER));
            }
        }
    }

    @Override
    public void onMembershipChanged(OwnerRef owner) {
        // SPI 通知可能来自 DB 写线程，Bukkit 事件必须回到主线程派发
        CrypticLibBukkit.scheduler().sync(() ->
            EventCaller.call(new OwnerMembershipChangedEvent(OwnerMembershipChangedEvent.Reason.MEMBERSHIP_CHANGED, owner)));
    }

    @Override
    public void onOwnerRemoved(OwnerRef owner) {
        CrypticLibBukkit.scheduler().sync(() ->
            EventCaller.call(new OwnerMembershipChangedEvent(OwnerMembershipChangedEvent.Reason.OWNER_REMOVED, owner)));
    }

    @Override
    public void onFullInvalidation() {
        CrypticLibBukkit.scheduler().sync(() ->
            EventCaller.call(new OwnerMembershipChangedEvent(OwnerMembershipChangedEvent.Reason.FULL, null)));
    }

    private void restartRevalidateTask() {
        stopRevalidateTask();
        int interval = PluginConfigs.OWNER_MEMBERSHIP_REVALIDATE_INTERVAL_TICKS.value();
        if (interval <= 0) {
            return;
        }
        revalidateTask = new CrypticLibRunnable() {
            @Override
            public void run() {
                ClaimOwnerRegistry.INSTANCE.fireFullInvalidation();
            }
        };
        revalidateTask.syncTimer(interval, interval);
    }

    private void stopRevalidateTask() {
        if (revalidateTask != null) {
            revalidateTask.cancel();
            revalidateTask = null;
        }
    }

}
