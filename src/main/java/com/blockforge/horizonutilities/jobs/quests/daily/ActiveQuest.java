package com.blockforge.horizonutilities.jobs.quests.daily;

import com.blockforge.horizonutilities.jobs.JobAction;

import java.util.UUID;

/**
 * A quest instance assigned to a specific player.
 * Tracks progress toward the target amount.
 */
public class ActiveQuest {

    private final int dbId;
    private final UUID playerUuid;
    private final String questId;
    private final String jobId;
    private final String description;
    private final JobAction targetType;
    private final String targetMaterial; // null = any
    private final int targetAmount;
    private int currentProgress;
    private final double rewardMoney;
    private final double rewardXp;
    private final String assignedDate; // yyyy-MM-dd
    private boolean completed;
    private Long completedAt;

    public ActiveQuest(int dbId, UUID playerUuid, String questId, String jobId,
                       String description, JobAction targetType, String targetMaterial,
                       int targetAmount, int currentProgress,
                       double rewardMoney, double rewardXp,
                       String assignedDate, boolean completed, Long completedAt) {
        this.dbId = dbId;
        this.playerUuid = playerUuid;
        this.questId = questId;
        this.jobId = jobId;
        this.description = description;
        this.targetType = targetType;
        this.targetMaterial = targetMaterial;
        this.targetAmount = targetAmount;
        this.currentProgress = currentProgress;
        this.rewardMoney = rewardMoney;
        this.rewardXp = rewardXp;
        this.assignedDate = assignedDate;
        this.completed = completed;
        this.completedAt = completedAt;
    }

    public int getDbId()              { return dbId; }
    public UUID getPlayerUuid()       { return playerUuid; }
    public String getQuestId()        { return questId; }
    public String getJobId()          { return jobId; }
    public String getDescription()    { return description; }
    public JobAction getTargetType()  { return targetType; }
    public String getTargetMaterial() { return targetMaterial; }
    public int getTargetAmount()      { return targetAmount; }
    public int getCurrentProgress()   { return currentProgress; }
    public double getRewardMoney()    { return rewardMoney; }
    public double getRewardXp()       { return rewardXp; }
    public String getAssignedDate()   { return assignedDate; }
    public boolean isCompleted()      { return completed; }
    public Long getCompletedAt()      { return completedAt; }

    /**
     * Increment progress by the given amount. Returns true if quest was just completed.
     */
    public boolean addProgress(int amount) {
        if (completed) return false;
        currentProgress = Math.min(currentProgress + amount, targetAmount);
        if (currentProgress >= targetAmount) {
            completed = true;
            completedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public double getProgressPercent() {
        return targetAmount > 0 ? (double) currentProgress / targetAmount : 0;
    }

    /**
     * Check if this quest matches the given action and material.
     */
    public boolean matches(JobAction action, String material) {
        if (completed) return false;
        if (targetType != action) return false;
        if (targetMaterial != null && !targetMaterial.equalsIgnoreCase(material)) return false;
        return true;
    }
}
