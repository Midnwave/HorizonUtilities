package com.blockforge.horizonutilities.gems;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

public class GemsManager {

    private final HorizonUtilitiesPlugin plugin;
    private final GemsConfig config;
    private final GemsStorageManager storage;
    private final Random random = new Random();

    public GemsManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.config = new GemsConfig(plugin);
        this.storage = new GemsStorageManager(plugin);
    }

    public GemsConfig getConfig() { return config; }
    public GemsStorageManager getStorage() { return storage; }

    /**
     * Ensure a player has a gems account. Called on join.
     */
    public void ensureAccount(UUID uuid, String name) {
        double balance = storage.getBalance(uuid);
        if (balance < 0) {
            // Account doesn't exist
            storage.createAccount(uuid, name, config.getStartingBalance());
        } else {
            // Update name in case it changed
            storage.updateName(uuid, name);
        }
    }

    public double getBalance(UUID uuid) {
        double bal = storage.getBalance(uuid);
        return bal < 0 ? 0 : bal;
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    /**
     * Deposit gems to a player. Returns true on success.
     */
    public boolean deposit(UUID uuid, double amount, String reason) {
        if (amount <= 0) return false;

        // Check max balance
        if (config.getMaxBalance() > 0) {
            double current = getBalance(uuid);
            if (current + amount > config.getMaxBalance()) {
                return false;
            }
        }

        if (!storage.deposit(uuid, amount)) return false;

        double newBalance = getBalance(uuid);
        if (config.isAdminLogOperations()) {
            storage.logTransaction(uuid, "DEPOSIT", amount, newBalance, reason);
        }
        return true;
    }

    /**
     * Withdraw gems from a player. Returns true on success.
     */
    public boolean withdraw(UUID uuid, double amount, String reason) {
        if (amount <= 0) return false;
        if (!storage.withdraw(uuid, amount)) return false;

        double newBalance = getBalance(uuid);
        if (config.isAdminLogOperations()) {
            storage.logTransaction(uuid, "WITHDRAW", amount, newBalance, reason);
        }
        return true;
    }

    /**
     * Set a player's gem balance directly.
     */
    public boolean setBalance(UUID uuid, double balance, String reason) {
        if (balance < 0) return false;
        if (!storage.setBalance(uuid, balance)) return false;

        if (config.isAdminLogOperations()) {
            storage.logTransaction(uuid, "SET", balance, balance, reason);
        }
        return true;
    }

    /**
     * Transfer gems between players.
     */
    public boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0) return false;
        if (!has(from, amount)) return false;

        if (config.getMaxBalance() > 0) {
            if (getBalance(to) + amount > config.getMaxBalance()) return false;
        }

        if (!storage.withdraw(from, amount)) return false;
        if (!storage.deposit(to, amount)) {
            // Rollback
            storage.deposit(from, amount);
            return false;
        }

        double fromBal = getBalance(from);
        double toBal = getBalance(to);
        storage.logTransaction(from, "TRANSFER_OUT", amount, fromBal, "transfer to " + to);
        storage.logTransaction(to, "TRANSFER_IN", amount, toBal, "transfer from " + from);

        if (config.isBroadcastLargeTransactions() && amount >= config.getBroadcastThreshold()) {
            Player fromPlayer = Bukkit.getPlayer(from);
            Player toPlayer = Bukkit.getPlayer(to);
            String fromName = fromPlayer != null ? fromPlayer.getName() : from.toString();
            String toName = toPlayer != null ? toPlayer.getName() : to.toString();
            Bukkit.broadcast(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(fromName, NamedTextColor.GOLD))
                    .append(Component.text(" sent ", NamedTextColor.GRAY))
                    .append(Component.text(formatGems(amount), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" to ", NamedTextColor.GRAY))
                    .append(Component.text(toName, NamedTextColor.GOLD)));
        }
        return true;
    }

    /**
     * Exchange money for gems.
     */
    public boolean exchangeMoneyToGems(Player player, double moneyAmount) {
        if (!config.isExchangeEnabled()) return false;
        if (moneyAmount < config.getExchangeMinAmount() || moneyAmount > config.getExchangeMaxAmount()) return false;
        if (!plugin.getVaultHook().isAvailable()) return false;
        if (!plugin.getVaultHook().has(player, moneyAmount)) return false;

        double taxAmount = moneyAmount * (config.getExchangeTaxPercent() / 100.0);
        double effectiveAmount = moneyAmount - taxAmount;
        double gemsToGrant = effectiveAmount * config.getExchangeRate();
        gemsToGrant = Math.floor(gemsToGrant * 100) / 100; // round to 2 decimals

        if (gemsToGrant <= 0) return false;

        if (config.getMaxBalance() > 0 && getBalance(player.getUniqueId()) + gemsToGrant > config.getMaxBalance()) {
            return false;
        }

        // Withdraw money
        plugin.getVaultHook().withdraw(player, moneyAmount);

        // Deposit gems
        deposit(player.getUniqueId(), gemsToGrant, "exchange: " + moneyAmount + " money");

        return true;
    }

    /**
     * Called when a player completes a daily quest — chance to earn gems.
     */
    public void onQuestCompleted(Player player) {
        if (!config.isEnabled() || !config.isQuestRewardsEnabled()) return;

        int roll = random.nextInt(100);
        if (roll >= config.getQuestRewardChance()) return;

        int gems = config.getQuestRewardMin() +
                random.nextInt(Math.max(1, config.getQuestRewardMax() - config.getQuestRewardMin() + 1));

        if (deposit(player.getUniqueId(), gems, "quest completion reward")) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("You earned ", NamedTextColor.GRAY))
                    .append(Component.text(formatGems(gems), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" from quest completion!", NamedTextColor.GRAY)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
    }

    /**
     * Called when a player levels up a job — check milestone rewards.
     */
    public void onJobLevelUp(Player player, int newLevel) {
        if (!config.isEnabled() || !config.isJobMilestonesEnabled()) return;

        Integer reward = config.getJobMilestoneRewards().get(newLevel);
        if (reward == null || reward <= 0) return;

        if (deposit(player.getUniqueId(), reward, "job milestone level " + newLevel)) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Job milestone! ", NamedTextColor.GREEN))
                    .append(Component.text("Level " + newLevel + " reached — earned ", NamedTextColor.GRAY))
                    .append(Component.text(formatGems(reward), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("!", NamedTextColor.GRAY)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
    }

    public static String formatGems(double amount) {
        if (amount == (long) amount) {
            return String.format("%,d gems", (long) amount);
        }
        return String.format("%,.2f gems", amount);
    }
}
