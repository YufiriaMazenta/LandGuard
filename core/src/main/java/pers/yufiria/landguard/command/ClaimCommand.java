package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.command.annotation.Subcommand;
import crypticlib.perm.PermInfo;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.claim.*;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.group.GroupService;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.util.AsyncReply;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.ConfigValues;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;
import java.util.Map;

/**
 * {@code /land claim ...}：就地认领、方形批量认领、切换行走自动认领。
 * <p>
 * 三个入口交由框架节点树分派（{@code @Subcommand}）：{@code radius} / {@code auto} 各是独立子节点，
 * 只有无子命令（{@code /land claim}）时才回落到本节点的 {@link #execute} 做就地 3×3 认领。
 * 子命令关键字必须排在参数最前，{@code --group} 标志可在子命令之后的任意位置：
 * {@code /land claim radius 3 --group G} 与 {@code /land claim radius --group G 3} 等价。
 * <p>
 * 三者共用同一权限节点 {@code landguard.command.claim}：本命令的三种形态是同一能力的变体，
 * 拆分权限节点会让只授过根节点的服务器平白失去批量与自动认领。
 * 缺省 {@code --group} 即以本人身份认领；以组身份认领的授权校验在服务层写线程内复核。
 */
public final class ClaimCommand extends CommandNode {

    public static final ClaimCommand INSTANCE = new ClaimCommand();

    /** 认领身份标志：给出则以该用户组身份认领（缺省为本人）。 */
    public static final String FLAG_GROUP = "--group";

    private static final String PERMISSION = "landguard.command.claim";

    private ClaimCommand() {
        super(CommandInfo.builder("claim").permission(new PermInfo(PERMISSION)).build());
    }

    // ================= 子命令节点 =================

    @Subcommand
    CommandNode radius = new PlayerOnlyCommand(PERMISSION, "radius", this::radius, groupFlag());
    @Subcommand
    CommandNode auto = new PlayerOnlyCommand(PERMISSION, "auto", this::auto, groupFlag());

