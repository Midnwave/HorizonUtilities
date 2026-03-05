package com.blockforge.horizonutilities.config;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final HorizonUtilitiesPlugin plugin;
    private String prefix;
    private String databaseFile;
    private boolean metricsEnabled;
    private int anvilMaxRepairCost;

    public ConfigManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        prefix = cfg.getString("prefix", "<dark_gray>[<gradient:#7B2FF7:#FF5733>Horizon</gradient><dark_gray>]");
        databaseFile = cfg.getString("database.file", "data.db");
        metricsEnabled = cfg.getBoolean("metrics", true);
        anvilMaxRepairCost = cfg.getInt("anvil.max-repair-cost", -1);
    }

    public String getPrefix() { return prefix; }
    public String getDatabaseFile() { return databaseFile; }
    public boolean isMetricsEnabled() { return metricsEnabled; }
    /** Returns the max repair cost for anvils, or -1 to remove the limit. */
    public int getAnvilMaxRepairCost() { return anvilMaxRepairCost; }
}
