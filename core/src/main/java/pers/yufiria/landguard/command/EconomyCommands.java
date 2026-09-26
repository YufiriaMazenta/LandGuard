package pers.yufiria.landguard.command;

import crypticlib.CommonPlayer;
import crypticlib.CrypticLibBukkit;
import crypticlib.Invoker;
import crypticlib.command.CommandInfo;
import crypticlib.command.CommandNode;
import crypticlib.perm.PermInfo;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.economy.EconomyFailureReason;
import pers.yufiria.landguard.economy.EconomyService;
import pers.yufiria.landguard.util.CommandUtils;
import pers.yufiria.landguard.util.LangUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 经济命令：/land buy、/land sell、/land bank。
 * 经济不可用时（无 Vault 或配置关闭）统一拒绝并提示，其余功能不受影响（FR-7.3）。
 */
public final class EconomyCommands {

    private EconomyCommands() {
    }

    static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static void sendFail(Invoker player, EconomyFailureReason reason) {
        var entry = switch (reason) {
            case UNAVAILABLE -> Languages.COMMAND_ECONOMY_UNAVAILABLE;
            case INVALID_AMOUNT -> Languages.COMMAND_ECONOMY_INVALID_AMOUNT;
            case INSUFFICIENT_FUNDS -> Languages.COMMAND_ECONOMY_INSUFFICIENT_FUNDS;
            case QUOTA_IN_USE -> Languages.COMMAND_ECONOMY_QUOTA_IN_USE;
            case NOTHING_TO_SELL -> Languages.COMMAND_ECONOMY_NOTHING_TO_SELL;
            case CLAIM_NOT_FOUND -> Languages.COMMAND_BANK_UNCLAIMED;
            case BANK_FORBIDDEN -> Languages.COMMAND_BANK_FORBIDDEN;
            case BANK_EMPTY -> Languages.COMMAND_BANK_EMPTY;
        };
        LangUtils.sendLang(player, entry);
    }

    // ================= /land buy <区块数> =================

    public static final class BuyCommand extends CommandNode {

        public static final BuyCommand INSTANCE = new BuyCommand();

        private BuyCommand() {
            super(CommandInfo.builder("buy").permission(new PermInfo("landguard.command.buy")).build());
        }

