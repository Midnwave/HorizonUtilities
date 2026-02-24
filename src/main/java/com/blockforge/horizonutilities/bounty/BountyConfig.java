package com.blockforge.horizonutilities.bounty;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Loads and exposes configuration values from bounty.yml.
 */
public class BountyConfig {

    private final HorizonUtilitiesPlugin plugin;
    private YamlConfiguration config;

    private boolean enabled;
    private double minAmount;
    private double maxAmount;
    private int maxBountiesPerTarget;
    private int expirationDays;
    private double anonymousCostMultiplier;
    private boolean stacking;
    private double proximityAlertRange;
    private boolean broadcastNewBounty;
    private boolean broadcastClaim;

    public BountyConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "bounty.yml");
        if (!file.exists()) {
            plugin.saveResource("bounty.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        enabled                 = config.getBoolean("enabled", true);
        minAmount               = config.getDouble("min-amount", 100.0);
        maxAmount               = config.getDouble("max-amount", 1_000_000.0);
        maxBountiesPerTarget    = config.getInt("max-bounties-per-target", 10);
        expirationDays          = config.getInt("expiration-days", 7);
        anonymousCostMultiplier = config.getDouble("anonymous-cost-multiplier", 1.5);
        stacking                = config.getBoolean("stacking", true);
        proximityAlertRange     = config.getDouble("proximity-alert-range", 50.0);
        broadcastNewBounty      = config.getBoolean("broadcast-new-bounty", true);
        broadcastClaim          = config.getBoolean("broadcast-claim", true);
    }

    public boolean isEnabled()                  { return enabled; }
    public double getMinAmount()                 { return minAmount; }
    public double getMaxAmount()                 { return maxAmount; }
    public int getMaxBountiesPerTarget()         { return maxBountiesPerTarget; }
    public int getExpirationDays()               { return expirationDays; }
    public double getAnonymousCostMultiplier()   { return anonymousCostMultiplier; }
    public boolean isStacking()                  { return stacking; }
    public double getProximityAlertRange()       { return proximityAlertRange; }
    public boolean isBroadcastNewBounty()        { return broadcastNewBounty; }
    public boolean isBroadcastClaim()            { return broadcastClaim; }

    /** Expiration timestamp (millis) for a new bounty placed right now. */
    public long computeExpiresAt() {
        return System.currentTimeMillis() + (long) expirationDays * 24 * 60 * 60 * 1000;
    }
}
