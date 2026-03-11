package com.blockforge.horizonutilities.quests.model;

import com.blockforge.horizonutilities.quests.QuestActionType;
import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;

import java.util.List;
import java.util.UUID;

/**
 * A quest instance assigned to a specific player.
 * Tracks multi-step progress toward completion.
 */
public class ActiveQuest {

    private final int dbId;
    private final UUID playerUuid;
    private final String templateId;
    private final QuestCategory category;
    private final String periodKey;
    private final long seed;
    private final List<ActiveStep> steps;
    private RewardDefinition scaledRewards;
    private int currentStepIndex;
    private boolean completed;
    private Long completedAt;
    private boolean rewardsClaimed;
    private final long assignedAt;
    private final String jobId;       // null if not job-specific
    private final String questName;   // procedurally generated display name

    public ActiveQuest(int dbId, UUID playerUuid, String templateId, QuestCategory category,
                       String periodKey, long seed, List<ActiveStep> steps,
                       RewardDefinition scaledRewards, int currentStepIndex,
                       boolean completed, Long completedAt, boolean rewardsClaimed, long assignedAt,
                       String jobId, String questName) {
        this.dbId = dbId;
        this.playerUuid = playerUuid;
        this.templateId = templateId;
        this.category = category;
        this.periodKey = periodKey;
        this.seed = seed;
        this.steps = steps;
        this.scaledRewards = scaledRewards;
        this.currentStepIndex = currentStepIndex;
        this.completed = completed;
        this.completedAt = completedAt;
        this.rewardsClaimed = rewardsClaimed;
        this.assignedAt = assignedAt;
        this.jobId = jobId;
        this.questName = questName;
    }

    /** Backwards-compatible constructor (jobId=null, questName from templateId). */
    public ActiveQuest(int dbId, UUID playerUuid, String templateId, QuestCategory category,
                       String periodKey, long seed, List<ActiveStep> steps,
                       RewardDefinition scaledRewards, int currentStepIndex,
                       boolean completed, Long completedAt, boolean rewardsClaimed, long assignedAt) {
        this(dbId, playerUuid, templateId, category, periodKey, seed, steps,
             scaledRewards, currentStepIndex, completed, completedAt, rewardsClaimed, assignedAt,
             null, templateId != null ? templateId.replace('_', ' ').replace('-', ' ') : "Quest");
    }

    // --- Identification ---
    public int getDbId()               { return dbId; }
    public UUID getPlayerUuid()        { return playerUuid; }
    public String getTemplateId()      { return templateId; }
    public QuestCategory getCategory() { return category; }
    public String getPeriodKey()       { return periodKey; }
    public long getSeed()              { return seed; }
    public long getAssignedAt()        { return assignedAt; }
    public String getJobId()           { return jobId; }
    public String getQuestName()       { return questName; }

    // --- Steps ---
    public List<ActiveStep> getSteps()      { return steps; }
    public int getCurrentStepIndex()        { return currentStepIndex; }
    public int getTotalSteps()              { return steps.size(); }

    public ActiveStep getCurrentStep() {
        if (completed || currentStepIndex >= steps.size()) return null;
        return steps.get(currentStepIndex);
    }

    // --- Rewards ---
    public RewardDefinition getScaledRewards()          { return scaledRewards; }
    public void setScaledRewards(RewardDefinition val)  { this.scaledRewards = val; }
    public boolean isRewardsClaimed()                   { return rewardsClaimed; }
    public void setRewardsClaimed(boolean val)          { this.rewardsClaimed = val; }

    // --- Completion ---
    public boolean isCompleted()     { return completed; }
    public Long getCompletedAt()     { return completedAt; }

    /**
     * Try to add progress to the current step. Returns true if the entire quest was just completed.
     */
    public boolean addProgress(QuestActionType action, String material, int amount) {
        if (completed) return false;
        ActiveStep step = getCurrentStep();
        if (step == null) return false;
        if (!step.matches(action, material)) return false;

        boolean stepDone = step.addProgress(amount);
        if (stepDone) {
            currentStepIndex++;
            if (currentStepIndex >= steps.size()) {
                completed = true;
                completedAt = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this quest has any step matching the given action and material.
     * Only checks the current step (quests are sequential).
     */
    public boolean canTrack(QuestActionType action, String material) {
        if (completed) return false;
        ActiveStep step = getCurrentStep();
        return step != null && step.matches(action, material);
    }

    public double getOverallProgressPercent() {
        if (completed) return 1.0;
        int totalWork = 0;
        int doneWork = 0;
        for (int i = 0; i < steps.size(); i++) {
            ActiveStep s = steps.get(i);
            totalWork += s.getTargetAmount();
            if (i < currentStepIndex) {
                doneWork += s.getTargetAmount();
            } else if (i == currentStepIndex) {
                doneWork += s.getCurrentProgress();
            }
        }
        return totalWork > 0 ? (double) doneWork / totalWork : 0;
    }

    // --- Inner class: ActiveStep ---

    public static class ActiveStep {
        private final int stepDbId;
        private final int stepIndex;
        private final QuestActionType actionType;
        private final String targetMaterial; // null = any
        private final int targetAmount;
        private int currentProgress;
        private boolean completed;
        private final String description;

        public ActiveStep(int stepDbId, int stepIndex, QuestActionType actionType,
                          String targetMaterial, int targetAmount, int currentProgress,
                          boolean completed, String description) {
            this.stepDbId = stepDbId;
            this.stepIndex = stepIndex;
            this.actionType = actionType;
            this.targetMaterial = targetMaterial;
            this.targetAmount = targetAmount;
            this.currentProgress = currentProgress;
            this.completed = completed;
            this.description = description;
        }

        public int getStepDbId()            { return stepDbId; }
        public int getStepIndex()           { return stepIndex; }
        public QuestActionType getActionType(){ return actionType; }
        public String getTargetMaterial()   { return targetMaterial; }
        public int getTargetAmount()        { return targetAmount; }
        public int getCurrentProgress()     { return currentProgress; }
        public boolean isCompleted()        { return completed; }
        public String getDescription()      { return description; }

        public boolean matches(QuestActionType action, String material) {
            if (completed) return false;
            if (actionType != action) return false;
            if (targetMaterial != null && !targetMaterial.equalsIgnoreCase(material)) return false;
            return true;
        }

        public boolean addProgress(int amount) {
            if (completed) return false;
            currentProgress = Math.min(currentProgress + amount, targetAmount);
            if (currentProgress >= targetAmount) {
                completed = true;
                return true;
            }
            return false;
        }

        public double getProgressPercent() {
            return targetAmount > 0 ? (double) currentProgress / targetAmount : 0;
        }
    }
}
