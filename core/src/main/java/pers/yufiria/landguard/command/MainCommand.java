package pers.yufiria.landguard.command;

import crypticlib.Invoker;
import crypticlib.PlatformSide;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.command.CommandTree;
import crypticlib.command.annotation.Command;
import crypticlib.command.annotation.Subcommand;
import crypticlib.perm.PermInfo;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.config.PluginConfigs;
import pers.yufiria.landguard.ui.ClaimListMenu;
import pers.yufiria.landguard.util.CommandUtils;

import java.util.List;

@Command(platforms = {PlatformSide.BUKKIT})
public class MainCommand extends CommandTree {

    public static final MainCommand INSTANCE = new MainCommand();

    MainCommand() {
        super(
            CommandInfo
                .builder("land")
                .permission(new PermInfo("landguard.command"))
                .aliases(PluginConfigs.MAIN_COMMAND_ALIASES.value())
                .build()
        );
    }

    /** /land 无参打开 GUI 领地菜单（Task 8）。 */
    @Override
    public void execute(@NotNull Invoker invoker, List<String> args) {
        if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
            return;
        }
        // 打开 GUI 需要 Bukkit 玩家对象（crypticlib UI 以 Bukkit Player 为入口）
        new ClaimListMenu(CommandUtils.bukkitPlayer(invoker.asPlayer())).openMenu();
    }

    @Subcommand
    CommandNode reload = ReloadCommand.INSTANCE;

    @Subcommand
    CommandNode version = VersionCommand.INSTANCE;

    @Subcommand
    CommandNode claim = ClaimCommand.INSTANCE;

    @Subcommand
    CommandNode unclaim = UnclaimCommand.INSTANCE;

    @Subcommand
    CommandNode list = ClaimListCommand.INSTANCE;

    @Subcommand
    CommandNode info = ClaimInfoCommand.INSTANCE;

    @Subcommand
    CommandNode boundary = BoundaryCommand.INSTANCE;

    @Subcommand
    CommandNode rename = RenameCommand.INSTANCE;

    @Subcommand
    CommandNode transfer = TransferCommand.INSTANCE;

    @Subcommand
    CommandNode group = GroupCommand.INSTANCE;

    @Subcommand
    CommandNode buy = EconomyCommands.BuyCommand.INSTANCE;

    @Subcommand
    CommandNode sell = EconomyCommands.SellCommand.INSTANCE;

    @Subcommand
    CommandNode bank = EconomyCommands.BankCommand.INSTANCE;

    @Subcommand
    CommandNode admin = AdminCommand.INSTANCE;

}
