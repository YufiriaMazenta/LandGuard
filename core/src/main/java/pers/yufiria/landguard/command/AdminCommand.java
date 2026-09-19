package pers.yufiria.landguard.command;

import crypticlib.CrypticLibBukkit;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.lang.entry.StringLangEntry;
import crypticlib.perm.PermInfo;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.admin.AdminFailureReason;
import pers.yufiria.landguard.admin.AdminOpResult;
import pers.yufiria.landguard.admin.AdminService;
import pers.yufiria.landguard.admin.OrphanInfo;
import pers.yufiria.landguard.admin.OrphanService;
import pers.yufiria.landguard.claim.ClaimEngine;
import pers.yufiria.landguard.claim.ClaimMessages;
import pers.yufiria.landguard.claim.ClaimService;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.builtin.server.ServerClaimOwner;
import pers.yufiria.landguard.upkeep.UpkeepNotifications;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.ConfigValues;
import pers.yufiria.landguard.util.LangUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /land admin ...}：管理领地创建/删除、强制放弃/转让、信息查看、
 * 孤儿领地列表与处理、手动触发回收（FR-9.1）。
 * 权限节点 {@code landguard.command.admin} 与保护绕过节点 {@code landguard.bypass} 相互独立：
 * 拥有管理命令权限不等于可以无视保护放置/破坏。
 * 所有写操作走服务层（DataStore 单写线程），命令层只做参数解析与反馈。
 */
public final class AdminCommand extends CommandNode {

    public static final AdminCommand INSTANCE = new AdminCommand();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .localizedBy(Locale.getDefault())
        .withZone(ZoneId.systemDefault());

    private AdminCommand() {
        super(CommandInfo.builder("admin").permission(new PermInfo("landguard.command.admin")).build());
    }

