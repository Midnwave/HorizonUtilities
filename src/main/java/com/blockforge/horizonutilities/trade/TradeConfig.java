package com.blockforge.horizonutilities.trade;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads and exposes configuration values from trade.yml.
 */
public class TradeConfig {

    private final HorizonUtilitiesPlugin plugin;
    private YamlConfiguration config;

    private int requestTimeoutSeconds;
    private boolean sameWorldOnly;
    private Set<Material> blacklistedMaterials;
    private boolean logTrades;
    private String guiTitle;

    public TradeConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "trade.yml");
        if (!file.exists()) {
            plugin.saveResource("trade.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        requestTimeoutSeconds = config.getInt("request-timeout-seconds", 60);
        sameWorldOnly = config.getBoolean("same-world-only", false);
        logTrades = config.getBoolean("log-trades", true);
        guiTitle = config.getString("gui-title", "<gradient:#7B2FF7:#FF5733>Trade: <player1> ↔ <player2>");

        blacklistedMaterials = new HashSet<>();
        for (String name : config.getStringList("blacklisted-materials")) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                blacklistedMaterials.add(mat);
            }
        }
    }

    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public boolean isSameWorldOnly() { return sameWorldOnly; }
    public Set<Material> getBlacklistedMaterials() { return blacklistedMaterials; }
    public boolean isLogTrades() { return logTrades; }
    public String getGuiTitle() { return guiTitle; }

    public boolean isBlacklisted(Material material) {
        return blacklistedMaterials.contains(material);
    }
}
