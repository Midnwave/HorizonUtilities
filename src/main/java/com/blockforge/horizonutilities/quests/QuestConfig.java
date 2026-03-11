package com.blockforge.horizonutilities.quests;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads quest settings, tier formula weights, quest counts, and scaling from horizon-quests.yml.
 * Templates are no longer used — quests are generated procedurally at runtime.
 */
public class QuestConfig {

    private final HorizonUtilitiesPlugin plugin;
    private final Logger logger;

    // Settings
    private boolean enabled;
    private String dailyResetTime;
    private String weeklyResetDay;
    private boolean notifyOnAssign;
    private boolean notifyOnComplete;
    private boolean notifyOnStepComplete;
    private boolean autoClaimRewards;
    private double dailyMoneyCap;
    private int dailyItemRewardCap;
    private int challengeRepeatBlockDays;

    // Tier
    private TierWeights tierWeights;
    private double maxTier;

    // Quest counts per category
    private final Map<QuestCategory, QuestCountConfig> questCounts = new EnumMap<>(QuestCategory.class);

    // Scaling
    private double difficultyScale;
    private double rewardScale;

    // Boss bar
    private boolean bossBarEnabled;
    private int bossBarDurationSeconds;
    private int bossBarMaxBars;
    private boolean bossBarShowOnComplete;
    private int bossBarCompleteDurationSeconds;
    private String bossBarFormat;
    private String bossBarCompleteFormat;
    private BossBar.Overlay bossBarOverlay;
    private final Map<QuestCategory, BossBar.Color> bossBarCategoryColors = new EnumMap<>(QuestCategory.class);

