package pers.yufiria.landguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.owner.OwnerRef;

/**
 * 所有者实体成员状态变化的 Bukkit 事件。
 * 由 LandGuard 从 SPI 失效回调桥接触发，第三方插件也可以监听它来感知内置用户组的变化。
 */
public class OwnerMembershipChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Reason {
        /** 成员加入/退出或角色调整 */
        MEMBERSHIP_CHANGED,
        /** 所有者实体被删除（组织解散等） */
        OWNER_REMOVED,
        /** 周期性全量校正 */
        FULL
    }

    private final Reason reason;
    private final @Nullable OwnerRef owner;

    public OwnerMembershipChangedEvent(@NotNull Reason reason, @Nullable OwnerRef owner) {
        this.reason = reason;
        this.owner = owner;
    }

    public @NotNull Reason reason() {
        return reason;
    }

    /**
     * {@link Reason#FULL} 时为 null。
     */
    public @Nullable OwnerRef owner() {
        return owner;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}
