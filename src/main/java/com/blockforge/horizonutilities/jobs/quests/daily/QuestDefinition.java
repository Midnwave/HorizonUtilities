package com.blockforge.horizonutilities.jobs.quests.daily;

import com.blockforge.horizonutilities.jobs.JobAction;

/**
 * A template for a quest that can be assigned to players.
 * Loaded from the quests.yml config or the jobs_quest_definitions table.
 */
public class QuestDefinition {

    private final String questId;
    private final String jobId;
    private final String description;
    private final JobAction actionType;
    private final String targetMaterial; // null = any
    private final int baseAmount;
    private final double baseMoneyReward;
    private final double baseXpReward;
    private final int weight;
    private final boolean enabled;

    public QuestDefinition(String questId, String jobId, String description,
                           JobAction actionType, String targetMaterial,
                           int baseAmount, double baseMoneyReward, double baseXpReward,
                           int weight, boolean enabled) {
        this.questId = questId;
        this.jobId = jobId;
        this.description = description;
        this.actionType = actionType;
        this.targetMaterial = targetMaterial;
        this.baseAmount = baseAmount;
        this.baseMoneyReward = baseMoneyReward;
        this.baseXpReward = baseXpReward;
        this.weight = weight;
        this.enabled = enabled;
    }

    public String getQuestId()        { return questId; }
    public String getJobId()          { return jobId; }
    public String getDescription()    { return description; }
    public JobAction getActionType()  { return actionType; }
    public String getTargetMaterial() { return targetMaterial; }
    public int getBaseAmount()        { return baseAmount; }
    public double getBaseMoneyReward(){ return baseMoneyReward; }
    public double getBaseXpReward()   { return baseXpReward; }
    public int getWeight()            { return weight; }
    public boolean isEnabled()        { return enabled; }
}
