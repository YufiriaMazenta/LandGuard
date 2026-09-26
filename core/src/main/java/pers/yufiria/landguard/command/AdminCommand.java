package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.CrypticLibBukkit;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.command.annotation.Subcommand;
import crypticlib.lang.entry.StringLangEntry;
import crypticlib.perm.PermInfo;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.admin.*;
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
import java.util.*;
import java.util.function.BiConsumer;

/**
 * {@code /land admin ...}：管理领地创建/删除、强制放弃/转让、信息查看、
 * 孤儿领地列表与处理、手动触发回收（FR-9.1）。
 * 子命令交由框架节点树分派（{@code @Subcommand}）：每个动作独立权限节点、独立补全，
 * 参数列表已去掉动作名（{@code args.get(0)} 即该动作的第一个参数）。
 * 权限节点 {@code landguard.command.admin.*} 与保护绕过节点 {@code landguard.bypass} 相互独立：
 * 拥有管理命令权限不等于可以无视保护放置/破坏。
 * 所有写操作走服务层（DataStore 单写线程），命令层只做参数解析与反馈。
 */
public final class AdminCommand extends CommandNode {

    public static final AdminCommand INSTANCE = new AdminCommand();
    private static final String PERM_PREFIX = "landguard.command.admin.";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .localizedBy(Locale.getDefault())
        .withZone(ZoneId.systemDefault());

    private AdminCommand() {
        super(CommandInfo.builder("admin").permission(new PermInfo("landguard.command.admin")).build());
    }

    // ================= 子命令节点 =================

    @Subcommand
    CommandNode claim = action("claim", this::claim);
    @Subcommand
    CommandNode unclaim = action("unclaim", this::unclaim);
    @Subcommand
    CommandNode transfer = action("transfer", this::transfer, players());
    @Subcommand
    CommandNode release = action("release", this::release);
    @Subcommand
    CommandNode exempt = action("exempt", this::exempt, booleans());
    @Subcommand
    CommandNode rename = action("rename", this::rename);
    @Subcommand
    CommandNode info = action("info", (player, args) -> info(player));
    @Subcommand
    CommandNode orphans = action("orphans", (player, args) -> orphans(player));
    @Subcommand
    CommandNode run = action("run", (player, args) -> run(player));

