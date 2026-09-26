package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.CrypticLibPlugin;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.lifecycle.LifecyclePhase;
import crypticlib.lifecycle.LifecycleSchedule;
import crypticlib.lifecycle.LifecycleTask;
import crypticlib.lifecycle.LifecycleTaskConfig;
import crypticlib.perm.PermInfo;
import crypticlib.scheduler.CrypticLibRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.LifecycleOrder;
import pers.yufiria.landguard.LandGuard;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@LifecycleTaskConfig(schedules = @LifecycleSchedule(
    phase = LifecyclePhase.RELOAD,
    priority = LifecycleOrder.LAST,
    isAsync = true
))
public final class ReloadCommand extends CommandNode implements LifecycleTask {

    public static final ReloadCommand INSTANCE = new ReloadCommand();
    private volatile @Nullable UUID reloadSenderUuid;
    private final AtomicBoolean reloading = new AtomicBoolean(false);
    private @Nullable CrypticLibRunnable reloadTimeoutCallback = null;

    private ReloadCommand() {
        super(CommandInfo.builder("reload").permission(new PermInfo("landguard.command.reload")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, List<String> args) {
        if (reloading.get()) {
            LangUtils.sendLang(invoker, Languages.COMMAND_RELOAD_RELOADING);
            return;
        }
        try {
            reloading.set(true);
            reloadSenderUuid = invoker.uniqueId();
            LangUtils.sendLang(invoker, Languages.COMMAND_RELOAD_RELOADING);
            LandGuard.instance().reloadPlugin();
            //通过一个延迟任务，确保就算插件重载报错了也能在1分钟的超时时间后正确恢复不在重载的状态
            reloadTimeoutCallback = new CrypticLibRunnable() {
                @Override
                public void run() {
                    reloading.set(false);
                    reloadSenderUuid = null;
                }
            };
            reloadTimeoutCallback.asyncLater(1200);
        } catch (Exception e) {
            e.printStackTrace();
            LangUtils.sendLang(invoker, Languages.COMMAND_RELOAD_EXCEPTION);
        }
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

    @Override
    public void onLifecycle(CrypticLibPlugin crypticLibPlugin, LifecyclePhase lifecyclePhase) {
        UUID senderUuid = reloadSenderUuid;
        //如果正确重载了，取消超时任务
        if (reloadTimeoutCallback != null) {
            reloadTimeoutCallback.cancel();
            reloadTimeoutCallback = null;
        }
        reloadSenderUuid = null;
        reloading.set(false);
        if (senderUuid == null || senderUuid.equals(Invoker.CONSOLE_UUID)) {
            LangUtils.info(Languages.COMMAND_RELOAD_SUCCESS);
            return;
        }
        // 只通知仍在线的玩家；已离线则不补发
        CommonPlayer.fromUuid(senderUuid).ifPresent(player ->
            LangUtils.sendLang(player, Languages.COMMAND_RELOAD_SUCCESS));
    }

    public boolean isReloading() {
        return reloading.get();
    }

}
