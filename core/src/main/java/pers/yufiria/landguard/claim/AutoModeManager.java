package pers.yufiria.landguard.claim;

import crypticlib.CommonPlayer;
import crypticlib.CrypticLibBukkit;
import crypticlib.listener.EventListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.CommandUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行走自动模式：玩家进入新区块时按当前模式自动认领或自动放弃站立区块。
 * 认领与放弃互斥（同一玩家同一时刻只处于一种模式），失败反馈按玩家节流（3 秒内最多一次），
 * 避免穿越他人领地时刷屏。
 */
@EventListener
public enum AutoModeManager implements Listener {

    INSTANCE;

    /** 行走自动模式：关闭 / 进入新区块自动认领 / 进入新区块自动放弃本人领地。 */
    public enum Mode {
        OFF, CLAIM, UNCLAIM
    }

    private static final long FAILURE_THROTTLE_MILLIS = 3000L;

    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastFailureNotice = new ConcurrentHashMap<>();

    /**
     * 切换自动模式：已是该模式则关闭，否则切到该模式（另一种模式随之关闭）。
     */
    public Mode toggle(UUID uuid, Mode mode) {
        Mode current = modes.getOrDefault(uuid, Mode.OFF);
        Mode next = current == mode ? Mode.OFF : mode;
        if (next == Mode.OFF) {
            modes.remove(uuid);
        } else {
            modes.put(uuid, next);
        }
        return next;
    }

    public Mode modeOf(UUID uuid) {
        return modes.getOrDefault(uuid, Mode.OFF);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Mode mode = modes.get(player.getUniqueId());
        if (mode == null || sameChunk(event)) {
            return;
        }
        ChunkLoc target = ChunkLoc.of(
            player.getWorld().getUID(),
            event.getTo().getBlockX() >> 4,
            event.getTo().getBlockZ() >> 4
        );
        CommonPlayer commonPlayer = CommandUtils.commonPlayer(player);
        switch (mode) {
            case CLAIM -> attemptClaim(commonPlayer, target);
            case UNCLAIM -> attemptUnclaim(commonPlayer, target);
            default -> {
            }
        }
    }

    private boolean sameChunk(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
            && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4;
    }

    private void attemptClaim(CommonPlayer player, ChunkLoc target) {
        // 已被占用的区块（含本人领地）静默跳过：不写库、不提示
        if (DataStore.INSTANCE.snapshot().claimIdByChunk().containsKey(target)) {
            return;
        }
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.uniqueId().toString());
        List<ChunkLoc> targets = List.of(target);
        ClaimService.INSTANCE.claim(owner, target.worldUuid(), targets, player.name(), false)
            .whenComplete((result, throwable) -> {
                if (throwable != null || result == null) {
                    return;
                }
                CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                    if (!bukkitPlayer.isOnline()) {
                        return;
                    }
                    if (result.success()) {
                        if (result.affectedChunks() == 0) {
                            return;
                        }
                        ClaimBoundaryVisualizer.show(bukkitPlayer, targets);
                        ClaimMessages.claimSuccess(player, result);
                    } else if (result.failureReason() != ClaimFailureReason.OVERLAP
                        && shouldNotify(player.uniqueId())) {
                        ClaimMessages.failure(player, result.failureReason());
                    }
                });
            });
    }

    private void attemptUnclaim(CommonPlayer player, ChunkLoc target) {
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        // 授权交给 ClaimService：野外与他人领地返回失败，这里静默跳过不提示
        ClaimService.INSTANCE.unclaimOwnedBy(player.uniqueId(), target)
            .whenComplete((result, throwable) -> {
                if (throwable != null || result == null || !result.success()) {
                    return;
                }
                CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                    if (!bukkitPlayer.isOnline()) {
                        return;
                    }
                    ClaimMessages.unclaimSuccess(player, result);
                });
            });
    }

    private boolean shouldNotify(UUID uuid) {
        long now = System.currentTimeMillis();
        Long previous = lastFailureNotice.get(uuid);
        if (previous != null && now - previous < FAILURE_THROTTLE_MILLIS) {
            return false;
        }
        lastFailureNotice.put(uuid, now);
        return true;
    }

}