        @Override
        public void execute(@NotNull Invoker invoker, List<String> args) {
            if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
                return;
            }
            CommonPlayer player = invoker.asPlayer();
            Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
            if (!EconomyService.INSTANCE.available()) {
                LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_UNAVAILABLE);
                return;
            }
            Integer chunks = parsePositiveInt(args, player);
            if (chunks == null) {
                return;
            }
            EconomyService.INSTANCE.buyChunks(player.uniqueId(), chunks)
                .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                    if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                        return;
                    }
                    if (result.success()) {
                        LangUtils.sendLang(player, Languages.COMMAND_BUY_SUCCESS, Map.of(
                            "<chunks>", String.valueOf(chunks),
                            "<cost>", money(result.amount()),
                            "<balance>", money(result.accountBalance())));
                    } else {
                        sendFail(player, result.failureReason());
                    }
                }));
        }

        @Override
        public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
            LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
        }
    }

    // ================= /land sell <区块数> =================

    public static final class SellCommand extends CommandNode {

        public static final SellCommand INSTANCE = new SellCommand();

        private SellCommand() {
            super(CommandInfo.builder("sell").permission(new PermInfo("landguard.command.sell")).build());
        }

        @Override
        public void execute(@NotNull Invoker invoker, List<String> args) {
            if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
                return;
            }
            CommonPlayer player = invoker.asPlayer();
            Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
            if (!EconomyService.INSTANCE.available()) {
                LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_UNAVAILABLE);
                return;
            }
            Integer chunks = parsePositiveInt(args, player);
            if (chunks == null) {
                return;
            }
            EconomyService.INSTANCE.sellChunks(player.uniqueId(), chunks)
                .whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                    if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                        return;
                    }
                    if (result.success()) {
                        LangUtils.sendLang(player, Languages.COMMAND_SELL_SUCCESS, Map.of(
                            "<chunks>", String.valueOf(chunks),
                            "<refund>", money(result.amount()),
                            "<balance>", money(result.accountBalance())));
                    } else {
                        sendFail(player, result.failureReason());
                    }
                }));
        }

        @Override
        public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
            LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
        }
    }

    // ================= /land bank [deposit|withdraw <金额>] =================

    public static final class BankCommand extends CommandNode {

        public static final BankCommand INSTANCE = new BankCommand();

        private BankCommand() {
            super(CommandInfo.builder("bank").permission(new PermInfo("landguard.command.bank")).build());
        }

        @Override
        public void execute(@NotNull Invoker invoker, List<String> args) {
            if (!CommandUtils.checkInvokerIsPlayer(invoker)) {
                return;
            }
            CommonPlayer player = invoker.asPlayer();
            Player bukkitPlayer = CommandUtils.bukkitPlayer(player);
            if (!EconomyService.INSTANCE.available()) {
                LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_UNAVAILABLE);
                return;
            }
            UUID world = bukkitPlayer.getWorld().getUID();
            int cx = bukkitPlayer.getLocation().getBlockX() >> 4;
            int cz = bukkitPlayer.getLocation().getBlockZ() >> 4;
            if (args.isEmpty()) {
                double balance = EconomyService.INSTANCE.bankBalanceAt(DataStore.INSTANCE.snapshot(), world, cx, cz);
                if (balance < 0) {
                    LangUtils.sendLang(player, Languages.COMMAND_BANK_UNCLAIMED);
                    return;
                }
                LangUtils.sendLang(player, Languages.COMMAND_BANK_BALANCE, Map.of("<balance>", money(balance)));
                return;
            }
            String action = args.get(0).toLowerCase(Locale.ROOT);
            boolean deposit = "deposit".equals(action);
            if (args.size() < 2 || (!deposit && !"withdraw".equals(action))) {
                LangUtils.sendLang(player, Languages.COMMAND_BANK_USAGE);
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(args.get(1));
            } catch (NumberFormatException e) {
                LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_INVALID_AMOUNT);
                return;
            }
            if (!(amount > 0) || Double.isNaN(amount) || Double.isInfinite(amount)) {
                LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_INVALID_AMOUNT);
                return;
            }
            var future = deposit
                ? EconomyService.INSTANCE.deposit(player.uniqueId(), world, cx, cz, amount)
                : EconomyService.INSTANCE.withdraw(player.uniqueId(), world, cx, cz, amount);
            future.whenComplete((result, throwable) -> CrypticLibBukkit.scheduler().runOnEntity(bukkitPlayer, () -> {
                if (!bukkitPlayer.isOnline() || throwable != null || result == null) {
                    return;
                }
                if (result.success()) {
                    LangUtils.sendLang(player,
                        deposit ? Languages.COMMAND_BANK_DEPOSIT_SUCCESS : Languages.COMMAND_BANK_WITHDRAW_SUCCESS,
                        Map.of("<amount>", money(result.amount()), "<balance>", money(result.bankBalance())));
                } else {
                    sendFail(player, result.failureReason());
                }
            }));
        }

        @Override
        public void onNoPerm(@NotNull Invoker invoker, @NotNull List<String> args) {
            LangUtils.sendLang(invoker, Languages.COMMAND_NO_PERM);
        }

        @Override
        public List<String> tabComplete(@NotNull Invoker invoker, @NotNull List<String> args) {
            return args.size() == 1 ? List.of("deposit", "withdraw") : List.of();
        }
    }

    private static Integer parsePositiveInt(List<String> args, CommonPlayer player) {
        if (args.isEmpty()) {
            LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_USAGE);
            return null;
        }
        try {
            int value = Integer.parseInt(args.get(0));
            if (value <= 0) {
                LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_INVALID_AMOUNT);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_INVALID_AMOUNT);
            return null;
        }
    }

}
