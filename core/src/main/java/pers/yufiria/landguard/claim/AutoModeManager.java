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
import pers.yufiria.landguard.owner.builtin.server.ServerClaimOwner;
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

    /** 行走自动模式：关闭 / 进入新区块自动认领 / 进入新区块自动放弃（本人的个人领地，或本人所在组内拥有放弃权限的身份所持的组领地）；ADMIN_* 为管理员变体，仅对拥有对应管理命令权限的玩家生效。 */
    public enum Mode {
        OFF, CLAIM, UNCLAIM, ADMIN_CLAIM, ADMIN_UNCLAIM
    }

    /**
     * 行走自动状态：模式 + 认领时使用的身份（{@code groupId == null} 表示以本人身份认领）。
     * 记住身份是为了让「以组身份认领」在整段行走期间保持不变。
     */
    public record AutoState(Mode mode, @Nullable String groupId) {

        public static final AutoState OFF = new AutoState(Mode.OFF, null);
    }

    private static final long FAILURE_THROTTLE_MILLIS = 3000L;

    /** 与 {@code /land admin claim}、{@code /land admin unclaim} 节点同名的权限：行走期间每次行动前复核，收回后自动关闭管理模式。 */
    private static final String ADMIN_CLAIM_PERMISSION = "landguard.command.admin.claim";
    private static final String ADMIN_UNCLAIM_PERMISSION = "landguard.command.admin.unclaim";

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
            case ADMIN_CLAIM -> attemptAdminClaim(commonPlayer, target);
            case ADMIN_UNCLAIM -> attemptAdminUnclaim(commonPlayer, target);
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

    /** 管理自动认领：以 server 虚拟所有者创建/扩容管理领地（admin 标记，跳过相邻与额度限制）。 */
    private void attemptAdminClaim(CommonPlayer player, ChunkLoc target) {
        // 已被占用的区块（含本人领地）静默跳过：不写库、不提示
        if (DataStore.INSTANCE.snapshot().claimIdByChunk().containsKey(target)) {
            return;
        }
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        if (!bukkitPlayer.hasPermission(ADMIN_CLAIM_PERMISSION)) {
            states.remove(player.uniqueId());
            if (shouldNotify(player.uniqueId())) {
                LangUtils.sendLang(player, Languages.COMMAND_NO_PERM);
            }
            return;
        }
        List<ChunkLoc> targets = List.of(target);
        AsyncReply.toPlayer(bukkitPlayer, ClaimService.INSTANCE.claim(
            OwnerRef.of(BuiltinOwnerTypes.SERVER, ServerClaimOwner.ID), target.worldUuid(), targets,
            ClaimService.ADMIN_CLAIM_NAME, true), result -> {
            if (result.success()) {
                LangUtils.sendLang(player, Languages.COMMAND_ADMIN_CLAIM_SUCCESS, Map.of(
                    "<count>", String.valueOf(result.affectedChunks())));
            }
        });
    }

    /** 管理自动强制放弃：不校验归属，进入已认领区块即移除该区块（野外静默跳过）。 */
    private void attemptAdminUnclaim(CommonPlayer player, ChunkLoc target) {
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        if (!bukkitPlayer.hasPermission(ADMIN_UNCLAIM_PERMISSION)) {
            states.remove(player.uniqueId());
            if (shouldNotify(player.uniqueId())) {
                LangUtils.sendLang(player, Languages.COMMAND_NO_PERM);
            }
            return;
        }
        AsyncReply.toPlayer(bukkitPlayer, ClaimService.INSTANCE.adminUnclaim(List.of(target)), result -> {
            if (result.success()) {
                LangUtils.sendLang(player, Languages.COMMAND_ADMIN_UNCLAIM_SUCCESS, Map.of(
                    "<count>", String.valueOf(result.affectedChunks())));
            }
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
