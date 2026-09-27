package pers.yufiria.landguard.claim;

import crypticlib.CommonPlayer;
import crypticlib.listener.EventListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.PermissionPoint;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

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

    /** 行走自动模式：关闭 / 进入新区块自动认领 / 进入新区块自动放弃（本人的个人领地，或本人所在组内拥有放弃权限的身份所持的组领地，默认领袖与管理者）。 */
    public enum Mode {
        OFF, CLAIM, UNCLAIM
    }

    /**
     * 行走自动状态：模式 + 认领时使用的身份（{@code groupId == null} 表示以本人身份认领）。
     * 记住身份是为了让「以组身份认领」在整段行走期间保持不变。
     */
    public record AutoState(Mode mode, @Nullable String groupId) {

        public static final AutoState OFF = new AutoState(Mode.OFF, null);
    }

    private static final long FAILURE_THROTTLE_MILLIS = 3000L;

    private final Map<UUID, AutoState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastFailureNotice = new ConcurrentHashMap<>();

    /** 兼容重载：等价于以本人身份（{@code groupId == null}）切换。 */
    public AutoState toggle(UUID uuid, Mode mode) {
        return toggle(uuid, mode, null);
    }

    /**
     * 切换自动状态：当前状态与目标组合（模式 + 身份）相同则关闭，否则切到该组合。
     */
    public AutoState toggle(UUID uuid, Mode mode, @Nullable String groupId) {
        AutoState desired = mode == Mode.OFF ? AutoState.OFF : new AutoState(mode, groupId);
        AutoState next = stateOf(uuid).equals(desired) ? AutoState.OFF : desired;
        if (next.equals(AutoState.OFF)) {
            states.remove(uuid);
        } else {
            states.put(uuid, next);
        }
        return next;
    }

    /** 该玩家的行走自动状态；未开启时返回 {@link AutoState#OFF}。 */
    public AutoState stateOf(UUID uuid) {
        return states.getOrDefault(uuid, AutoState.OFF);
    }

    /** 兼容包装：只取模式。 */
    public Mode modeOf(UUID uuid) {
        return stateOf(uuid).mode();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        AutoState state = states.get(player.getUniqueId());
        if (state == null || state.mode() == Mode.OFF || sameChunk(event)) {
            return;
        }
        ChunkLoc target = ChunkLoc.of(
            player.getWorld().getUID(),
            event.getTo().getBlockX() >> 4,
            event.getTo().getBlockZ() >> 4
        );
        CommonPlayer commonPlayer = CommandUtils.commonPlayer(player);
        switch (state.mode()) {
            case CLAIM -> attemptClaim(commonPlayer, target, state.groupId());
            case UNCLAIM -> attemptUnclaim(commonPlayer, target);
            default -> {
            }
        }
    }

    private boolean sameChunk(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
            && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4;
    }

    private void attemptClaim(CommonPlayer player, ChunkLoc target, @Nullable String groupId) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        // 已被占用的区块（含本人领地）静默跳过：不写库、不提示
        if (snapshot.claimIdByChunk().containsKey(target)) {
            return;
        }
        OwnerRef owner;
        String defaultName;
        if (groupId == null) {
            owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.uniqueId().toString());
            defaultName = player.name();
        } else {
            GroupData group = GroupService.findById(snapshot, groupId);
            // 组已不存在，或玩家已失去该组的扩张权限：清空状态并节流提示，避免每次移动刷屏
            if (group == null || !IdentityPermissions.has(snapshot, group.getGroupId(), player.uniqueId(),
                PermissionPoint.CLAIM_EXPAND)) {
                states.remove(player.uniqueId());
                if (shouldNotify(player.uniqueId())) {
                    LangUtils.sendLang(player, Languages.COMMAND_CLAIM_AUTO_GROUP_INVALID);
                }
                return;
            }
            owner = OwnerRef.of(BuiltinOwnerTypes.GROUP, group.getGroupId());
            defaultName = group.getName();
        }
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        List<ChunkLoc> targets = List.of(target);
        AsyncReply.toPlayer(bukkitPlayer, ClaimService.INSTANCE.claim(owner, target.worldUuid(), targets, defaultName, false, player.uniqueId()), result -> {
            if (result.success()) {
                ClaimBoundaryVisualizer.show(bukkitPlayer, targets);
                ClaimMessages.claimSuccess(player, result);
            } else if (result.failureReason() != ClaimFailureReason.OVERLAP
                && shouldNotify(player.uniqueId())) {
                ClaimMessages.failure(player, result.failureReason());
            }
        });
    }

    private void attemptUnclaim(CommonPlayer player, ChunkLoc target) {
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        // 授权交给 ClaimService：野外与他人领地返回失败，这里静默跳过不提示
        AsyncReply.toPlayer(bukkitPlayer, ClaimService.INSTANCE.unclaimOwnedBy(player.uniqueId(), target), result -> {
            if (!result.success()) {
                return;
            }
            ClaimMessages.unclaimSuccess(player, result);
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
