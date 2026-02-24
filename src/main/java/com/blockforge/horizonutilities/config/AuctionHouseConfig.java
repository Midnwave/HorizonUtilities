package com.blockforge.horizonutilities.config;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class AuctionHouseConfig {

    private final HorizonUtilitiesPlugin plugin;
    private YamlConfiguration config;

    private List<Integer> durations;
    private int defaultDuration;
    private double listingFeePercent;
    private double salesTaxPercent;
    private int listingCooldownSeconds;
    private int maxListingsDefault;
    private boolean antiSnipeEnabled;
    private int antiSnipeTriggerSeconds;
    private int antiSnipeExtensionSeconds;
    private int antiSnipeMaxExtensions;
    private boolean escrowEnabled;
    private double minPrice;
    private double maxPrice;
    private double minBidIncrementPercent;
    private Set<Material> blacklistedMaterials;
    private String notificationSound;
    private boolean queueOffline;

    public AuctionHouseConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "auction-house.yml");
        if (!file.exists()) plugin.saveResource("auction-house.yml", false);
        config = YamlConfiguration.loadConfiguration(file);

        durations = config.getIntegerList("durations");
        if (durations.isEmpty()) durations = List.of(1, 6, 12, 24, 48, 72);
        defaultDuration = config.getInt("default-duration", 24);
        listingFeePercent = config.getDouble("listing-fee-percent", 2.0);
        salesTaxPercent = config.getDouble("sales-tax-percent", 5.0);
        listingCooldownSeconds = config.getInt("listing-cooldown-seconds", 10);
        maxListingsDefault = config.getInt("max-listings-default", 5);

        ConfigurationSection snipe = config.getConfigurationSection("anti-snipe");
        if (snipe != null) {
            antiSnipeEnabled = snipe.getBoolean("enabled", true);
            antiSnipeTriggerSeconds = snipe.getInt("trigger-seconds", 30);
            antiSnipeExtensionSeconds = snipe.getInt("extension-seconds", 30);
            antiSnipeMaxExtensions = snipe.getInt("max-extensions", 3);
        }

        escrowEnabled = config.getBoolean("escrow", true);
        minPrice = config.getDouble("min-price", 1.0);
        maxPrice = config.getDouble("max-price", 1000000.0);
        minBidIncrementPercent = config.getDouble("min-bid-increment-percent", 5.0);

        blacklistedMaterials = new HashSet<>();
        for (String name : config.getStringList("blacklisted-materials")) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) blacklistedMaterials.add(mat);
        }

        notificationSound = config.getString("notifications.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        queueOffline = config.getBoolean("notifications.queue-offline", true);
    }

    public List<Integer> getDurations() { return durations; }
    public int getDefaultDuration() { return defaultDuration; }
    public double getListingFeePercent() { return listingFeePercent; }
    public double getSalesTaxPercent() { return salesTaxPercent; }
    public int getListingCooldownSeconds() { return listingCooldownSeconds; }
    public int getMaxListingsDefault() { return maxListingsDefault; }
    public boolean isAntiSnipeEnabled() { return antiSnipeEnabled; }
    public int getAntiSnipeTriggerSeconds() { return antiSnipeTriggerSeconds; }
    public int getAntiSnipeExtensionSeconds() { return antiSnipeExtensionSeconds; }
    public int getAntiSnipeMaxExtensions() { return antiSnipeMaxExtensions; }
    public boolean isEscrowEnabled() { return escrowEnabled; }
    public double getMinPrice() { return minPrice; }
    public double getMaxPrice() { return maxPrice; }
    public double getMinBidIncrementPercent() { return minBidIncrementPercent; }
    public boolean isBlacklisted(Material mat) { return blacklistedMaterials.contains(mat); }
    public String getNotificationSound() { return notificationSound; }
    public boolean isQueueOffline() { return queueOffline; }
}