    /** 无子命令或子命令名未命中时，框架回落到本节点：输出用法。 */
    @Override
    public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        LangUtils.sendLang(invoker, Languages.COMMAND_ADMIN_USAGE);
    }

    private static CommandNode action(String name, BiConsumer<CommonPlayer, List<String>> handler) {
        return new PlayerOnlyCommand(PERM_PREFIX + name, name, handler);
    }

    private static CommandNode action(String name, BiConsumer<CommonPlayer, List<String>> handler,
                                      PlayerOnlyCommand.TabCompleter completer) {
        return new PlayerOnlyCommand(PERM_PREFIX + name, name, handler, completer);
    }

    // ================= 参数补全 =================

    /** 第一参数为在线玩家名。 */
    private static PlayerOnlyCommand.TabCompleter players() {
        return (player, args) -> args.size() > 1 ? List.of() : CommandCompletions.onlinePlayers();
    }

    /** 第一参数为布尔字面量。 */
    private static PlayerOnlyCommand.TabCompleter booleans() {
        return (player, args) -> args.size() > 1 ? List.of() : List.of("true", "false");
    }

    // ================= 管理领地创建/删除 =================

    private void claim(CommonPlayer player, List<String> args) {
        int radius = parseRadius(player, args);
        if (radius < 0) {
            return;
        }
        List<ChunkLoc> targets = standingTargets(player, radius);
        OwnerRef server = OwnerRef.of(BuiltinOwnerTypes.SERVER, ServerClaimOwner.ID);
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        ClaimService.INSTANCE.claim(server, bukkitPlayer.getWorld().getUID(), targets, "Admin Claim", true)
            .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_ADMIN_CLAIM_SUCCESS,
                        Map.of("<count>", String.valueOf(result.affectedChunks())));
                } else {
                    ClaimMessages.failure(player, result.failureReason());
                }
            }));
    }

    private void unclaim(CommonPlayer player, List<String> args) {
        int radius = parseRadius(player, args);
        if (radius < 0) {
            return;
        }
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        List<ChunkLoc> targets = standingTargets(player, radius);
        ClaimService.INSTANCE.adminUnclaim(targets)
            .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    LangUtils.sendLang(player, Languages.COMMAND_ADMIN_UNCLAIM_SUCCESS,
                        Map.of("<count>", String.valueOf(result.affectedChunks())));
                } else {
                    ClaimMessages.failure(player, result.failureReason());
                }
            }));
    }

    // ================= 强制转让 / 释放 / 豁免 =================

    private void transfer(CommonPlayer player, List<String> args) {
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
            return;
        }
        UUID target = resolveTarget(args.getFirst());
        if (target == null) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_FAIL_INVALID_PLAYER);
            return;
        }
        ClaimData standing = standingClaim(player);
        if (standing == null) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_FAIL_CLAIM_NOT_FOUND);
            return;
        }
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        AdminService.INSTANCE.transferClaim(standing.getClaimId(), target)
            .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null) {
                    return;
                }
                sendAdminResult(bukkitPlayer, result, Languages.COMMAND_ADMIN_TRANSFER_SUCCESS, Map.of(
                    "<player>", args.getFirst(),
                    "<chunks>", String.valueOf(result.affectedChunks())));
            }));
    }

    private void release(CommonPlayer player, List<String> args) {
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
            return;
        }
        String claimId = args.getFirst();
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        AdminService.INSTANCE.releaseClaim(claimId)
            .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null) {
                    return;
                }
                sendAdminResult(bukkitPlayer, result, Languages.COMMAND_ADMIN_RELEASE_SUCCESS, Map.of(
                    "<claim>", claimId,
                    "<chunks>", String.valueOf(result.affectedChunks())));
            }));
    }

    private void exempt(CommonPlayer player, List<String> args) {
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
            return;
        }
        Boolean exempt = parseBoolean(args.getFirst());
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
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        AdminService.INSTANCE.setExempt(claimId, exempt)
            .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null) {
                    return;
                }
                sendAdminResult(bukkitPlayer, result, Languages.COMMAND_ADMIN_EXEMPT_SET, Map.of(
                    "<exempt>", String.valueOf(exempt)));
            }));
    }

    // ================= 重命名 =================

    private void rename(CommonPlayer player, List<String> args) {
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_USAGE);
            return;
        }
        ClaimData standing = standingClaim(player);
        if (standing == null) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_FAIL_CLAIM_NOT_FOUND);
            return;
        }
        String claimId = standing.getClaimId();
        // 名字允许带空格，整段参数拼接后再交给服务层 trim 与长度校验
        String name = String.join(" ", args);
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        AdminService.INSTANCE.renameClaim(claimId, name)
            .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null) {
                    return;
                }
                sendAdminResult(bukkitPlayer, result, Languages.COMMAND_ADMIN_RENAME_SUCCESS, Map.of(
                    "<claim>", claimId,
                    "<name>", ClaimService.normalizeClaimName(name)));
            }));
    }

    // ================= 信息 / 孤儿列表 =================

    private void info(CommonPlayer player) {
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
            : PlainTextComponentSerializer
                .plainText().serialize(resolved.displayName());

        LangUtils.sendLang(player, Languages.COMMAND_INFO_HEADER);
        LangUtils.sendLang(player, Languages.COMMAND_INFO_NAME, Map.of(
            "<name>", claim.getName() == null ? claim.getClaimId() : claim.getName()));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_OWNER, Map.of(
            "<owner>", ownerName, "<type>", LangUtils.ownerTypeLabel(player.locale(), claim.getOwnerType())));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_WORLD, Map.of(
            "<world>", world == null ? claim.getWorldUuid().toString().substring(0, 8) : world.getName()));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_CHUNKS, Map.of("<chunks>", String.valueOf(chunks)));
        LangUtils.sendLang(player, Languages.COMMAND_INFO_CREATED, Map.of(
            "<created>", DATE_FORMAT.format(Instant.ofEpochMilli(claim.getCreatedAt()))));
        LangUtils.sendLang(player, Languages.COMMAND_ADMIN_INFO_ADMIN, Map.of(
            "<admin>", String.valueOf(claim.isAdmin()),
            "<claim>", claim.getClaimId()));
        LangUtils.sendLang(player, Languages.COMMAND_ADMIN_INFO_EXEMPT, Map.of(
            "<exempt>", String.valueOf(claim.isUpkeepExempt())));
        LangUtils.sendLang(player, Languages.COMMAND_ADMIN_INFO_ORPHAN, Map.of(
            "<orphan>", String.valueOf(OrphanService.INSTANCE.isOrphan(claim))));
    }

    private void orphans(CommonPlayer player) {
        List<OrphanInfo> orphans = OrphanService.INSTANCE.listOrphans();
        if (orphans.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_ORPHANS_EMPTY);
            return;
        }
        LangUtils.sendLang(player, Languages.COMMAND_ADMIN_ORPHANS_HEADER, Map.of(
            "<size>", String.valueOf(orphans.size())));
        for (OrphanInfo orphan : orphans) {
            long sinceDays = orphan.orphanSince() == 0L ? 0L
                : Math.max(0L, (System.currentTimeMillis() - orphan.orphanSince()) / 1000L / 86400L);
            LangUtils.sendLang(player, Languages.COMMAND_ADMIN_ORPHANS_ENTRY, Map.of(
                "<claim>", orphan.claimId(),
                "<name>", orphan.claimName() == null ? "?" : orphan.claimName(),
                "<type>", LangUtils.ownerTypeLabel(player.locale(), orphan.ownerType()),
                "<owner>", orphan.ownerId(),
                "<chunks>", String.valueOf(orphan.chunks()),
                "<days>", String.valueOf(sinceDays)));
        }
    }

    // ================= 手动回收 =================

    private void run(CommonPlayer player) {
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        AdminService.INSTANCE.runMaintenance(System.currentTimeMillis())
            .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null) {
                    return;
                }
                UpkeepNotifications.dispatch(result);
                LangUtils.sendLang(player, Languages.COMMAND_ADMIN_RUN_DONE, Map.of(
                    "<released>", String.valueOf(result.released()),
                    "<charged>", String.valueOf(result.fullyCharged())));
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

    private static @Nullable ClaimData standingClaim(CommonPlayer player) {
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        ChunkLoc standing = ChunkLoc.of(
            bukkitPlayer.getWorld().getUID(),
            bukkitPlayer.getLocation().getBlockX() >> 4,
            bukkitPlayer.getLocation().getBlockZ() >> 4
        );
        String claimId = snapshot.claimIdByChunk().get(standing);
        return claimId == null ? null : snapshot.claimsById().get(claimId);
    }

    private static List<ChunkLoc> standingTargets(CommonPlayer player, int radius) {
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        return ClaimEngine.radiusTargets(
            bukkitPlayer.getWorld().getUID(),
            bukkitPlayer.getLocation().getBlockX() >> 4,
            bukkitPlayer.getLocation().getBlockZ() >> 4,
            radius);
    }

    /**
     * 解析可选的 [radius] 参数：缺省为 1；非法返回 -1（已向玩家反馈）。
     */
    private static int parseRadius(CommonPlayer player, List<String> args) {
        if (args.isEmpty()) {
            return 1;
        }
        int radius;
        try {
            radius = Integer.parseInt(args.getFirst());
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
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_TOO_LARGE, Map.of("<max>", String.valueOf(max)));
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
        return CommandUtils.resolvePlayer(name);
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
