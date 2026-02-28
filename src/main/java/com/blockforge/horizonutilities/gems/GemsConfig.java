package com.blockforge.horizonutilities.gems;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class GemsConfig {

    private final HorizonUtilitiesPlugin plugin;

    private boolean enabled;
    private double startingBalance;
    private double maxBalance;
    private boolean broadcastLargeTransactions;
    private double broadcastThreshold;

    // Exchange
    private boolean exchangeEnabled;
    private double exchangeRate;
    private double exchangeMinAmount;
    private double exchangeMaxAmount;
    private double exchangeTaxPercent;

    // Quest rewards
    private boolean questRewardsEnabled;
    private int questRewardChance;
    private int questRewardMin;
    private int questRewardMax;

    // Job milestones
    private boolean jobMilestonesEnabled;
    private final Map<Integer, Integer> jobMilestoneRewards = new HashMap<>();

    // Admin
    private boolean adminLogOperations;

    public GemsConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "gems.yml");
        if (!file.exists()) {
            plugin.saveResource("gems.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        enabled = config.getBoolean("enabled", true);
        startingBalance = config.getDouble("starting-balance", 0);
        maxBalance = config.getDouble("max-balance", 0);
        broadcastLargeTransactions = config.getBoolean("broadcast-large-transactions", true);
        broadcastThreshold = config.getDouble("broadcast-threshold", 100);

        exchangeEnabled = config.getBoolean("exchange.enabled", true);
        exchangeRate = config.getDouble("exchange.rate", 0.1);
        exchangeMinAmount = config.getDouble("exchange.min-amount", 100.0);
        exchangeMaxAmount = config.getDouble("exchange.max-amount", 1000000.0);
        exchangeTaxPercent = config.getDouble("exchange.tax-percent", 5.0);

        questRewardsEnabled = config.getBoolean("quest-rewards.enabled", true);
        questRewardChance = config.getInt("quest-rewards.chance", 25);
        questRewardMin = config.getInt("quest-rewards.min", 1);
        questRewardMax = config.getInt("quest-rewards.max", 5);

        jobMilestonesEnabled = config.getBoolean("job-milestones.enabled", true);
        jobMilestoneRewards.clear();
        var milestonesSection = config.getConfigurationSection("job-milestones.levels");
        if (milestonesSection != null) {
            for (String key : milestonesSection.getKeys(false)) {
                try {
                    jobMilestoneRewards.put(Integer.parseInt(key), milestonesSection.getInt(key));
                } catch (NumberFormatException ignored) {}
            }
        }

        adminLogOperations = config.getBoolean("admin.log-operations", true);
    }

    public boolean isEnabled() { return enabled; }
    public double getStartingBalance() { return startingBalance; }
    public double getMaxBalance() { return maxBalance; }
    public boolean isBroadcastLargeTransactions() { return broadcastLargeTransactions; }
    public double getBroadcastThreshold() { return broadcastThreshold; }
    public boolean isExchangeEnabled() { return exchangeEnabled; }
    public double getExchangeRate() { return exchangeRate; }
    public double getExchangeMinAmount() { return exchangeMinAmount; }
    public double getExchangeMaxAmount() { return exchangeMaxAmount; }
    public double getExchangeTaxPercent() { return exchangeTaxPercent; }
    public boolean isQuestRewardsEnabled() { return questRewardsEnabled; }
    public int getQuestRewardChance() { return questRewardChance; }
    public int getQuestRewardMin() { return questRewardMin; }
    public int getQuestRewardMax() { return questRewardMax; }
    public boolean isJobMilestonesEnabled() { return jobMilestonesEnabled; }
    public Map<Integer, Integer> getJobMilestoneRewards() { return jobMilestoneRewards; }
    public boolean isAdminLogOperations() { return adminLogOperations; }
}