    @Override
    public void execute(@NotNull Invoker invoker, List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        Player player = (Player) CommandUtils.invoker2Sender(invoker);
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
            return;
        }
        switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "claim" -> claim(player, args);
            case "unclaim" -> unclaim(player, args);
            case "transfer" -> transfer(player, args);
            case "release" -> release(player, args);
            case "exempt" -> exempt(player, args);
            case "info" -> info(player);
            case "orphans" -> orphans(player);
            case "run" -> run(player);
            default -> LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
        }
    }

    // ================= 管理领地创建/删除 =================

    private void claim(Player player, List<String> args) {
        int radius = parseRadius(player, args);
        if (radius < 0) {
            return;
        }
        List<ChunkLoc> targets = standingTargets(player, radius);
        OwnerRef server = OwnerRef.of(BuiltinOwnerTypes.SERVER, ServerClaimOwner.ID);
        ClaimService.INSTANCE.claim(server, player.getWorld().getUID(), targets, "Admin Claim", true)
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_ADMIN_CLAIM_SUCCESS,
                        Map.of("count", String.valueOf(result.affectedChunks())));
                } else {
                    ClaimMessages.failure(player, result.failureReason());
                }
            }));
    }

    private void unclaim(Player player, List<String> args) {
        int radius = parseRadius(player, args);
        if (radius < 0) {
            return;
        }
        List<ChunkLoc> targets = standingTargets(player, radius);
        ClaimService.INSTANCE.adminUnclaim(targets)
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_ADMIN_UNCLAIM_SUCCESS,
                        Map.of("count", String.valueOf(result.affectedChunks())));
                } else {
                    ClaimMessages.failure(player, result.failureReason());
                }
            }));
    }

    // ================= 强制转让 / 释放 / 豁免 =================

    private void transfer(Player player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
            return;
        }
        UUID target = resolveTarget(args.get(1));
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_FAIL_INVALID_PLAYER);
            return;
        }
        ClaimData standing = standingClaim(player);
        if (standing == null) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_FAIL_CLAIM_NOT_FOUND);
            return;
        }
        AdminService.INSTANCE.transferClaim(standing.getClaimId(), target)
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null) {
                    return;
                }
                sendAdminResult(player, result, Languages.COMMAND_ADMIN_TRANSFER_SUCCESS, Map.of(
                    "player", args.get(1),
                    "chunks", String.valueOf(result.affectedChunks())));
            }));
    }

    private void release(Player player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
            return;
        }
        String claimId = args.get(1);
        AdminService.INSTANCE.releaseClaim(claimId)
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null) {
                    return;
                }
                sendAdminResult(player, result, Languages.COMMAND_ADMIN_RELEASE_SUCCESS, Map.of(
                    "claim", claimId,
                    "chunks", String.valueOf(result.affectedChunks())));
            }));
    }

    private void exempt(Player player, List<String> args) {
        if (args.size() < 2) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
            return;
        }
        Boolean exempt = parseBoolean(args.get(1));
        if (exempt == null) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_FAIL_INVALID_ARGUMENT);
            return;
        }
        ClaimData standing = standingClaim(player);
        if (standing == null) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_FAIL_CLAIM_NOT_FOUND);
            return;
        }
        String claimId = standing.getClaimId();
        AdminService.INSTANCE.setExempt(claimId, exempt)
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null) {
                    return;
                }
                sendAdminResult(player, result, Languages.COMMAND_ADMIN_EXEMPT_SET, Map.of(
                    "exempt", String.valueOf(exempt)));
            }));
    }

    // ================= 信息 / 孤儿列表 =================

    private void info(Player player) {
        ClaimData claim = standingClaim(player);
        if (claim == null) {
            LangUtils.sendLang(player, Languages.COMMAND_INFO_UNCLAIMED);
            return;
        }
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        int chunks = snapshot.chunksByClaim().getOrDefault(claim.getClaimId(), Set.of()).size();
        World world = Bukkit.getWorld(claim.getWorldUuid());
        OwnerRef ownerRef = OwnerRef.of(claim.getOwnerType(), claim.getOwnerId());
        ClaimOwner resolved = ClaimOwnerRegistry.INSTANCE.resolve(ownerRef);
        String ownerName = resolved == null ? claim.getOwnerId()
            : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(resolved.displayName());

        LangUtils.sendLang(player, Languages.COMMAND_INFO_HEADER);
        LangUtils.sendLang(player, Languages.COMMAND_INFO_NAME, Map.of(
            "name", claim.getName() == null ? claim.getClaimId() : claim.getName()));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_OWNER, Map.of(
            "owner", ownerName, "type", claim.getOwnerType()));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_WORLD, Map.of(
            "world", world == null ? claim.getWorldUuid().toString().substring(0, 8) : world.getName()));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_CHUNKS, Map.of("chunks", String.valueOf(chunks)));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_CREATED, Map.of(
            "created", DATE_FORMAT.format(Instant.ofEpochMilli(claim.getCreatedAt()))));
        LangUtils.sendLang(player, Languages.COMMAND_ADMIN_INFO_ADMIN, Map.of(
            "admin", String.valueOf(claim.isAdmin()),
            "claim", claim.getClaimId()));
        LangUtils.sendLang(player, Languages.COMMAND_ADMIN_INFO_EXEMPT, Map.of(
            "exempt", String.valueOf(claim.isUpkeepExempt())));
        LangUtils.sendLang(player, Languages.COMMAND_ADMIN_INFO_ORPHAN, Map.of(
            "orphan", String.valueOf(OrphanService.INSTANCE.isOrphan(claim))));
    }

    private void orphans(Player player) {
        List<OrphanInfo> orphans = OrphanService.INSTANCE.listOrphans();
        if (orphans.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_ORPHANS_EMPTY);
            return;
        }
        LangUtils.sendLang(player, Languages.COMMAND_ADMIN_ORPHANS_HEADER, Map.of(
            "size", String.valueOf(orphans.size())));
        for (OrphanInfo orphan : orphans) {
            long sinceDays = orphan.orphanSince() == 0L ? 0L
                : Math.max(0L, (System.currentTimeMillis() - orphan.orphanSince()) / 1000L / 86400L);
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_ORPHANS_ENTRY, Map.of(
                "claim", orphan.claimId(),
                "name", orphan.claimName() == null ? "?" : orphan.claimName(),
                "type", orphan.ownerType(),
                "owner", orphan.ownerId(),
                "chunks", String.valueOf(orphan.chunks()),
                "days", String.valueOf(sinceDays)));
        }
    }

    // ================= 手动回收 =================

    private void run(Player player) {
        AdminService.INSTANCE.runMaintenance(System.currentTimeMillis())
            .whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
                if (!player.isOnline() || throwable != null) {
                    return;
                }
                UpkeepNotifications.dispatch(result);
                LangUtils.sendLang(player, Languages.COMMAND_ADMIN_RUN_DONE, Map.of(
                    "released", String.valueOf(result.released()),
                    "charged", String.valueOf(result.fullyCharged())));
            }));
    }

    // ================= 工具 =================

    private void sendAdminResult(Player player, AdminOpResult result,
                                 StringLangEntry successEntry,
                                 Map<String, String> formats) {
        if (result.success()) {
            LangUtils.sendLang(player, successEntry, formats);
            return;
        }
        AdminFailureReason reason = result.failureReason();
        var entry = switch (reason == null ? AdminFailureReason.CLAIM_NOT_FOUND : reason) {
            case CLAIM_NOT_FOUND -> Languages.COMMAND_ADMIN_FAIL_CLAIM_NOT_FOUND;
            case INVALID_PLAYER -> Languages.COMMAND_ADMIN_FAIL_INVALID_PLAYER;
            case ALREADY_OWNED -> Languages.COMMAND_ADMIN_FAIL_ALREADY_OWNED;
            case INVALID_ARGUMENT -> Languages.COMMAND_ADMIN_FAIL_INVALID_ARGUMENT;
        };
        LangUtils.sendLang(player, entry);
    }

    private static @Nullable ClaimData standingClaim(Player player) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ChunkLoc standing = ChunkLoc.of(
            player.getWorld().getUID(),
            player.getLocation().getBlockX() >> 4,
            player.getLocation().getBlockZ() >> 4
        );
        String claimId = snapshot.claimIdByChunk().get(standing);
        return claimId == null ? null : snapshot.claimsById().get(claimId);
    }

    private static List<ChunkLoc> standingTargets(Player player, int radius) {
        return ClaimEngine.radiusTargets(
            player.getWorld().getUID(),
            player.getLocation().getBlockX() >> 4,
            player.getLocation().getBlockZ() >> 4,
            radius);
    }

    /**
     * 解析 [radius] 参数：缺省为 1；非法返回 -1（已向玩家反馈）。
     */
    private static int parseRadius(Player player, List<String> args) {
        if (args.size() < 2) {
            return 1;
        }
        int radius;
        try {
            radius = Integer.parseInt(args.get(1));
        } catch (NumberFormatException e) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
            return -1;
        }
        if (radius < 1) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
            return -1;
        }
        int max = ConfigValues.get(ClaimConfigs.MAX_RADIUS);
        if (radius > max) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_TOO_LARGE, Map.of("max", String.valueOf(max)));
            return -1;
        }
        return radius;
    }

    private static @Nullable Boolean parseBoolean(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on") || raw.equals("1")) {
            return Boolean.TRUE;
        }
        if (raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("off") || raw.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static @Nullable UUID resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
