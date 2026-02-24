package com.blockforge.horizonutilities.economy;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class VaultHook {

    private final HorizonUtilitiesPlugin plugin;
    private Economy economy;

    public VaultHook(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return true;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (economy == null) return 0;
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        EconomyResponse resp = economy.withdrawPlayer(player, amount);
        return resp.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        EconomyResponse resp = economy.depositPlayer(player, amount);
        return resp.transactionSuccess();
    }

    /**
     * Deposits an amount to a player who may be offline.
     * Vault's depositPlayer(OfflinePlayer, double) handles offline accounts
     * for any economy plugin that supports them (e.g. EssentialsX).
     *
     * @param uuid   the target player's UUID
     * @param name   the target player's last-known name (used only for logging context)
     * @param amount amount to deposit
     * @return true if the transaction succeeded
     */
    public boolean depositOffline(UUID uuid, String name, double amount) {
        if (economy == null) return false;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        EconomyResponse resp = economy.depositPlayer(offlinePlayer, amount);
        return resp.transactionSuccess();
    }

    public String format(double amount) {
        if (economy == null) return String.format("$%.2f", amount);
        return economy.format(amount);
    }
}