    /** 无子命令（或子命令名未命中）：{@code /land claim [--group <组>]} 就地 3×3 认领。 */
    @Override
    public void execute(@NotNull Invoker invoker, @NotNull List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        CommonPlayer player = invoker.asPlayer();
        GroupArg parsed = parseGroup(player, args);
        if (parsed == null) {
            return;
        }
        // 剥掉 --group 后仍有位置参数，说明子命令关键字写在了标志之后（如 --group G radius 3）
        if (!parsed.positional().isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_USAGE);
            return;
        }
        claim(player, 1, parsed.group());
    }

    /** {@code /land claim radius <半径> [--group <组>]}：以站立区块为中心的方形批量认领。 */
    private void radius(CommonPlayer player, List<String> args) {
        GroupArg parsed = parseGroup(player, args);
        if (parsed == null) {
            return;
        }
        List<String> positional = parsed.positional();
        if (positional.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
            return;
        }
        int radius;
        try {
            radius = Integer.parseInt(positional.getFirst());
        } catch (NumberFormatException e) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
            return;
        }
        if (radius < 1) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_INVALID);
            return;
        }
        int max = ConfigValues.get(ClaimConfigs.MAX_RADIUS);
        if (radius > max) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_RADIUS_TOO_LARGE, Map.of("<max>", String.valueOf(max)));
            return;
        }
        claim(player, radius, parsed.group());
    }

    /** {@code /land claim auto [--group <组>]}：切换行走自动认领，并记住本次使用的身份。 */
    private void auto(CommonPlayer player, List<String> args) {
        GroupArg parsed = parseGroup(player, args);
        if (parsed == null) {
            return;
        }
        if (!parsed.positional().isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_USAGE);
            return;
        }
        GroupData group = parsed.group();
        AutoModeManager.AutoState state = AutoModeManager.INSTANCE.toggle(
            player.uniqueId(), AutoModeManager.Mode.CLAIM, group == null ? null : group.getGroupId());
        LangUtils.sendLang(player, state.mode() == AutoModeManager.Mode.CLAIM
            ? Languages.COMMAND_CLAIM_AUTO_ON
            : Languages.COMMAND_CLAIM_AUTO_OFF);
    }

    /** 三个入口共用的认领执行：{@code group == null} 表示以本人身份认领。 */
    private void claim(CommonPlayer player, int radius, @Nullable GroupData group) {
        // 仅世界/坐标与实体区域调度需要 Bukkit 玩家，其余一律走 crypticlib 对象
        Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
        List<ChunkLoc> targets = ClaimEngine.radiusTargets(
            bukkitPlayer.getWorld().getUID(),
            bukkitPlayer.getLocation().getBlockX() >> 4,
            bukkitPlayer.getLocation().getBlockZ() >> 4,
            radius);
        OwnerRef owner = group == null
            ? OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.uniqueId().toString())
            : OwnerRef.of(BuiltinOwnerTypes.GROUP, group.getGroupId());
        String defaultName = group == null ? player.name() : group.getName();
        AsyncReply.toPlayer(bukkitPlayer, ClaimService.INSTANCE.claim(
            owner, bukkitPlayer.getWorld().getUID(), targets, defaultName, false, player.uniqueId()), result -> {
            if (result.success()) {
                ClaimBoundaryVisualizer.show(bukkitPlayer, targets);
                ClaimMessages.claimSuccess(player, result);
            } else {
                ClaimMessages.failure(player, result.failureReason());
            }
        });
    }

    // ================= 参数解析 / 补全 =================

    /** 解析结果：剥掉 {@code --group} 后的位置参数，以及与之一同解析出的用户组（{@code null} 表示以本人身份）。 */
    private record GroupArg(List<String> positional, @Nullable GroupData group) {
    }

    /**
     * 解析可出现在任意位置的 {@code --group <组标识符>}：返回位置参数与该组。
     * 组标识符解析不到时已给出提示并返回 {@code null}，调用方直接返回即可。
     */
    private static @Nullable GroupArg parseGroup(CommonPlayer player, List<String> args) {
        String groupIdArg = CommandUtils.parseFlag(args, FLAG_GROUP);
        List<String> positional = CommandUtils.withoutValueFlag(args, FLAG_GROUP);
        // 裸 --group（标志后没有值）不静默降级成「以本人身份认领」，否则玩家会误以为认到了组名下
        if (groupIdArg == null && CommandUtils.hasFlag(args, FLAG_GROUP)) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_USAGE);
            return null;
        }
        if (groupIdArg == null) {
            return new GroupArg(positional, null);
        }
        GroupData group = GroupService.findById(DataStore.INSTANCE.snapshot(), groupIdArg);
        if (group == null) {
            LangUtils.sendLang(player, Languages.COMMAND_CLAIM_GROUP_NOT_FOUND);
            return null;
        }
        return new GroupArg(positional, group);
    }

    /** 子命令参数补全：末位是 {@code --group} 时列出该玩家可扩张的组标识符，否则提示补标志本身。 */
    private static PlayerOnlyCommand.TabCompleter groupFlag() {
        return (player, args) -> !args.isEmpty() && args.getLast().equalsIgnoreCase(FLAG_GROUP)
            ? CommandCompletions.expandableGroups(player.uniqueId())
            : List.of(FLAG_GROUP);
    }

    @Override
    public List<String> tabComplete(@NotNull Invoker invoker, @NotNull List<String> args) {
        // 子命令名由框架自动并入候选，这里只补 --group 本身与它的取值（/land claim --group <TAB> 即带组身份的就地认领）
        if (invoker.isPlayer() && !args.isEmpty() && args.getLast().equalsIgnoreCase(FLAG_GROUP)) {
            return CommandCompletions.expandableGroups(invoker.asPlayer().uniqueId());
        }
        return List.of(FLAG_GROUP);
    }

    @Override
    public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
        LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
    }

}
