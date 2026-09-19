package pers.yufiria.landguard.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.owner.ClaimOwner;

import java.util.UUID;

/**
 * 保护判定前置事件（Task 6 的监听套件在每次行为判定时触发）。
 * 第三方可在不修改 LandGuard 的情况下对某次行为追加豁免（FORCE_ALLOW）或拒绝（FORCE_DENY）。
 * 未干预时结论为 DEFAULT，判定继续沿「成员资格 → 角色 → flag → 访客默认」链路执行。
 */
public class LandProtectionQueryEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID worldUuid;
    private final int chunkX;
    private final int chunkZ;
    private final String actionKey;
    private final @Nullable ClaimOwner owner;
    private final @Nullable String role;
    private ProtectionDecision decision = ProtectionDecision.DEFAULT;

    public LandProtectionQueryEvent(@NotNull Player player,
                                    @NotNull UUID worldUuid,
                                    int chunkX,
                                    int chunkZ,
                                    @NotNull String actionKey,
                                    @Nullable ClaimOwner owner,
                                    @Nullable String role) {
        this.player = player;
        this.worldUuid = worldUuid;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.actionKey = actionKey;
        this.owner = owner;
        this.role = role;
    }

    public @NotNull Player player() {
        return player;
    }

    public @NotNull UUID worldUuid() {
        return worldUuid;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /**
     * 行为标识，对应 flag 键（Task 5 起为注册表里的 Flag 键）。
     */
    public @NotNull String actionKey() {
        return actionKey;
    }

    /**
     * 区块所属所有者；野外为 null。
     */
    public @Nullable ClaimOwner owner() {
        return owner;
    }

    /**
     * 玩家在该所有者实体中的角色；非成员/野外为 null。
     */
    public @Nullable String role() {
        return role;
    }

    public @NotNull ProtectionDecision decision() {
        return decision;
    }

    public void forceAllow() {
        this.decision = ProtectionDecision.FORCE_ALLOW;
    }

    public void forceDeny() {
        this.decision = ProtectionDecision.FORCE_DENY;
    }

    public void resetDecision() {
        this.decision = ProtectionDecision.DEFAULT;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}