    public QuestConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "horizon-quests.yml");
        if (!file.exists()) {
            plugin.saveResource("horizon-quests.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Settings
        enabled                 = cfg.getBoolean("settings.enabled", true);
        dailyResetTime          = cfg.getString("settings.daily-reset-time", "00:00");
        weeklyResetDay          = cfg.getString("settings.weekly-reset-day", "MONDAY");
        notifyOnAssign          = cfg.getBoolean("settings.notify-on-assign", true);
        notifyOnComplete        = cfg.getBoolean("settings.notify-on-complete", true);
        notifyOnStepComplete    = cfg.getBoolean("settings.notify-on-step-complete", true);
        autoClaimRewards        = cfg.getBoolean("settings.auto-claim-rewards", true);
        dailyMoneyCap           = cfg.getDouble("settings.daily-money-cap", 5000.0);
        dailyItemRewardCap      = cfg.getInt("settings.daily-item-reward-cap", 10);
        challengeRepeatBlockDays= cfg.getInt("settings.challenge-repeat-block-days", 30);

        // Tier weights
        maxTier = cfg.getDouble("tier.max-tier", 10.0);
        tierWeights = new TierWeights(
                cfg.getDouble("tier.play-hours-weight", 0.005),
                cfg.getDouble("tier.mining-weight", 0.008),
                cfg.getDouble("tier.mining-per-hour-cap", 200),
                cfg.getDouble("tier.combat-weight", 0.015),
                cfg.getDouble("tier.kills-per-hour-cap", 50),
                cfg.getDouble("tier.distance-weight", 0.002),
                cfg.getDouble("tier.distance-cap-km", 500),
                cfg.getDouble("tier.crafting-weight", 0.01),
                cfg.getDouble("tier.crafting-per-hour-cap", 30),
                cfg.getDouble("tier.survival-weight", 0.3),
                cfg.getDouble("tier.job-level-weight", 0.04),
                cfg.getDouble("tier.prestige-weight", 0.2),
                cfg.getDouble("tier.exploration-weight", 0.5),
                cfg.getDouble("tier.exploration-divisor", 500),
                cfg.getDouble("tier.diversity-weight", 0.3),
                cfg.getInt("tier.diversity-blocks-threshold", 1000),
                cfg.getInt("tier.diversity-kills-threshold", 100),
                cfg.getDouble("tier.diversity-distance-threshold", 50),
                cfg.getInt("tier.diversity-crafting-threshold", 200),
                cfg.getInt("tier.diversity-fishing-threshold", 50),
                cfg.getInt("tier.diversity-breeding-threshold", 20),
                cfg.getDouble("tier.diversity-job-level-threshold", 10)
        );

        // Quest counts
        questCounts.clear();
        for (QuestCategory cat : QuestCategory.values()) {
            String key = "quest-counts." + cat.name().toLowerCase().replace('_', '-');
            questCounts.put(cat, new QuestCountConfig(
                    cfg.getInt(key + ".base", cat == QuestCategory.CHALLENGE ? 0 : 1),
                    cfg.getDouble(key + ".per-tier", 0.2),
                    cfg.getInt(key + ".max", 4),
                    cfg.getDouble(key + ".min-tier", cat == QuestCategory.CHALLENGE ? 3.0 : 0)
            ));
        }

        // Scaling
        difficultyScale = cfg.getDouble("difficulty-scale", 0.12);
        rewardScale     = cfg.getDouble("reward-scale", 0.15);

        // Boss bar
        bossBarEnabled                = cfg.getBoolean("bossbar.enabled", true);
        bossBarDurationSeconds        = cfg.getInt("bossbar.duration-seconds", 5);
        bossBarMaxBars                = cfg.getInt("bossbar.max-bars", 3);
        bossBarShowOnComplete         = cfg.getBoolean("bossbar.show-on-complete", true);
        bossBarCompleteDurationSeconds= cfg.getInt("bossbar.complete-duration-seconds", 3);
        bossBarFormat                 = cfg.getString("bossbar.format",
            "<icon> <gray><quest></gray> <dark_gray>|</dark_gray> <white><description></white> <yellow><progress>/<total></yellow>");
        bossBarCompleteFormat         = cfg.getString("bossbar.complete-format",
            "<icon> <green><bold><quest></bold> — Complete!</green>");

        String overlayStr = cfg.getString("bossbar.style", "NOTCHED_10").toUpperCase();
        try {
            bossBarOverlay = BossBar.Overlay.valueOf(overlayStr);
        } catch (IllegalArgumentException e) {
            bossBarOverlay = BossBar.Overlay.NOTCHED_10;
        }

        bossBarCategoryColors.clear();
        bossBarCategoryColors.put(QuestCategory.JOB_DAILY,
            parseBossColor(cfg.getString("bossbar.colors.job-daily", "GREEN")));
        bossBarCategoryColors.put(QuestCategory.GENERAL_DAILY,
            parseBossColor(cfg.getString("bossbar.colors.general-daily", "YELLOW")));
        bossBarCategoryColors.put(QuestCategory.WEEKLY,
            parseBossColor(cfg.getString("bossbar.colors.weekly", "BLUE")));
        bossBarCategoryColors.put(QuestCategory.CHALLENGE,
            parseBossColor(cfg.getString("bossbar.colors.challenge", "PURPLE")));

        logger.info("[Quests] Configuration loaded (procedural generation mode).");
    }

    // --- Getters ---
    public boolean isEnabled()                  { return enabled; }
    public String getDailyResetTime()           { return dailyResetTime; }
    public String getWeeklyResetDay()           { return weeklyResetDay; }
    public boolean isNotifyOnAssign()           { return notifyOnAssign; }
    public boolean isNotifyOnComplete()         { return notifyOnComplete; }
    public boolean isNotifyOnStepComplete()     { return notifyOnStepComplete; }
    public boolean isAutoClaimRewards()         { return autoClaimRewards; }
    public double getDailyMoneyCap()            { return dailyMoneyCap; }
    public int getDailyItemRewardCap()          { return dailyItemRewardCap; }
    public int getChallengeRepeatBlockDays()    { return challengeRepeatBlockDays; }
    public double getMaxTier()                  { return maxTier; }
    public TierWeights getTierWeights()         { return tierWeights; }
    public double getDifficultyScale()          { return difficultyScale; }
    public double getRewardScale()              { return rewardScale; }

    // Boss bar getters
    public boolean isBossBarEnabled()                           { return bossBarEnabled; }
    public int getBossBarDurationSeconds()                      { return bossBarDurationSeconds; }
    public int getBossBarMaxBars()                              { return bossBarMaxBars; }
    public boolean isBossBarShowOnComplete()                    { return bossBarShowOnComplete; }
    public int getBossBarCompleteDurationSeconds()              { return bossBarCompleteDurationSeconds; }
    public String getBossBarFormat()                            { return bossBarFormat; }
    public String getBossBarCompleteFormat()                    { return bossBarCompleteFormat; }
    public BossBar.Overlay getBossBarOverlay()                  { return bossBarOverlay; }
    public Map<QuestCategory, BossBar.Color> getBossBarCategoryColors() { return bossBarCategoryColors; }

    public QuestCountConfig getQuestCount(QuestCategory category) {
        return questCounts.getOrDefault(category,
                new QuestCountConfig(1, 0.2, 4, 0));
    }

    // --- Records ---

    public record TierWeights(
            double playHoursWeight,
            double miningWeight,
            double miningPerHourCap,
            double combatWeight,
            double killsPerHourCap,
            double distanceWeight,
            double distanceCapKm,
            double craftingWeight,
            double craftingPerHourCap,
            double survivalWeight,
            double jobLevelWeight,
            double prestigeWeight,
            double explorationWeight,
            double explorationDivisor,
            double diversityWeight,
            int diversityBlocksThreshold,
            int diversityKillsThreshold,
            double diversityDistanceThreshold,
            int diversityCraftingThreshold,
            int diversityFishingThreshold,
            int diversityBreedingThreshold,
            double diversityJobLevelThreshold
    ) {}

    public record QuestCountConfig(
            int base,
            double perTier,
            int max,
            double minTier
    ) {
        public int calculateCount(double tier) {
            if (tier < minTier) return 0;
            return Math.min(max, (int) Math.floor(base + tier * perTier));
        }
    }

    private static BossBar.Color parseBossColor(String name) {
        try {
            return BossBar.Color.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.WHITE;
        }
    }
}
