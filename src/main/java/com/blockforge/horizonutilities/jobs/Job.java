package com.blockforge.horizonutilities.jobs;

import com.blockforge.horizonutilities.jobs.quests.QuestDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Logger;

/**
 * Immutable definition of a job, loaded from jobs/&lt;id&gt;.yml.
 */
public class Job {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final String description;
    private final int maxLevel;

    /** action type -> (material/entity name -> entry) */
    private final Map<JobAction, Map<String, JobActionEntry>> actions;

    /** level milestone -> bonus multiplier added at that milestone */
    private final Map<Integer, Double> perks;

    /** -1 means fall back to the global income cap. */
    private final double hourlyIncomeCap;

    private final List<QuestDefinition> questPool;

    public Job(String id,
               String displayName,
               Material icon,
               String description,
               int maxLevel,
               Map<JobAction, Map<String, JobActionEntry>> actions,
               Map<Integer, Double> perks,
               double hourlyIncomeCap,
               List<QuestDefinition> questPool) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.maxLevel = maxLevel;
        this.actions = Collections.unmodifiableMap(actions);
        this.perks = Collections.unmodifiableMap(perks);
        this.hourlyIncomeCap = hourlyIncomeCap;
        this.questPool = Collections.unmodifiableList(questPool);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Loads a Job from a YAML FileConfiguration that was read from
     * {@code jobs/<jobId>.yml}.
     *
     * <pre>
     * display-name: "Miner"
     * icon: IRON_PICKAXE
     * description: "Mine ores for profit"
     * max-level: 100
     * hourly-income-cap: -1
     * actions:
     *   BREAK:
     *     STONE:
     *       money: 0.5
     *       xp: 1.0
     *       spawner-allowed: true
     * perks:
     *   10: 0.05
     *   25: 0.10
     * quests:
     *   - id: miner_daily_1
     *     description: "Mine 64 stone"
     *     action: BREAK
     *     material: STONE
     *     amount: 64
     *     reward-money: 50
     *     reward-xp: 100
     *     weight: 5
     * </pre>
     */
    public static Job loadFromConfig(String jobId, FileConfiguration config) {
        return loadFromConfig(jobId, config, null);
    }

    public static Job loadFromConfig(String jobId, FileConfiguration config, Logger logger) {
        String displayName = config.getString("display-name", jobId);
        String description = config.getString("description", "");
        int maxLevel = config.getInt("max-level", 100);
        double hourlyIncomeCap = config.getDouble("hourly-income-cap", -1.0);

        Material icon = Material.PAPER;
        String iconStr = config.getString("icon", "PAPER");
        try {
            icon = Material.valueOf(iconStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            if (logger != null) logger.warning("[Jobs] Unknown icon material '" + iconStr + "' for job " + jobId);
        }

        // Actions
        Map<JobAction, Map<String, JobActionEntry>> actions = new EnumMap<>(JobAction.class);
        ConfigurationSection actionsSection = config.getConfigurationSection("actions");
        if (actionsSection != null) {
            for (String actionKey : actionsSection.getKeys(false)) {
                JobAction action;
                try {
                    action = JobAction.valueOf(actionKey.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    if (logger != null) logger.warning("[Jobs] Unknown action '" + actionKey + "' in job " + jobId);
                    continue;
                }
                ConfigurationSection actionSection = actionsSection.getConfigurationSection(actionKey);
                if (actionSection == null) continue;

                Map<String, JobActionEntry> entries = new LinkedHashMap<>();
                for (String matKey : actionSection.getKeys(false)) {
                    ConfigurationSection entrySection = actionSection.getConfigurationSection(matKey);
                    double money = 0;
                    double xp = 0;
                    boolean spawnerAllowed = true;
                    if (entrySection != null) {
                        money = entrySection.getDouble("money", 0);
                        xp = entrySection.getDouble("xp", 0);
                        spawnerAllowed = entrySection.getBoolean("spawner-allowed", true);
                    } else {
                        // scalar shorthand: STONE: 0.5  (money only)
                        money = actionSection.getDouble(matKey, 0);
                    }
                    entries.put(matKey.toUpperCase(Locale.ROOT),
                            new JobActionEntry(matKey.toUpperCase(Locale.ROOT), money, xp, spawnerAllowed));
                }
                actions.put(action, entries);
            }
        }

        // Perks (milestones)
        Map<Integer, Double> perks = new TreeMap<>();
        ConfigurationSection perksSection = config.getConfigurationSection("perks");
        if (perksSection != null) {
            for (String levelKey : perksSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(levelKey);
                    double bonus = perksSection.getDouble(levelKey, 0);
                    perks.put(level, bonus);
                } catch (NumberFormatException ex) {
                    if (logger != null) logger.warning("[Jobs] Invalid perk level '" + levelKey + "' in job " + jobId);
                }
            }
        }

        // Quest pool (inline definitions)
        List<QuestDefinition> questPool = new ArrayList<>();
        List<Map<?, ?>> questList = config.getMapList("quests");
        for (Map<?, ?> qMapRaw : questList) { @SuppressWarnings("unchecked") Map<Object, Object> qMap = (Map<Object, Object>) qMapRaw;
            try {
                String questId = String.valueOf(qMap.get("id"));
                String desc = String.valueOf(qMap.getOrDefault("description", "Complete tasks"));
                String actionStr = String.valueOf(qMap.getOrDefault("action", "BREAK"));
                JobAction qAction = JobAction.valueOf(actionStr.toUpperCase(Locale.ROOT));
                String material = qMap.containsKey("material") ? String.valueOf(qMap.get("material")).toUpperCase(Locale.ROOT) : null;
                int amount = Integer.parseInt(String.valueOf(qMap.getOrDefault("amount", "1")));
                double rewardMoney = Double.parseDouble(String.valueOf(qMap.getOrDefault("reward-money", "0")));
                double rewardXp = Double.parseDouble(String.valueOf(qMap.getOrDefault("reward-xp", "0")));
                int weight = Integer.parseInt(String.valueOf(qMap.getOrDefault("weight", "1")));
                boolean enabled = Boolean.parseBoolean(String.valueOf(qMap.getOrDefault("enabled", "true")));
                questPool.add(new QuestDefinition(questId, jobId, desc, qAction, material, amount,
                        rewardMoney, rewardXp, weight, enabled));
            } catch (Exception ex) {
                if (logger != null) logger.warning("[Jobs] Failed to parse quest entry in job " + jobId + ": " + ex.getMessage());
            }
        }

        return new Job(jobId, displayName, icon, description, maxLevel, actions, perks, hourlyIncomeCap, questPool);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the action entry for a given action and material, or null if the
     * job does not pay for that combination.
     */
    public JobActionEntry getEntry(JobAction action, String material) {
        Map<String, JobActionEntry> map = actions.get(action);
        if (map == null) return null;
        return map.get(material.toUpperCase(Locale.ROOT));
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public String getDescription() { return description; }
    public int getMaxLevel() { return maxLevel; }
    public Map<JobAction, Map<String, JobActionEntry>> getActions() { return actions; }
    public Map<Integer, Double> getPerks() { return perks; }
    public double getHourlyIncomeCap() { return hourlyIncomeCap; }
    public List<QuestDefinition> getQuestPool() { return questPool; }
}
