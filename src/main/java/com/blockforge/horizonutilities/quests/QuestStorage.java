package com.blockforge.horizonutilities.quests;

import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;
import com.blockforge.horizonutilities.storage.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles all database operations for the quest system.
 */
public class QuestStorage {

    private final DatabaseManager dbManager;
    private final Logger logger;

    public QuestStorage(DatabaseManager dbManager, Logger logger) {
        this.dbManager = dbManager;
        this.logger = logger;
    }

    // ========== QUEST CRUD ==========

    /**
     * Save a new quest and its steps. Returns the generated quest DB id.
     */
    public int saveQuest(ActiveQuest quest) {
        String insertQuest = "INSERT INTO horizon_quests " +
            "(player_uuid, template_id, category, period_key, seed, current_step, total_steps, " +
            "assigned_at, completed, completed_at, rewards_claimed, " +
            "reward_money, reward_job_xp, reward_xp_levels, reward_gems, job_id, quest_name) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String insertStep = "INSERT INTO horizon_quest_steps " +
            "(quest_id, step_index, action_type, target_material, target_amount, " +
            "current_progress, completed, description) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            Connection conn = dbManager.getConnection();
            conn.setAutoCommit(false);
            try {
                int questId;
                try (PreparedStatement ps = conn.prepareStatement(insertQuest, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, quest.getPlayerUuid().toString());
                    ps.setString(2, quest.getTemplateId());
                    ps.setString(3, quest.getCategory().name());
                    ps.setString(4, quest.getPeriodKey());
                    ps.setLong(5, quest.getSeed());
                    ps.setInt(6, quest.getCurrentStepIndex());
                    ps.setInt(7, quest.getTotalSteps());
                    ps.setLong(8, quest.getAssignedAt());
                    ps.setInt(9, quest.isCompleted() ? 1 : 0);
                    if (quest.getCompletedAt() != null) {
                        ps.setLong(10, quest.getCompletedAt());
                    } else {
                        ps.setNull(10, Types.INTEGER);
                    }
                    ps.setInt(11, quest.isRewardsClaimed() ? 1 : 0);
                    // Persist rewards directly in DB (procedural quests have no template to re-derive from)
                    RewardDefinition rewards = quest.getScaledRewards();
                    ps.setDouble(12, rewards != null ? rewards.getMoney() : 0);
                    ps.setDouble(13, rewards != null ? rewards.getJobXp() : 0);
                    ps.setInt(14, rewards != null ? rewards.getXpLevels() : 0);
                    ps.setInt(15, rewards != null ? rewards.getGems() : 0);
                    ps.setString(16, quest.getJobId());
                    ps.setString(17, quest.getQuestName());
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No generated key for quest insert");
                        questId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(insertStep)) {
                    for (ActiveQuest.ActiveStep step : quest.getSteps()) {
                        ps.setInt(1, questId);
                        ps.setInt(2, step.getStepIndex());
                        ps.setString(3, step.getActionType().name());
                        ps.setString(4, step.getTargetMaterial());
                        ps.setInt(5, step.getTargetAmount());
                        ps.setInt(6, step.getCurrentProgress());
                        ps.setInt(7, step.isCompleted() ? 1 : 0);
                        ps.setString(8, step.getDescription());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
                return questId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to save quest: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Update quest progress (current step index, completion status, step progress).
     */
    public void updateQuestProgress(ActiveQuest quest) {
        String updateQuest = "UPDATE horizon_quests SET current_step = ?, completed = ?, " +
            "completed_at = ?, rewards_claimed = ? WHERE id = ?";

        String updateStep = "UPDATE horizon_quest_steps SET current_progress = ?, completed = ? " +
            "WHERE quest_id = ? AND step_index = ?";

        try {
            Connection conn = dbManager.getConnection();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(updateQuest)) {
                    ps.setInt(1, quest.getCurrentStepIndex());
                    ps.setInt(2, quest.isCompleted() ? 1 : 0);
                    if (quest.getCompletedAt() != null) {
                        ps.setLong(3, quest.getCompletedAt());
                    } else {
                        ps.setNull(3, Types.INTEGER);
                    }
                    ps.setInt(4, quest.isRewardsClaimed() ? 1 : 0);
                    ps.setInt(5, quest.getDbId());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(updateStep)) {
                    for (ActiveQuest.ActiveStep step : quest.getSteps()) {
                        ps.setInt(1, step.getCurrentProgress());
                        ps.setInt(2, step.isCompleted() ? 1 : 0);
                        ps.setInt(3, quest.getDbId());
                        ps.setInt(4, step.getStepIndex());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to update quest progress: " + e.getMessage());
        }
    }

    /**
     * Load all active (non-expired) quests for a player in a given period.
     */
    public List<ActiveQuest> loadQuests(UUID playerUuid, QuestCategory category, String periodKey) {
        String sql = "SELECT * FROM horizon_quests WHERE player_uuid = ? AND category = ? AND period_key = ?";
        List<ActiveQuest> quests = new ArrayList<>();

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, category.name());
            ps.setString(3, periodKey);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ActiveQuest quest = readQuest(rs);
                    if (quest != null) quests.add(quest);
                }
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to load quests: " + e.getMessage());
        }
        return quests;
    }

    /**
     * Load ALL active quests for a player across all categories/periods.
     */
    public List<ActiveQuest> loadAllQuests(UUID playerUuid) {
        String sql = "SELECT * FROM horizon_quests WHERE player_uuid = ?";
        List<ActiveQuest> quests = new ArrayList<>();

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ActiveQuest quest = readQuest(rs);
                    if (quest != null) quests.add(quest);
                }
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to load all quests: " + e.getMessage());
        }
        return quests;
    }

