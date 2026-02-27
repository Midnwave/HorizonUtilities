package com.blockforge.horizonutilities.jobs;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * All SQLite operations for the jobs system. All public methods are safe to
 * call from async threads; callers should ensure they do so via
 * {@code Bukkit.getScheduler().runTaskAsynchronously}.
 */
public class JobStorageManager {

    private static final DateTimeFormatter HOUR_KEY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH").withZone(ZoneOffset.UTC);

    private final HorizonUtilitiesPlugin plugin;

    public JobStorageManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private Connection conn() {
        return plugin.getDatabaseManager().getConnection();
    }

    // -------------------------------------------------------------------------
    // Player job CRUD
    // -------------------------------------------------------------------------

    /**
     * Returns all job records for the given player.
     * Retries once on connection failure to handle stale/broken connections.
     */
    public List<JobPlayer> loadPlayerJobs(UUID playerUuid) {
        for (int attempt = 0; attempt < 2; attempt++) {
            List<JobPlayer> result = new ArrayList<>();
            try (PreparedStatement ps = conn().prepareStatement(
                    "SELECT * FROM jobs_players WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(JobPlayer.fromResultSet(rs));
                    }
                }
                return result;
            } catch (SQLException e) {
                if (attempt == 0) {
                    plugin.getLogger().info("[Jobs] Database error loading jobs, retrying...");
                } else {
                    plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to load player jobs for " + playerUuid, e);
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * Upserts the given JobPlayer record.
     */
    public void savePlayerJob(JobPlayer jp) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO jobs_players " +
                "(player_uuid, player_name, job_id, level, xp, prestige, total_earned, joined_at, last_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(player_uuid, job_id) DO UPDATE SET " +
                "player_name=excluded.player_name, level=excluded.level, xp=excluded.xp, " +
                "prestige=excluded.prestige, total_earned=excluded.total_earned, last_active=excluded.last_active")) {
            Object[] vals = jp.toInsertValues();
            for (int i = 0; i < vals.length; i++) {
                ps.setObject(i + 1, vals[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to save player job: " + jp, e);
        }
    }

    /**
     * Removes a player from a specific job.
     */
    public void deletePlayerJob(UUID playerUuid, String jobId) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM jobs_players WHERE player_uuid = ? AND job_id = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, jobId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to delete player job", e);
        }
    }

    // -------------------------------------------------------------------------
    // Leaderboard queries
    // -------------------------------------------------------------------------

    /**
     * Returns the top {@code limit} players in a specific job, ordered by
     * level descending then xp descending.
     */
    public List<JobPlayer> getTopPlayers(String jobId, int limit) {
        List<JobPlayer> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM jobs_players WHERE job_id = ? " +
                "ORDER BY prestige DESC, level DESC, xp DESC LIMIT ?")) {
            ps.setString(1, jobId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(JobPlayer.fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to get top players for " + jobId, e);
        }
        return result;
    }

    /**
     * Returns the top {@code limit} players ranked by sum of levels across all
     * jobs. Returns entries of playerName -> totalLevel.
     */
    public List<Map.Entry<String, Integer>> getTopOverall(int limit) {
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT player_name, SUM(level) AS total_level " +
                "FROM jobs_players GROUP BY player_uuid " +
                "ORDER BY total_level DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(Map.entry(rs.getString("player_name"), rs.getInt("total_level")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to get overall top players", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Income cap tracking
    // -------------------------------------------------------------------------

    /**
     * Adds {@code amount} to the current-hour tracking row for the player/job.
     */
    public void trackIncome(UUID playerUuid, String jobId, double amount) {
        String hourKey = HOUR_KEY_FMT.format(Instant.now());
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO jobs_income_tracking (player_uuid, job_id, hour_key, earned) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(player_uuid, job_id, hour_key) DO UPDATE SET earned = earned + excluded.earned")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, jobId);
            ps.setString(3, hourKey);
            ps.setDouble(4, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to track income", e);
        }
    }

    /**
     * Returns how much the player has earned in this hour for the given job.
     */
    public double getHourlyEarned(UUID playerUuid, String jobId) {
        String hourKey = HOUR_KEY_FMT.format(Instant.now());
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT earned FROM jobs_income_tracking " +
                "WHERE player_uuid = ? AND job_id = ? AND hour_key = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, jobId);
            ps.setString(3, hourKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("earned");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to get hourly earned", e);
        }
        return 0.0;
    }

    /**
     * Deletes income-tracking entries older than 48 hours to keep the table
     * small. Call this from a daily maintenance task.
     */
    public void cleanOldIncomeTracking() {
        // hour_key format is yyyy-MM-dd'T'HH; anything with a date 2 days old is safe to remove.
        Instant cutoff = Instant.now().minusSeconds(48 * 3600);
        String cutoffKey = HOUR_KEY_FMT.format(cutoff);
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM jobs_income_tracking WHERE hour_key < ?")) {
            ps.setString(1, cutoffKey);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("[Jobs] Cleaned " + deleted + " old income tracking rows.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to clean income tracking", e);
        }
    }
}
