package com.blockforge.horizonutilities.quests.model;

import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A quest template loaded from horizon-quests.yml.
 * Templates define what quests can be generated — they are not quests themselves.
 * The QuestGenerator picks a template, resolves targets via seed, and creates an ActiveQuest.
 */
public class QuestTemplate {

    private final String templateId;
    private final QuestCategory category;
    private final String jobId;           // null if not job-specific
    private final int weight;
    private final double minTier;
    private final List<QuestStep> steps;
    private final RewardDefinition rewards;

    public QuestTemplate(String templateId, QuestCategory category, String jobId,
                         int weight, double minTier, List<QuestStep> steps, RewardDefinition rewards) {
        this.templateId = templateId;
        this.category = category;
        this.jobId = jobId;
        this.weight = weight;
        this.minTier = minTier;
        this.steps = steps;
        this.rewards = rewards;
    }

    public String getTemplateId()      { return templateId; }
    public QuestCategory getCategory() { return category; }
    public String getJobId()           { return jobId; }
    public int getWeight()             { return weight; }
    public double getMinTier()         { return minTier; }
    public List<QuestStep> getSteps()  { return steps; }
    public RewardDefinition getRewards(){ return rewards; }

    public boolean isJobSpecific() {
        return jobId != null && !jobId.isEmpty();
    }

    public static QuestTemplate fromConfig(String templateId, ConfigurationSection section) {
        String catStr = section.getString("category", "GENERAL_DAILY");
        QuestCategory category;
        try {
            category = QuestCategory.valueOf(catStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            category = QuestCategory.GENERAL_DAILY;
        }

        String jobId = section.getString("job", null);
        int weight = section.getInt("weight", 1);
        double minTier = section.getDouble("min-tier", 0);

        List<QuestStep> steps = new ArrayList<>();
        if (section.isList("steps")) {
            List<?> stepList = section.getList("steps");
            if (stepList != null) {
                for (int i = 0; i < stepList.size(); i++) {
                    ConfigurationSection stepSec = section.getConfigurationSection("steps");
                    // steps is a list of maps in YAML — we access it via the parent
                }
            }
            // Handle list-of-maps format
            var stepsList = section.getMapList("steps");
            for (var stepMap : stepsList) {
                org.bukkit.configuration.MemoryConfiguration mc = new org.bukkit.configuration.MemoryConfiguration();
                for (var entry : stepMap.entrySet()) {
                    mc.set(String.valueOf(entry.getKey()), entry.getValue());
                }
                steps.add(QuestStep.fromConfig(mc));
            }
        }
        if (steps.isEmpty()) {
            // Fallback: single-step quest from top-level fields
            ConfigurationSection stepSec = section.getConfigurationSection("step");
            if (stepSec != null) {
                steps.add(QuestStep.fromConfig(stepSec));
            }
        }

        RewardDefinition rewards = RewardDefinition.fromConfig(section.getConfigurationSection("rewards"));

        return new QuestTemplate(templateId, category, jobId, weight, minTier, steps, rewards);
    }
}
