package com.blockforge.horizonutilities.jobs.quests;

import com.blockforge.horizonutilities.jobs.JobAction;

/**
 * Immutable template for a quest that can be assigned to a player.
 * Stored in jobs_quest_definitions table and in job YAML quest pools.
 */
public class QuestDefinition {

    private final String questId;
    private final String jobId;
    private final String description;
    private final JobAction actionType;
    private final String targetMaterial;  // nullable — null means "any material"
    private final int targetAmount;
    private final double rewardMoney;
    private final double rewardXp;
    private final int weight;             // higher weight = higher chance of selection
    private final boolean enabled;

    public QuestDefinition(String questId,
                           String jobId,
                           String description,
                           JobAction actionType,
                           String targetMaterial,
                           int targetAmount,
                           double rewardMoney,
                           double rewardXp,
                           int weight,
                           boolean enabled) {
        this.questId = questId;
        this.jobId = jobId;
        this.description = description;
        this.actionType = actionType;
        this.targetMaterial = targetMaterial;
        this.targetAmount = targetAmount;
        this.rewardMoney = rewardMoney;
        this.rewardXp = rewardXp;
        this.weight = weight;
        this.enabled = enabled;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getQuestId() { return questId; }
    public String getJobId() { return jobId; }
    public String getDescription() { return description; }
    public JobAction getActionType() { return actionType; }
    public String getTargetMaterial() { return targetMaterial; }
    public int getTargetAmount() { return targetAmount; }
    public double getRewardMoney() { return rewardMoney; }
    public double getRewardXp() { return rewardXp; }
    public int getWeight() { return weight; }
    public boolean isEnabled() { return enabled; }

    @Override
    public String toString() {
        return "QuestDefinition{id='" + questId + "', job='" + jobId
                + "', action=" + actionType + ", material='" + targetMaterial
                + "', amount=" + targetAmount + '}';
    }
}
