package net.lifesteal.stealaspawner.util;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Thin wrapper around Vault's Economy service. All methods degrade to a
 * harmless no-op / false when Vault or an economy plugin isn't present,
 * so the rest of the plugin never needs a null check.
 */
public final class EconomyHook {

    private final JavaPlugin plugin;
    private Economy economy;

    public EconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Call from onEnable after Vault's plugin.yml softdepend has had a
     * chance to load. Returns true if a working Economy provider was found.
     */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (economy == null) return 0.0;
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.has(player, amount);
    }

    /**
     * Withdraws amount from player if they have it. Returns true on success.
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Deposits amount into player's account, works offline (Vault persists
     * through the economy plugin's own storage, not the player needing to
     * be online).
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (economy == null) return String.format("%.2f", amount);
        return economy.format(amount);
    }
}
