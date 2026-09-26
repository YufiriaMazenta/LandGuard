package pers.yufiria.landguard.claim;

import crypticlib.CommonPlayer;
import crypticlib.config.node.impl.bukkit.StringConfig;
import crypticlib.lang.entry.StringLangEntry;
import crypticlib.listener.EventListener;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.ConfigValues;
import pers.yufiria.landguard.util.LangUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家进出领地时的提示：动作栏/聊天消息；进入时另外渲染该领地的粒子边界。
 * 只在“所处领地发生变化”时触发一次，同一领地内跨区块移动不会重复提示。
 * 离开提示只在走进非领地范围时发送，从领地 A 直接穿入领地 B 只提示进入 B。
 * 消息通道与是否渲染边界见 claim.yml 的 visualization 节，玩家侧边界开关见 {@link ClaimBoundaryVisualizer}。
 */
@EventListener
public enum ClaimEnterNotifier implements Listener {

    INSTANCE;

    /** 玩家当前所在领地 ID；不在任何领地内时不留条目。 */
    private final ConcurrentHashMap<UUID, String> currentClaim = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (sameChunk(event)) {
            return;
        }
        check(event.getPlayer(), event.getTo().getChunk().getX(), event.getTo().getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        check(event.getPlayer(), event.getTo().getChunk().getX(), event.getTo().getChunk().getZ());
    }

    /** 登录时同步一次，避免玩家在领地内上线却没有任何提示。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        check(player, player.getChunk().getX(), player.getChunk().getZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        currentClaim.remove(playerUuid);
        ClaimBoundaryVisualizer.clear(playerUuid);
    }

    private boolean sameChunk(PlayerMoveEvent event) {
        return event.getFrom().getWorld() == event.getTo().getWorld()
            && event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
            && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4;
    }

    private void check(Player player, int chunkX, int chunkZ) {
        UUID playerUuid = player.getUniqueId();
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ClaimData claim = snapshot.claimAt(player.getWorld().getUID(), chunkX, chunkZ);
        if (claim == null) {
            // 只在真正走到非领地范围时提示离开；领地 A 直接穿入领地 B 不提示
            String leftClaimId = currentClaim.remove(playerUuid);
            if (leftClaimId != null) {
                notifyExit(player, leftClaimId, snapshot);
            }
            return;
        }
        String claimId = claim.getClaimId();
        if (claimId.equals(currentClaim.put(playerUuid, claimId))) {
            return;
        }
        notifyEnter(player, claim, snapshot);
    }

    private void notifyEnter(Player player, ClaimData claim, DataSnapshot snapshot) {
        CommonPlayer commonPlayer = CommandUtils.commonPlayer(player);
        sendNotify(commonPlayer, claim);
        if (ConfigValues.get(ClaimConfigs.ENTER_BOUNDARY)) {
            List<ChunkLoc> chunks =
                List.copyOf(snapshot.chunksByClaim().getOrDefault(claim.getClaimId(), Set.of()));
            ClaimBoundaryVisualizer.show(player, chunks);
        }
    }

    /** 离开领地只发提示，不渲染边界。 */
    private void notifyExit(Player player, String claimId, DataSnapshot snapshot) {
        if (!ConfigValues.get(ClaimConfigs.EXIT_NOTIFY_ENABLED)) {
            return;
        }
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim == null) {
            return;
        }
        send(CommandUtils.commonPlayer(player), claim,
            Languages.CLAIM_EXIT_NOTIFY, Languages.CLAIM_EXIT_ACTIONBAR, ClaimConfigs.EXIT_NOTIFY_CHANNEL);
    }

    private void sendNotify(CommonPlayer player, ClaimData claim) {
        if (!ConfigValues.get(ClaimConfigs.ENTER_NOTIFY_ENABLED)) {
            return;
        }
        send(player, claim,
            Languages.CLAIM_ENTER_NOTIFY, Languages.CLAIM_ENTER_ACTIONBAR, ClaimConfigs.ENTER_NOTIFY_CHANNEL);
    }

    /**
     * 按配置的通道发送领地进出提示，无法识别的通道值按 actionbar 处理。
     */
    private void send(CommonPlayer player, ClaimData claim, StringLangEntry chatEntry,
                      StringLangEntry actionbarEntry, StringConfig channel) {
        Locale locale = player.locale();
        String claimName = claim.getName() == null ? claim.getClaimId() : claim.getName();
        OwnerRef ownerRef = OwnerRef.of(claim.getOwnerType(), claim.getOwnerId());
        ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(ownerRef);
        String ownerName = owner == null
            ? claim.getOwnerId()
            : PlainTextComponentSerializer.plainText().serialize(owner.displayName());
        Map<String, String> params = Map.of(
            "<name>", claimName,
            "<owner>", ownerName,
            "<type>", LangUtils.ownerTypeLabel(locale, claim.getOwnerType())
        );
        switch (ConfigValues.get(channel).toLowerCase(Locale.ROOT)) {
            case "chat" -> LangUtils.sendLang(player, chatEntry, params);
            case "both" -> {
                LangUtils.sendLang(player, chatEntry, params);
                LangUtils.sendActionBar(player, actionbarEntry, params);
            }
            case "none" -> {
                // 显式关闭提示，只保留边界渲染
            }
            default -> LangUtils.sendActionBar(player, actionbarEntry, params);
        }
    }

}
