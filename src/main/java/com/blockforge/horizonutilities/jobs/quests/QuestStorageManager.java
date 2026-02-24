package com.blockforge.horizonutilities.jobs.quests;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite CRUD operations for the {@code jobs_quests} and
 * {@code jobs_quest_definitions} tables.
 */
public class QuestStorageManager {

    private final HorizonUtilitiesPlugin plugin;

    public QuestStorageManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private Connection conn() {
        return plugin.getDatabaseManager().getConnection();
    }

    // -------------------------------------------------------------------------
    // Active quest queries
    // -------------------------------------------------------------------------

    /**
     * Returns all active (and completed) quests assigned to the player on the
     * given date string (yyyy-MM-dd).
     */
    public List<DailyQuest> getActiveQuests(UUID playerUuid, String date) {
        List<DailyQuest> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM jobs_quests WHERE player_uuid = ? AND assigned_date = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, date);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(DailyQuest.fromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to load quests for " + playerUuid, e);
        }
        return result;
    }

    /**
     * Inserts a new DailyQuest row and returns the generated auto-increment id.
     */
    public int insertQuest(DailyQuest quest) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO jobs_quests " +
                "(player_uuid, quest_id, job_id, description, target_type, target_material, " +
                "target_amount, current_progress, reward_money, reward_xp, assigned_date, completed) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
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
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to insert quest", e);
            return -1;
        }
    }

    /**
     * Updates the progress value for a quest row.
     */
    public void updateProgress(int questId, int progress) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE jobs_quests SET current_progress = ? WHERE id = ?")) {
            ps.setInt(1, progress);
            ps.setInt(2, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to update quest progress", e);
        }
    }

    /**
     * Marks a quest as completed with the given timestamp.
     */
    public void completeQuest(int questId, long completedAt) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE jobs_quests SET completed = 1, completed_at = ?, current_progress = target_amount WHERE id = ?")) {
            ps.setLong(1, completedAt);
            ps.setInt(2, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to mark quest complete", e);
        }
    }

    // -------------------------------------------------------------------------
    // Quest definition CRUD
    // -------------------------------------------------------------------------

    /**
     * Returns all quest definitions for a given job (or all if jobId is null).
     */
    public List<QuestDefinition> getAllQuestDefinitions(String jobId) {
        List<QuestDefinition> result = new ArrayList<>();
        String sql = jobId == null
                ? "SELECT * FROM jobs_quest_definitions"
                : "SELECT * FROM jobs_quest_definitions WHERE job_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (jobId != null) ps.setString(1, jobId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(mapDefinition(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to load quest definitions", e);
        }
        return result;
    }

    /**
     * Upserts a QuestDefinition record.
     */
    public void saveQuestDefinition(QuestDefinition def) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO jobs_quest_definitions " +
                "(quest_id, job_id, description, action_type, target_material, target_amount, " +
                "reward_money, reward_xp, weight, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(quest_id) DO UPDATE SET " +
                "job_id=excluded.job_id, description=excluded.description, " +
                "action_type=excluded.action_type, target_material=excluded.target_material, " +
                "target_amount=excluded.target_amount, reward_money=excluded.reward_money, " +
                "reward_xp=excluded.reward_xp, weight=excluded.weight, enabled=excluded.enabled")) {
            ps.setString(1, def.getQuestId());
            ps.setString(2, def.getJobId());
            ps.setString(3, def.getDescription());
            ps.setString(4, def.getActionType().name());
            ps.setString(5, def.getTargetMaterial());
            ps.setInt(6, def.getTargetAmount());
            ps.setDouble(7, def.getRewardMoney());
            ps.setDouble(8, def.getRewardXp());
            ps.setInt(9, def.getWeight());
            ps.setInt(10, def.isEnabled() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to save quest definition", e);
        }
    }

    /**
     * Deletes a quest definition by id.
     */
    public void deleteQuestDefinition(String questId) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM jobs_quest_definitions WHERE quest_id = ?")) {
            ps.setString(1, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to delete quest definition", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private QuestDefinition mapDefinition(ResultSet rs) throws SQLException {
        return new QuestDefinition(
                rs.getString("quest_id"),
                rs.getString("job_id"),
                rs.getString("description"),
                JobAction.valueOf(rs.getString("action_type")),
                rs.getString("target_material"),
                rs.getInt("target_amount"),
                rs.getDouble("reward_money"),
                rs.getDouble("reward_xp"),
                rs.getInt("weight"),
                rs.getInt("enabled") == 1
        );
    }
}
