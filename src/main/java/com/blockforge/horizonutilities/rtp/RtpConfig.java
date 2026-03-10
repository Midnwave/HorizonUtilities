package com.blockforge.horizonutilities.rtp;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads RTP settings from rtp.yml.
 */
public class RtpConfig {

    private final HorizonUtilitiesPlugin plugin;

    // Defaults
    private int warmupSeconds;
    private int cooldownSeconds;
    private double cost;
    private int minRange;
    private int maxRange;
    private int maxAttempts;
    private boolean particles;

    // Blacklists
    private final Set<Biome> biomeBlacklist = new HashSet<>();
    private final Set<Material> unsafeBlocks = new HashSet<>();

    // First join
    private boolean firstJoinEnabled;
    private String firstJoinWorld;
    private int firstJoinDelayTicks;

    private FileConfiguration cfg;

    public RtpConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "rtp.yml");
        if (!file.exists()) {
            plugin.saveResource("rtp.yml", false);
        }

        cfg = YamlConfiguration.loadConfiguration(file);

        warmupSeconds   = cfg.getInt("defaults.warmup-seconds", 5);
        cooldownSeconds = cfg.getInt("defaults.cooldown-seconds", 300);
        cost            = cfg.getDouble("defaults.cost", 100.0);
        minRange        = cfg.getInt("defaults.min-range", 500);
        maxRange        = cfg.getInt("defaults.max-range", 10000);
        maxAttempts     = cfg.getInt("defaults.max-attempts", 50);
        particles       = cfg.getBoolean("defaults.particles", true);

        biomeBlacklist.clear();
        for (String name : cfg.getStringList("biome-blacklist")) {
            Biome biome = Registry.BIOME.get(NamespacedKey.minecraft(name.toLowerCase()));
            if (biome != null) biomeBlacklist.add(biome);
        }

        unsafeBlocks.clear();
        for (String name : cfg.getStringList("unsafe-blocks")) {
            try { unsafeBlocks.add(Material.valueOf(name.toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        }

        firstJoinEnabled    = cfg.getBoolean("first-join.enabled", true);
        firstJoinWorld      = cfg.getString("first-join.world", "world");
        firstJoinDelayTicks = cfg.getInt("first-join.delay-ticks", 60);
    }

    // ---- Per-world overrides ----

    public boolean isWorldEnabled(String worldName) {
        ConfigurationSection ws = cfg.getConfigurationSection("worlds." + worldName);
        if (ws == null) return true; // default to enabled
        return ws.getBoolean("enabled", true);
    }

    public int getMinRange(String worldName) {
        return cfg.getInt("worlds." + worldName + ".min-range", minRange);
    }

    public int getMaxRange(String worldName) {
        return cfg.getInt("worlds." + worldName + ".max-range", maxRange);
    }

    public double getCost(String worldName) {
        return cfg.getDouble("worlds." + worldName + ".cost", cost);
    }

    // ---- Getters ----
    public int getWarmupSeconds()       { return warmupSeconds; }
    public int getCooldownSeconds()     { return cooldownSeconds; }
    public double getDefaultCost()      { return cost; }
    public int getDefaultMinRange()     { return minRange; }
    public int getDefaultMaxRange()     { return maxRange; }
    public int getMaxAttempts()         { return maxAttempts; }
    public boolean isParticles()        { return particles; }
    public Set<Biome> getBiomeBlacklist()  { return biomeBlacklist; }
    public Set<Material> getUnsafeBlocks() { return unsafeBlocks; }
    public boolean isFirstJoinEnabled()    { return firstJoinEnabled; }
    public String getFirstJoinWorld()      { return firstJoinWorld; }
    public int getFirstJoinDelayTicks()    { return firstJoinDelayTicks; }
}
