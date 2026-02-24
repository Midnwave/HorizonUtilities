package com.blockforge.horizonutilities.jobs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Mutable per-player job data object. Mirrors the jobs_players table row.
 */
public class JobPlayer {

    private UUID playerUuid;
    private String playerName;
    private String jobId;
    private int level;
    private double xp;
    private int prestige;
    private double totalEarned;
    private long joinedAt;
    private long lastActive;

    public JobPlayer(UUID playerUuid,
                     String playerName,
                     String jobId,
                     int level,
                     double xp,
                     int prestige,
                     double totalEarned,
                     long joinedAt,
                     long lastActive) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.jobId = jobId;
        this.level = level;
        this.xp = xp;
        this.prestige = prestige;
        this.totalEarned = totalEarned;
        this.joinedAt = joinedAt;
        this.lastActive = lastActive;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Constructs a JobPlayer from a ResultSet row from {@code jobs_players}.
     * The cursor must already be positioned on the desired row (rs.next() must
     * have returned true before calling this).
     */
    public static JobPlayer fromResultSet(ResultSet rs) throws SQLException {
        return new JobPlayer(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("job_id"),
                rs.getInt("level"),
                rs.getDouble("xp"),
                rs.getInt("prestige"),
                rs.getDouble("total_earned"),
                rs.getLong("joined_at"),
                rs.getLong("last_active")
        );
    }

    /**
     * Returns the values in the order expected by the INSERT / UPSERT statement
     * in {@code JobStorageManager}.
     * <p>
     * Order: player_uuid, player_name, job_id, level, xp, prestige,
     * total_earned, joined_at, last_active
     */
    public Object[] toInsertValues() {
        return new Object[]{
                playerUuid.toString(),
                playerName,
                jobId,
                level,
                xp,
                prestige,
                totalEarned,
                joinedAt,
                lastActive
        };
    }

    // -------------------------------------------------------------------------
    // Mutation helpers
    // -------------------------------------------------------------------------

    public void addXp(double amount) {
        this.xp += amount;
    }

    public void addEarned(double amount) {
        this.totalEarned += amount;
    }

    public void touch() {
        this.lastActive = System.currentTimeMillis();
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public double getXp() { return xp; }
    public void setXp(double xp) { this.xp = xp; }

    public int getPrestige() { return prestige; }
    public void setPrestige(int prestige) { this.prestige = prestige; }

    public double getTotalEarned() { return totalEarned; }
    public void setTotalEarned(double totalEarned) { this.totalEarned = totalEarned; }

    public long getJoinedAt() { return joinedAt; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }

    public long getLastActive() { return lastActive; }
    public void setLastActive(long lastActive) { this.lastActive = lastActive; }

    @Override
    public String toString() {
        return "JobPlayer{player=" + playerName + ", job=" + jobId
                + ", level=" + level + ", xp=" + xp + ", prestige=" + prestige + '}';
    }
}
