package com.blockforge.horizonutilities.jobs.quests.daily;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles all database operations for the daily quest system.
 * Uses the shared connection from DatabaseManager (never close it).
 */
public class DailyQuestStorage {

    private final HorizonUtilitiesPlugin plugin;

    public DailyQuestStorage(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load all active (today's) quests for a player.
     */
    public List<ActiveQuest> loadPlayerQuests(UUID uuid, String todayDate) {
        List<ActiveQuest> quests = new ArrayList<>();
        String sql = "SELECT * FROM jobs_quests WHERE player_uuid = ? AND assigned_date = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, todayDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    quests.add(fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Quests] Failed to load quests for " + uuid, e);
        }
        return quests;
    }

    /**
     * Insert a new quest assignment for a player.
     */
    public int insertQuest(ActiveQuest quest) {
        String sql = """
            INSERT INTO jobs_quests (player_uuid, quest_id, job_id, description,
                target_type, target_material, target_amount, current_progress,
                reward_money, reward_xp, assigned_date, completed, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 0, NULL)""";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql,
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, quest.getPlayerUuid().toString());
            ps.setString(2, quest.getQuestId());
            ps.setString(3, quest.getJobId());
            ps.setString(4, quest.getDescription());
            ps.setString(5, quest.getTargetType().name());
            ps.setString(6, quest.getTargetMaterial());
            ps.setInt(7, quest.getTargetAmount());
            ps.setDouble(8, quest.getRewardMoney());
            ps.setDouble(9, quest.getRewardXp());
            ps.setString(10, quest.getAssignedDate());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Quests] Failed to insert quest", e);
        }
        return -1;
    }

    /**
     * Update progress for a quest.
     */
    public void updateProgress(int dbId, int progress, boolean completed, Long completedAt) {
        String sql = "UPDATE jobs_quests SET current_progress = ?, completed = ?, completed_at = ? WHERE id = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setInt(1, progress);
            ps.setInt(2, completed ? 1 : 0);
            if (completedAt != null) {
                ps.setLong(3, completedAt);
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setInt(4, dbId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Quests] Failed to update quest progress", e);
        }
    }

    /**
     * Count how many quests were completed by a player on a given date.
     */
    public int countCompletedOnDate(UUID uuid, String date) {
        String sql = "SELECT COUNT(*) FROM jobs_quests WHERE player_uuid = ? AND assigned_date = ? AND completed = 1";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Quests] Failed to count completed quests", e);
        }
        return 0;
    }

    /**
     * Get total quests completed by a player (all time).
     */
    public int getTotalCompleted(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM jobs_quests WHERE player_uuid = ? AND completed = 1";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Quests] Failed to count total completed", e);
        }
        return 0;
    }

    /**
     * Clean up old quest data older than the given number of days.
     */
    public void purgeOldQuests(int daysToKeep) {
        String sql = "DELETE FROM jobs_quests WHERE assigned_date < date('now', '-' || ? || ' days')";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setInt(1, daysToKeep);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("[Quests] Purged " + deleted + " old quest records.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Quests] Failed to purge old quests", e);
        }
    }

    private ActiveQuest fromResultSet(ResultSet rs) throws SQLException {
        JobAction action;
        try {
            action = JobAction.valueOf(rs.getString("target_type"));
        } catch (IllegalArgumentException e) {
            action = JobAction.BREAK;
        }
        long completedAtRaw = rs.getLong("completed_at");
        Long completedAt = rs.wasNull() ? null : completedAtRaw;

        return new ActiveQuest(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("quest_id"),
                rs.getString("job_id"),
                rs.getString("description"),
                action,
                rs.getString("target_material"),
                rs.getInt("target_amount"),
                rs.getInt("current_progress"),
                rs.getDouble("reward_money"),
                rs.getDouble("reward_xp"),
                rs.getString("assigned_date"),
                rs.getInt("completed") == 1,
                completedAt
        );
    }
}