    /**
     * Load steps for a specific quest.
     */
    private List<ActiveQuest.ActiveStep> loadSteps(int questId) {
        String sql = "SELECT * FROM horizon_quest_steps WHERE quest_id = ? ORDER BY step_index ASC";
        List<ActiveQuest.ActiveStep> steps = new ArrayList<>();

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, questId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    steps.add(new ActiveQuest.ActiveStep(
                        rs.getInt("id"),
                        rs.getInt("step_index"),
                        QuestActionType.valueOf(rs.getString("action_type")),
                        rs.getString("target_material"),
                        rs.getInt("target_amount"),
                        rs.getInt("current_progress"),
                        rs.getInt("completed") == 1,
                        rs.getString("description")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to load steps for quest " + questId + ": " + e.getMessage());
        }
        return steps;
    }

    private ActiveQuest readQuest(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String templateId = rs.getString("template_id");
        QuestCategory category = QuestCategory.valueOf(rs.getString("category"));
        String periodKey = rs.getString("period_key");
        long seed = rs.getLong("seed");
        int currentStep = rs.getInt("current_step");
        long assignedAt = rs.getLong("assigned_at");
        boolean completed = rs.getInt("completed") == 1;
        Long completedAt = rs.getObject("completed_at") != null ? rs.getLong("completed_at") : null;
        boolean rewardsClaimed = rs.getInt("rewards_claimed") == 1;

        // Rewards stored directly in DB (procedural quests have no template)
        double rewardMoney = rs.getDouble("reward_money");
        double rewardJobXp = rs.getDouble("reward_job_xp");
        int rewardXpLevels = rs.getInt("reward_xp_levels");
        int rewardGems = rs.getInt("reward_gems");
        RewardDefinition rewards = new RewardDefinition(rewardMoney, rewardJobXp, rewardXpLevels, rewardGems, List.of());

        String jobId = rs.getString("job_id");
        String questName = rs.getString("quest_name");

        List<ActiveQuest.ActiveStep> steps = loadSteps(id);

        return new ActiveQuest(id, playerUuid, templateId, category, periodKey, seed,
            steps, rewards, currentStep, completed, completedAt, rewardsClaimed, assignedAt,
            jobId, questName != null ? questName : "Quest");
    }

    // ========== DELETION / CLEANUP ==========

    /**
     * Delete all quests for a player in a given category + period (used on reset).
     */
    public void deleteQuests(UUID playerUuid, QuestCategory category, String periodKey) {
        String sql = "DELETE FROM horizon_quests WHERE player_uuid = ? AND category = ? AND period_key = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, category.name());
            ps.setString(3, periodKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to delete quests: " + e.getMessage());
        }
    }

    /**
     * Delete all quests for a player (admin reset).
     */
    public void deleteAllQuests(UUID playerUuid) {
        String sql = "DELETE FROM horizon_quests WHERE player_uuid = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to delete all quests: " + e.getMessage());
        }
    }

    /**
     * Purge old completed quests (housekeeping). Keeps recent N days.
     */
    public void purgeOldQuests(int keepDays) {
        long cutoff = System.currentTimeMillis() - (keepDays * 86_400_000L);
        String sql = "DELETE FROM horizon_quests WHERE completed = 1 AND completed_at < ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                logger.info("[Quests] Purged " + deleted + " old completed quests.");
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to purge old quests: " + e.getMessage());
        }
    }

    // ========== REWARD AUDIT ==========

    /**
     * Log a reward grant for audit and daily cap tracking.
     */
    public void logReward(int questId, UUID playerUuid, String rewardType, double amount, String details) {
        String sql = "INSERT INTO horizon_quest_rewards " +
            "(quest_id, player_uuid, reward_type, amount, details, granted_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, questId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, rewardType);
            ps.setDouble(4, amount);
            ps.setString(5, details);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to log reward: " + e.getMessage());
        }
    }

    /**
     * Get total money rewarded to a player today (for daily cap).
     */
    public double getTodayMoneyRewarded(UUID playerUuid, long dayStartMillis) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM horizon_quest_rewards " +
            "WHERE player_uuid = ? AND reward_type = 'MONEY' AND granted_at >= ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, dayStartMillis);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to get today's money rewarded: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Get count of item rewards granted today (for daily cap).
     */
    public int getTodayItemRewardsCount(UUID playerUuid, long dayStartMillis) {
        String sql = "SELECT COUNT(*) FROM horizon_quest_rewards " +
            "WHERE player_uuid = ? AND reward_type = 'ITEM' AND granted_at >= ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, dayStartMillis);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to get today's item reward count: " + e.getMessage());
        }
        return 0;
    }

    // ========== CHALLENGE HISTORY ==========

    /**
     * Record a challenge quest completion for repeat-block tracking.
     */
    public void recordChallengeCompletion(UUID playerUuid, String templateId) {
        String sql = "INSERT INTO horizon_quest_challenge_history (player_uuid, template_id, completed_at) " +
            "VALUES (?, ?, ?)";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, templateId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to record challenge completion: " + e.getMessage());
        }
    }

    /**
     * Get template IDs of challenges completed within the block period.
     */
    public List<String> getRecentChallenges(UUID playerUuid, int blockDays) {
        long cutoff = System.currentTimeMillis() - (blockDays * 86_400_000L);
        String sql = "SELECT DISTINCT template_id FROM horizon_quest_challenge_history " +
            "WHERE player_uuid = ? AND completed_at >= ?";

        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, cutoff);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("template_id"));
                }
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to get recent challenges: " + e.getMessage());
        }
        return result;
    }

    // ========== PLAYER STATS (CACHED TIER) ==========

    /**
     * Save or update the player's cached tier value.
     */
    public void saveCachedTier(UUID playerUuid, double tier) {
        String sql = "INSERT INTO horizon_player_stats (player_uuid, cached_tier, last_updated) " +
            "VALUES (?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET cached_tier = ?, last_updated = ?";

        long now = System.currentTimeMillis();
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setDouble(2, tier);
            ps.setLong(3, now);
            ps.setDouble(4, tier);
            ps.setLong(5, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to save cached tier: " + e.getMessage());
        }
    }

    /**
     * Get the player's cached tier, or -1 if not cached.
     */
    public double getCachedTier(UUID playerUuid) {
        String sql = "SELECT cached_tier FROM horizon_player_stats WHERE player_uuid = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("cached_tier");
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to get cached tier: " + e.getMessage());
        }
        return -1;
    }

    // ========== SCOREBOARD PREFERENCES ==========

    /**
     * Save scoreboard preference.
     */
    public void saveScoreboardPrefs(UUID playerUuid, boolean enabled, String mode) {
        String sql = "INSERT INTO horizon_scoreboard_prefs (player_uuid, scoreboard_enabled, scoreboard_mode) " +
            "VALUES (?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET scoreboard_enabled = ?, scoreboard_mode = ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, enabled ? 1 : 0);
            ps.setString(3, mode);
            ps.setInt(4, enabled ? 1 : 0);
            ps.setString(5, mode);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to save scoreboard prefs: " + e.getMessage());
        }
    }

    /**
     * Load scoreboard preference. Returns [enabled, mode] or null if not set.
     */
    public String[] loadScoreboardPrefs(UUID playerUuid) {
        String sql = "SELECT scoreboard_enabled, scoreboard_mode FROM horizon_scoreboard_prefs WHERE player_uuid = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[] {
                        String.valueOf(rs.getInt("scoreboard_enabled") == 1),
                        rs.getString("scoreboard_mode")
                    };
                }
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to load scoreboard prefs: " + e.getMessage());
        }
        return null;
    }

    // ========== RTP COOLDOWNS ==========

    /**
     * Save RTP last-used timestamp.
     */
    public void saveRtpCooldown(UUID playerUuid, long lastUsed) {
        String sql = "INSERT INTO horizon_rtp_cooldowns (player_uuid, last_used) " +
            "VALUES (?, ?) ON CONFLICT(player_uuid) DO UPDATE SET last_used = ?";

        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, lastUsed);
            ps.setLong(3, lastUsed);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to save RTP cooldown: " + e.getMessage());
        }
    }

    /**
     * Get RTP last-used timestamp, or 0 if never used.
     */
    public long getRtpCooldown(UUID playerUuid) {
        String sql = "SELECT last_used FROM horizon_rtp_cooldowns WHERE player_uuid = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("last_used");
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to get RTP cooldown: " + e.getMessage());
        }
        return 0;
    }

    // ========== STATISTICS ==========

    /**
     * Get total completed quests for a player all-time.
     */
    public int getTotalCompleted(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM horizon_quests WHERE player_uuid = ? AND completed = 1";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to get total completed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Check if quests already exist for a player + category + period.
     */
    public boolean hasQuestsForPeriod(UUID playerUuid, QuestCategory category, String periodKey) {
        String sql = "SELECT COUNT(*) FROM horizon_quests WHERE player_uuid = ? AND category = ? AND period_key = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, category.name());
            ps.setString(3, periodKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.severe("[Quests] Failed to check quest existence: " + e.getMessage());
        }
        return false;
    }
}
