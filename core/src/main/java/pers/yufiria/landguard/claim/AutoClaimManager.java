package pers.yufiria.landguard.claim;

import crypticlib.CommonPlayer;
import crypticlib.CrypticLibBukkit;
import crypticlib.listener.EventListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.CommandUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行走自动认领：玩家进入新区块时自动按规则尝试认领站立区块。
 * 失败反馈按原因节流（同一原因 3 秒内最多一次），避免穿越他人领地时刷屏。
 */
@EventListener
public enum AutoClaimManager implements Listener {

    INSTANCE;

    private static final long FAILURE_THROTTLE_MILLIS = 3000L;

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> lastFailureNotice = new ConcurrentHashMap<>();

    public boolean toggle(UUID uuid) {
        boolean now;
        if (enabled.contains(uuid)) {
            enabled.remove(uuid);
            now = false;
        } else {
            enabled.add(uuid);
            now = true;
        }
        return now;
    }

    public boolean isEnabled(UUID uuid) {
        return enabled.contains(uuid);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!enabled.contains(uuid)) {
            return;
        }
        if (sameChunk(event)) {
            return;
        }
        attemptClaim(CommandUtils.commonPlayer(player), event.getTo().getChunk().getX(), event.getTo().getChunk().getZ());
    }

    private boolean sameChunk(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
            && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4;
    }

    private void attemptClaim(CommonPlayer player, int chunkX, int chunkZ) {
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.uniqueId().toString());
        List<ChunkLoc> targets = List.of(ChunkLoc.of(bukkitPlayer.getWorld().getUID(), chunkX, chunkZ));
        ClaimService.INSTANCE.claim(owner, bukkitPlayer.getWorld().getUID(), targets, player.name(), false)
            .whenComplete((result, throwable) -> {
                if (throwable != null || result == null) {
                    return;
                }
                CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                    if (!bukkitPlayer.isOnline()) {
                        return;
                    }
                    if (result.success()) {
                        ClaimBoundaryVisualizer.show(bukkitPlayer, targets);
                        ClaimMessages.claimSuccess(player, result);
                    } else if (shouldNotify(player.uniqueId())) {
                        ClaimMessages.failure(player, result.failureReason());
                    }
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
