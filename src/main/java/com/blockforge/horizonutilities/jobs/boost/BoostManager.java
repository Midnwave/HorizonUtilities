package com.blockforge.horizonutilities.jobs.boost;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Manages active {@link BoostEvent} instances, persisting them to the
 * {@code jobs_boosts} table so they survive restarts.
 */
public class BoostManager {

    private final HorizonUtilitiesPlugin plugin;
    /** Thread-safe list; most operations read, few write. */
    private final List<BoostEvent> activeBoosts = new CopyOnWriteArrayList<>();

    public BoostManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Loads all currently-active boosts from the database. Call on startup.
     */
    public void loadFromDb() {
        activeBoosts.clear();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM jobs_boosts WHERE ends_at > ?")) {
            ps.setLong(1, now);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                activeBoosts.add(mapRow(rs));
            }
            plugin.getLogger().info("[Jobs] Loaded " + activeBoosts.size() + " active boost(s).");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to load boosts", e);
        }
    }

    /**
     * Starts a new boost, inserts it into the DB, and adds it to the in-memory
     * list.
     *
     * @param jobId       job id, or null for all-jobs boost
     * @param multiplier  e.g. 2.0 for double income
     * @param durationMs  boost duration in milliseconds
     * @param reason      human-readable reason shown in announcements
     * @param startedBy   UUID of the admin who started the boost
     * @return the created BoostEvent, or null if insertion failed
     */
    public BoostEvent startBoost(String jobId,
                                  double multiplier,
                                  long durationMs,
                                  String reason,
                                  UUID startedBy) {
        long now = System.currentTimeMillis();
        long endsAt = now + durationMs;
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO jobs_boosts (job_id, multiplier, started_by, started_at, ends_at, reason) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, jobId);
            ps.setDouble(2, multiplier);
            ps.setString(3, startedBy != null ? startedBy.toString() : "CONSOLE");
            ps.setLong(4, now);
            ps.setLong(5, endsAt);
            ps.setString(6, reason);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            int id = keys.next() ? keys.getInt(1) : -1;
            BoostEvent event = new BoostEvent(id, jobId, multiplier,
                    startedBy != null ? startedBy.toString() : "CONSOLE",
                    now, endsAt, reason);
            activeBoosts.add(event);
            return event;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Jobs] Failed to start boost", e);
            return null;
        }
    }

    /**
     * Returns the combined income multiplier for the given job, as the product
     * of all currently active boost multipliers that apply to it. Returns 1.0
     * if there are no active boosts.
     */
    public double getActiveMultiplier(String jobId) {
        double product = 1.0;
        long now = System.currentTimeMillis();
        for (BoostEvent boost : activeBoosts) {
            if (boost.getEndsAt() > now && boost.appliesTo(jobId)) {
                product *= boost.getMultiplier();
            }
        }
        return product;
    }

    /**
     * Removes expired boosts from the in-memory list. Does not touch the DB
     * (expired rows stay as history). Call this periodically (e.g. every
     * minute).
     */
    public void clearExpired() {
        activeBoosts.removeIf(BoostEvent::isExpired);
    }

    /** Returns an unmodifiable view of currently active boosts. */
    public List<BoostEvent> getActiveBoosts() {
        return Collections.unmodifiableList(activeBoosts);
    }

    /** Returns active boosts that apply to a specific job. */
    public List<BoostEvent> getBoostsForJob(String jobId) {
        List<BoostEvent> result = new ArrayList<>();
        for (BoostEvent b : activeBoosts) {
            if (b.isActive() && b.appliesTo(jobId)) result.add(b);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Connection conn() {
        return plugin.getDatabaseManager().getConnection();
    }

    private BoostEvent mapRow(ResultSet rs) throws SQLException {
        return new BoostEvent(
                rs.getInt("id"),
                rs.getString("job_id"),
                rs.getDouble("multiplier"),
                rs.getString("started_by"),
                rs.getLong("started_at"),
                rs.getLong("ends_at"),
                rs.getString("reason")
        );
    }
}
