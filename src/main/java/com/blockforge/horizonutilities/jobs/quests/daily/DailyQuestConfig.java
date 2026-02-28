package com.blockforge.horizonutilities.jobs.quests.daily;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads quest definitions and settings from quests.yml.
 */
public class DailyQuestConfig {

    private final HorizonUtilitiesPlugin plugin;
    private final Logger logger;

    // Settings
    private boolean enabled;
    private int questsPerDay;
    private String resetTime; // HH:mm in server timezone
    private double levelScalingFactor;
    private double prestigeScalingFactor;
    private boolean notifyOnAssign;
    private boolean notifyOnComplete;
    private boolean autoClaimRewards;

    // Quest definitions
    private final List<QuestDefinition> definitions = new ArrayList<>();

    public DailyQuestConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) {
            plugin.saveResource("quests.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Settings
        enabled                 = config.getBoolean("settings.enabled", true);
        questsPerDay            = config.getInt("settings.quests-per-day", 3);
        resetTime               = config.getString("settings.reset-time", "00:00");
        levelScalingFactor      = config.getDouble("settings.level-scaling-factor", 0.02);
        prestigeScalingFactor   = config.getDouble("settings.prestige-scaling-factor", 0.15);
        notifyOnAssign          = config.getBoolean("settings.notify-on-assign", true);
        notifyOnComplete        = config.getBoolean("settings.notify-on-complete", true);
        autoClaimRewards        = config.getBoolean("settings.auto-claim-rewards", true);

        // Load definitions
        definitions.clear();
        ConfigurationSection defsSection = config.getConfigurationSection("quests");
        if (defsSection == null) {
            logger.warning("[Quests] No quest definitions found in quests.yml");
            return;
        }

        for (String questId : defsSection.getKeys(false)) {
            ConfigurationSection qs = defsSection.getConfigurationSection(questId);
            if (qs == null) continue;

            String jobId = qs.getString("job", "");
            String description = qs.getString("description", "Complete a task");
            String actionStr = qs.getString("action", "BREAK");
            String material = qs.getString("material"); // null = any
            int amount = qs.getInt("amount", 50);
            double money = qs.getDouble("reward-money", 100.0);
            double xp = qs.getDouble("reward-xp", 50.0);
            int weight = qs.getInt("weight", 1);
            boolean defEnabled = qs.getBoolean("enabled", true);

            JobAction action;
            try {
                action = JobAction.valueOf(actionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[Quests] Invalid action type '" + actionStr + "' for quest '" + questId + "'");
                continue;
            }

            definitions.add(new QuestDefinition(
                    questId, jobId, description, action, material,
                    amount, money, xp, weight, defEnabled
            ));
        }

        logger.info("[Quests] Loaded " + definitions.size() + " quest definitions.");
    }

    // Getters
    public boolean isEnabled()                  { return enabled; }
    public int getQuestsPerDay()                { return questsPerDay; }
    public String getResetTime()                { return resetTime; }
    public double getLevelScalingFactor()        { return levelScalingFactor; }
    public double getPrestigeScalingFactor()    { return prestigeScalingFactor; }
    public boolean isNotifyOnAssign()           { return notifyOnAssign; }
    public boolean isNotifyOnComplete()         { return notifyOnComplete; }
    public boolean isAutoClaimRewards()         { return autoClaimRewards; }
    public List<QuestDefinition> getDefinitions() { return definitions; }
}
