package com.blockforge.horizonutilities.jobs.config;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Loads and exposes all settings from {@code jobs-config.yml}.
 */
public class JobsConfig {

    private final HorizonUtilitiesPlugin plugin;

    // General
    private int maxConcurrentJobs;
    private int maxLevel;
    private int maxPrestige;
    private double prestigeMultiplier;

    // Payment
    private PaymentMode paymentMode;

    // XP
    private double xpBase;
    private double xpExponent;

    // Anti-exploit
    private boolean blockTracking;
    private double spawnerPayMultiplier;
    private int areaFarmingRadius;
    private int areaFarmingMaxActions;
    private long areaFarmingTimeframeMs;
    private long actionCooldownMs;
    private boolean incomeCapEnabled;
    private double incomeCapDefault;

    // Exploration
    private ExploreMode exploreMode;
    private double exploreChunkReward;
    private double exploreDistancePerBlock;
    private int exploreDistanceThreshold;

    // Quests
    private int questDailyCount;
    private int questResetHour;

    // Tax
    private boolean taxEnabled;
    private double taxDefaultRate;
    private String taxExemptPermission;
    private boolean taxNotifyPlayer;
    private boolean taxNotifyOwner;

    // Perk milestones
    private Map<Integer, Double> perkMilestones;

    public enum PaymentMode { PER_ACTION, PERIODIC }
    public enum ExploreMode { CHUNK_DISCOVERY, DISTANCE }

    public JobsConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "jobs-config.yml");
        if (!file.exists()) {
            plugin.saveResource("jobs-config.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        maxConcurrentJobs   = cfg.getInt("general.max-concurrent-jobs", 3);
        maxLevel            = cfg.getInt("general.max-level", 100);
        maxPrestige         = cfg.getInt("general.max-prestige", 5);
        prestigeMultiplier  = cfg.getDouble("general.prestige-multiplier", 0.10);

        String pmStr = cfg.getString("payment.mode", "PER_ACTION").toUpperCase(Locale.ROOT);
        paymentMode = safeEnum(PaymentMode.class, pmStr, PaymentMode.PER_ACTION);

        xpBase     = cfg.getDouble("xp.base", 100.0);
        xpExponent = cfg.getDouble("xp.exponent", 1.8);

        blockTracking           = cfg.getBoolean("anti-exploit.block-tracking", true);
        spawnerPayMultiplier    = cfg.getDouble("anti-exploit.spawner-pay-multiplier", 0.25);
        areaFarmingRadius       = cfg.getInt("anti-exploit.area-farming.radius", 8);
        areaFarmingMaxActions   = cfg.getInt("anti-exploit.area-farming.max-actions", 20);
        areaFarmingTimeframeMs  = cfg.getLong("anti-exploit.area-farming.timeframe-ms", 10000L);
        actionCooldownMs        = cfg.getLong("anti-exploit.action-cooldown-ms", 0L);
        incomeCapEnabled        = cfg.getBoolean("anti-exploit.income-cap.enabled", true);
        incomeCapDefault        = cfg.getDouble("anti-exploit.income-cap.default", 1000.0);

        String emStr = cfg.getString("explore.mode", "CHUNK_DISCOVERY").toUpperCase(Locale.ROOT);
        exploreMode               = safeEnum(ExploreMode.class, emStr, ExploreMode.CHUNK_DISCOVERY);
        exploreChunkReward        = cfg.getDouble("explore.chunk-reward", 5.0);
        exploreDistancePerBlock   = cfg.getDouble("explore.distance-per-block", 0.05);
        exploreDistanceThreshold  = cfg.getInt("explore.distance-threshold-blocks", 10);

        questDailyCount = cfg.getInt("quests.daily-count", 3);
        questResetHour  = cfg.getInt("quests.reset-hour", 0);

        taxEnabled          = cfg.getBoolean("tax.enabled", true);
        taxDefaultRate      = cfg.getDouble("tax.default-rate", 0.10);
        taxExemptPermission = cfg.getString("tax.exempt-permission", "horizonutilities.jobs.tax.exempt");
        taxNotifyPlayer     = cfg.getBoolean("tax.notify-player", true);
        taxNotifyOwner      = cfg.getBoolean("tax.notify-owner", false);

        perkMilestones = new TreeMap<>();
        var perksSection = cfg.getConfigurationSection("perks");
        if (perksSection != null) {
            for (String key : perksSection.getKeys(false)) {
                try {
                    perkMilestones.put(Integer.parseInt(key), perksSection.getDouble(key));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static <E extends Enum<E>> E safeEnum(Class<E> clazz, String value, E fallback) {
        try { return Enum.valueOf(clazz, value); } catch (Exception e) { return fallback; }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int getMaxConcurrentJobs()         { return maxConcurrentJobs; }
    public int getMaxLevel()                  { return maxLevel; }
    public int getMaxPrestige()               { return maxPrestige; }
    public double getPrestigeMultiplier()     { return prestigeMultiplier; }
    public PaymentMode getPaymentMode()       { return paymentMode; }
    public double getXpBase()                 { return xpBase; }
    public double getXpExponent()             { return xpExponent; }
    public boolean isBlockTracking()          { return blockTracking; }
    public double getSpawnerPayMultiplier()   { return spawnerPayMultiplier; }
    public int getAreaFarmingRadius()         { return areaFarmingRadius; }
    public int getAreaFarmingMaxActions()     { return areaFarmingMaxActions; }
    public long getAreaFarmingTimeframeMs()   { return areaFarmingTimeframeMs; }
    public long getActionCooldownMs()         { return actionCooldownMs; }
    public boolean isIncomeCapEnabled()       { return incomeCapEnabled; }
    public double getIncomeCapDefault()       { return incomeCapDefault; }
    public ExploreMode getExploreMode()       { return exploreMode; }
    public double getExploreChunkReward()     { return exploreChunkReward; }
    public double getExploreDistancePerBlock(){ return exploreDistancePerBlock; }
    public int getExploreDistanceThreshold()  { return exploreDistanceThreshold; }
    public int getQuestDailyCount()           { return questDailyCount; }
    public int getQuestResetHour()            { return questResetHour; }
    public boolean isTaxEnabled()             { return taxEnabled; }
    public double getTaxDefaultRate()         { return taxDefaultRate; }
    public String getTaxExemptPermission()    { return taxExemptPermission; }
    public boolean isTaxNotifyPlayer()        { return taxNotifyPlayer; }
    public boolean isTaxNotifyOwner()         { return taxNotifyOwner; }
    public Map<Integer, Double> getPerkMilestones() { return Collections.unmodifiableMap(perkMilestones); }
}
