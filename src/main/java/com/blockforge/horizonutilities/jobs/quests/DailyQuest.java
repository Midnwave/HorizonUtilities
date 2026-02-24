package com.blockforge.horizonutilities.jobs.quests;

import com.blockforge.horizonutilities.jobs.JobAction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * An active daily quest assigned to a specific player for a given date.
 * Mirrors the jobs_quests table row.
 */
public class DailyQuest {

    private int id;
    private UUID playerUuid;
    private String questId;
    private String jobId;
    private String description;
    private JobAction targetType;
    private String targetMaterial;    // nullable
    private int targetAmount;
    private int currentProgress;
    private double rewardMoney;
    private double rewardXp;
    private String assignedDate;      // yyyy-MM-dd
    private boolean completed;
    private Long completedAt;         // epoch ms, nullable

    public DailyQuest() {}

    public DailyQuest(int id,
                      UUID playerUuid,
                      String questId,
                      String jobId,
                      String description,
                      JobAction targetType,
                      String targetMaterial,
                      int targetAmount,
                      int currentProgress,
                      double rewardMoney,
                      double rewardXp,
                      String assignedDate,
                      boolean completed,
                      Long completedAt) {
        this.id = id;
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

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static DailyQuest fromResultSet(ResultSet rs) throws SQLException {
        DailyQuest q = new DailyQuest();
        q.id = rs.getInt("id");
        q.playerUuid = UUID.fromString(rs.getString("player_uuid"));
        q.questId = rs.getString("quest_id");
        q.jobId = rs.getString("job_id");
        q.description = rs.getString("description");
        q.targetType = JobAction.valueOf(rs.getString("target_type"));
        q.targetMaterial = rs.getString("target_material");
        q.targetAmount = rs.getInt("target_amount");
        q.currentProgress = rs.getInt("current_progress");
        q.rewardMoney = rs.getDouble("reward_money");
        q.rewardXp = rs.getDouble("reward_xp");
        q.assignedDate = rs.getString("assigned_date");
        q.completed = rs.getInt("completed") == 1;
        long ca = rs.getLong("completed_at");
        q.completedAt = rs.wasNull() ? null : ca;
        return q;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public boolean isComplete() {
        return completed || currentProgress >= targetAmount;
    }

    public double getProgressPercent() {
        if (targetAmount <= 0) return 100.0;
        return Math.min(100.0, (currentProgress / (double) targetAmount) * 100.0);
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public String getQuestId() { return questId; }
    public void setQuestId(String questId) { this.questId = questId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public JobAction getTargetType() { return targetType; }
    public void setTargetType(JobAction targetType) { this.targetType = targetType; }

    public String getTargetMaterial() { return targetMaterial; }
    public void setTargetMaterial(String targetMaterial) { this.targetMaterial = targetMaterial; }

    public int getTargetAmount() { return targetAmount; }
    public void setTargetAmount(int targetAmount) { this.targetAmount = targetAmount; }

    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) { this.currentProgress = currentProgress; }

    public double getRewardMoney() { return rewardMoney; }
    public void setRewardMoney(double rewardMoney) { this.rewardMoney = rewardMoney; }

    public double getRewardXp() { return rewardXp; }
    public void setRewardXp(double rewardXp) { this.rewardXp = rewardXp; }

    public String getAssignedDate() { return assignedDate; }
    public void setAssignedDate(String assignedDate) { this.assignedDate = assignedDate; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
}
