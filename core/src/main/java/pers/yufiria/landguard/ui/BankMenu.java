package pers.yufiria.landguard.ui;

import crypticlib.CrypticLibBukkit;
import crypticlib.ui.display.Icon;
import crypticlib.ui.display.MenuDisplay;
import crypticlib.ui.display.MenuLayout;
import crypticlib.ui.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.command.EconomyCommands;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.economy.EconomyService;
import pers.yufiria.landguard.util.LangUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 领地银行页：展示脚下领地/组银行余额，固定档位纯点击存取。
 * 仅在经济可用（配置开关 + Vault 提供方）且玩家站在对应领地内时可从详情页进入。
 */
public class BankMenu extends Menu {

    static final int[] AMOUNTS = {10, 100, 1000};

    static final List<String> LAYOUT = List.of(
        "ggggggggg",
        "ggggggggg",
        "ggggrgggg"
    );

    private final String claimId;
    private final int listPage;
    private final java.util.UUID worldUuid;
    private final int chunkX;
    private final int chunkZ;

    public BankMenu(@NotNull Player player, @NotNull String claimId, int listPage) {
        super(player);
        this.claimId = claimId;
        this.listPage = listPage;
        this.worldUuid = player.getWorld().getUID();
        this.chunkX = player.getLocation().getBlockX() >> 4;
        this.chunkZ = player.getLocation().getBlockZ() >> 4;
        this.display = buildDisplay();
    }

    private MenuDisplay buildDisplay() {
        Player player = player().orElse(null);
        Map<Character, java.util.function.Supplier<Icon>> icons = new LinkedHashMap<>();
        icons.put('g', MenuSupport::glass);
        icons.put('r', () -> MenuSupport.backIcon(player,
            () -> new ClaimDetailMenu(player, claimId, listPage).openMenu()));
        return new MenuDisplay(MenuSupport.text(player, Languages.MENU_BANK_TITLE),
            new MenuLayout(LAYOUT, icons));
    }

    @Override
    public void onLayoutUpdated() {
        Player player = player().orElse(null);
        if (player == null) {
            return;
        }
        double balance = EconomyService.INSTANCE.bankBalanceAt(
            DataStore.INSTANCE.snapshot(), worldUuid, chunkX, chunkZ);
        String balanceText = balance < 0 ? "-" : MenuSupport.money(balance);
        setIcon(4, MenuSupport.icon(Material.GOLD_BLOCK,
            MenuSupport.text(player, Languages.MENU_BANK_BALANCE_NAME),
            List.of(MenuSupport.text(player, Languages.MENU_BANK_BALANCE_LORE,
                Map.of("balance", balanceText)))));
        for (int i = 0; i < AMOUNTS.length; i++) {
            int amount = AMOUNTS[i];
            setIcon(10 + i, amountIcon(player, Material.EMERALD,
                Languages.MENU_BANK_DEPOSIT_NAME, Languages.MENU_BANK_DEPOSIT_LORE, amount, true));
            setIcon(14 + i, amountIcon(player, Material.HOPPER,
                Languages.MENU_BANK_WITHDRAW_NAME, Languages.MENU_BANK_WITHDRAW_LORE, amount, false));
        }
    }

    private Icon amountIcon(Player player, Material material, crypticlib.lang.entry.StringLangEntry nameEntry,
                            crypticlib.lang.entry.StringLangEntry loreEntry, int amount, boolean deposit) {
        Map<String, String> replacements = Map.of("amount", MenuSupport.money(amount));
        Icon icon = MenuSupport.icon(material,
            MenuSupport.text(player, nameEntry, replacements),
            List.of(MenuSupport.text(player, loreEntry, replacements)));
        icon.setClickAction(event -> transfer(amount, deposit));
        return icon;
    }

    private void transfer(int amount, boolean deposit) {
        Player player = player().orElse(null);
        if (player == null) {
            return;
        }
        if (!EconomyService.INSTANCE.available()) {
            LangUtils.sendLang(player, Languages.COMMAND_ECONOMY_UNAVAILABLE);
            return;
        }
        if (!claimId.equals(MenuSupport.standingClaimId(player))) {
            LangUtils.sendLang(player, Languages.MENU_FAIL_NOT_STANDING);
            return;
        }
        var future = deposit
            ? EconomyService.INSTANCE.deposit(player.getUniqueId(), worldUuid, chunkX, chunkZ, amount)
            : EconomyService.INSTANCE.withdraw(player.getUniqueId(), worldUuid, chunkX, chunkZ, amount);
        future.whenComplete((result, throwable) -> pers.yufiria.landguard.util.Schedulers.onPlayer(player, () -> {
            if (!player.isOnline() || throwable != null || result == null) {
                return;
            }
            if (result.success()) {
                LangUtils.sendLang(player,
                    deposit ? Languages.COMMAND_BANK_DEPOSIT_SUCCESS : Languages.COMMAND_BANK_WITHDRAW_SUCCESS,
                    Map.of("amount", MenuSupport.money(result.amount()),
                        "balance", MenuSupport.money(result.bankBalance())));
            } else {
                EconomyCommands.sendFail(player, result.failureReason());
            }
            updateMenu(true);
        }));
    }

    @Override
    public String parsedMenuTitle() {
        return MenuSupport.text(player().orElse(null), Languages.MENU_BANK_TITLE);
    }

}
