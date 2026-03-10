package com.blockforge.horizonutilities.crafting;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class CraftingTableConfig {

    private final HorizonUtilitiesPlugin plugin;
    private boolean enabled;
    private int cleanupAfterDays;

    public CraftingTableConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "crafting-tables.yml");
        if (!file.exists()) plugin.saveResource("crafting-tables.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        enabled          = cfg.getBoolean("enabled", true);
        cleanupAfterDays = cfg.getInt("cleanup-after-days", 30);
    }

    public boolean isEnabled()       { return enabled; }
    public int getCleanupAfterDays() { return cleanupAfterDays; }
}
