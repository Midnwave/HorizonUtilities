package com.blockforge.horizonutilities.quests.scoreboard;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

/**
 * Loads scoreboard display settings from scoreboard-config.yml.
 */
public class ScoreboardConfig {

    private final HorizonUtilitiesPlugin plugin;

    private String title;
    private int updateIntervalTicks;
    private boolean autoSwitchOnAssign;
    private List<String> lines;
    private String incompleteFormat;
    private String completeFormat;
    private String jobDailyIcon;
    private String generalDailyIcon;
    private String weeklyIcon;
    private String challengeIcon;
    private int maxQuestsPerCategory;

    public ScoreboardConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "scoreboard-config.yml");
        if (!file.exists()) {
            plugin.saveResource("scoreboard-config.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        title                = cfg.getString("title", "<gradient:gold:yellow><bold>Horizon SMP</bold></gradient>");
        updateIntervalTicks  = cfg.getInt("update-interval-ticks", 20);
        autoSwitchOnAssign   = cfg.getBoolean("auto-switch-on-assign", true);
        lines                = cfg.getStringList("lines");
        incompleteFormat     = cfg.getString("quest-line-format.incomplete",
            "<white>{icon} {description}</white> <gray>[{progress}/{total}]</gray>");
        completeFormat       = cfg.getString("quest-line-format.complete",
            "<green>{icon} <strikethrough>{description}</strikethrough> ✔</green>");
        jobDailyIcon         = cfg.getString("icons.JOB_DAILY", "\u26CF");
        generalDailyIcon     = cfg.getString("icons.GENERAL_DAILY", "\u2694");
        weeklyIcon           = cfg.getString("icons.WEEKLY", "\uD83D\uDCC5");
        challengeIcon        = cfg.getString("icons.CHALLENGE", "\uD83C\uDFC6");
        maxQuestsPerCategory = cfg.getInt("max-quests-per-category", 4);
    }

    public String getTitle()                { return title; }
    public int getUpdateIntervalTicks()     { return updateIntervalTicks; }
    public boolean isAutoSwitchOnAssign()   { return autoSwitchOnAssign; }
    public List<String> getLines()          { return lines; }
    public String getIncompleteFormat()     { return incompleteFormat; }
    public String getCompleteFormat()       { return completeFormat; }
    public int getMaxQuestsPerCategory()    { return maxQuestsPerCategory; }

    public String getIcon(String categoryName) {
        return switch (categoryName) {
            case "JOB_DAILY" -> jobDailyIcon;
            case "GENERAL_DAILY" -> generalDailyIcon;
            case "WEEKLY" -> weeklyIcon;
            case "CHALLENGE" -> challengeIcon;
            default -> "\u2022";
        };
    }
}
