package pers.yufiria.landguard.hook.vault;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import pers.yufiria.landguard.economy.EconomyProvider;

import java.util.UUID;

/**
 * Vault {@link Economy} 到 core {@link EconomyProvider} 的适配。
 * 本类是全工程唯一引用 {@code net.milkbowl.vault} 的类，仅在 Vault 存在时
 * 由 {@link VaultHook} 反射加载，避免无 Vault 环境出现链接错误。
 */
public final class VaultEconomyAdapter implements EconomyProvider {

    private final Economy economy;

    public VaultEconomyAdapter(Economy economy) {
        this.economy = economy;
    }

    @Override
    public String name() {
        return "Vault";
    }

    @Override
    public boolean available() {
        return economy != null && economy.isEnabled();
    }

    private OfflinePlayer offline(UUID player) {
        return Bukkit.getOfflinePlayer(player);
    }

    @Override
    public boolean hasAccount(UUID player) {
        return economy.hasAccount(offline(player));
    }

    @Override
    public double balance(UUID player) {
        return economy.getBalance(offline(player));
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        EconomyResponse response = economy.depositPlayer(offline(player), amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(offline(player), amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }

}
