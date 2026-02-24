package com.blockforge.horizonutilities.auraskills;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Loads settings from auraskills-integration.yml.
 */
public class AuraSkillsIntegrationConfig {

    private final HorizonUtilitiesPlugin plugin;

    private boolean enabled;
    private boolean xpSyncEnabled;

    /** job id -> (skill name -> ratio) */
    private Map<String, Map<String, Double>> xpSyncRatios;

    private boolean milestonesEnabled;

    /** job level -> MilestoneReward */
    private Map<Integer, MilestoneReward> milestones;

    public AuraSkillsIntegrationConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "auraskills-integration.yml");
        if (!file.exists()) {
            plugin.saveResource("auraskills-integration.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        enabled = cfg.getBoolean("enabled", false);
        xpSyncEnabled = cfg.getBoolean("xp-sync.enabled", true);

        xpSyncRatios = new LinkedHashMap<>();
        ConfigurationSection ratiosSection = cfg.getConfigurationSection("xp-sync.ratios");
        if (ratiosSection != null) {
            for (String jobId : ratiosSection.getKeys(false)) {
                ConfigurationSection jobSection = ratiosSection.getConfigurationSection(jobId);
                if (jobSection == null) continue;
                Map<String, Double> skillMap = new LinkedHashMap<>();
                for (String skill : jobSection.getKeys(false)) {
                    skillMap.put(skill.toUpperCase(Locale.ROOT), jobSection.getDouble(skill, 0));
                }
                xpSyncRatios.put(jobId.toLowerCase(Locale.ROOT), skillMap);
            }
        }

        milestonesEnabled = cfg.getBoolean("milestone-rewards.enabled", true);
        milestones = new TreeMap<>();
        ConfigurationSection rewardsSection = cfg.getConfigurationSection("milestone-rewards.rewards");
        if (rewardsSection != null) {
            for (String levelStr : rewardsSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(levelStr);
                    ConfigurationSection rs = rewardsSection.getConfigurationSection(levelStr);
                    if (rs == null) continue;
                    int xpLamp = rs.getInt("xp-lamp-amount", 0);
                    int bonusStat = rs.getInt("bonus-stat-points", 0);
                    milestones.put(level, new MilestoneReward(level, xpLamp, bonusStat));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public boolean isXpSyncEnabled() { return xpSyncEnabled; }
    public Map<String, Map<String, Double>> getXpSyncRatios() { return xpSyncRatios; }
    public boolean isMilestonesEnabled() { return milestonesEnabled; }
    public Map<Integer, MilestoneReward> getMilestones() { return milestones; }

    /** Immutable milestone reward data. */
    public record MilestoneReward(int level, int xpLampAmount, int bonusStatPoints) {}
}
