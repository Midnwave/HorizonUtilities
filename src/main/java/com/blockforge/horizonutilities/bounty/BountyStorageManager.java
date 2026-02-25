package com.blockforge.horizonutilities.bounty;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite CRUD operations for the bounties table.
 */
public class BountyStorageManager {

    private final HorizonUtilitiesPlugin plugin;

    public BountyStorageManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Insert
    // -------------------------------------------------------------------------

    /**
     * Inserts a new bounty and returns the generated database ID.
     * Returns -1 on failure.
     */
    public int placeBounty(Bounty bounty) {
        String sql = "INSERT INTO bounties (target_uuid, target_name, setter_uuid, setter_name, " +
                     "amount, anonymous, created_at, expires_at, status) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, bounty.getTargetUuid().toString());
            ps.setString(2, bounty.getTargetName());
            ps.setString(3, bounty.getSetterUuid().toString());
            ps.setString(4, bounty.getSetterName());
            ps.setDouble(5, bounty.getAmount());
            ps.setInt(6, bounty.isAnonymous() ? 1 : 0);
            ps.setLong(7, bounty.getCreatedAt());
            ps.setLong(8, bounty.getExpiresAt());
            ps.setString(9, "ACTIVE");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to insert bounty", e);
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /** Returns all ACTIVE (non-expired) bounties on the given target. */
    public List<Bounty> getActiveBountiesOnTarget(UUID targetUuid) {
        List<Bounty> list = new ArrayList<>();
        String sql = "SELECT * FROM bounties WHERE target_uuid = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromRow(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch active bounties", e);
        }
        return list;
    }

    /** Returns the sum of all ACTIVE bounty amounts on the given target. */
    public double getTotalBountyValue(UUID targetUuid) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM bounties WHERE target_uuid = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get total bounty value", e);
        }
        return 0.0;
    }

    /** Returns all ACTIVE bounties across all targets. */
    public List<Bounty> getAllActiveBounties() {
        List<Bounty> list = new ArrayList<>();
        String sql = "SELECT * FROM bounties WHERE status = 'ACTIVE' ORDER BY amount DESC";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(fromRow(rs));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch all active bounties", e);
        }
        return list;
    }

    /**
     * Returns a list of (targetName, totalAmount) records for the top N bounty targets.
     * Uses a simple wrapper record for the result.
     */
    public List<BountyTarget> getTopBountyTargets(int limit) {
        List<BountyTarget> list = new ArrayList<>();
        String sql = "SELECT target_uuid, target_name, SUM(amount) AS total " +
                     "FROM bounties WHERE status = 'ACTIVE' " +
                     "GROUP BY target_uuid ORDER BY total DESC LIMIT ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BountyTarget(
                            UUID.fromString(rs.getString("target_uuid")),
                            rs.getString("target_name"),
                            rs.getDouble("total")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch top bounty targets", e);
        }
        return list;
    }

    /** Returns all bounties set by the given player UUID. */
    public List<Bounty> getBountiesSetBy(UUID setterUuid) {
        List<Bounty> list = new ArrayList<>();
        String sql = "SELECT * FROM bounties WHERE setter_uuid = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, setterUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromRow(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch bounties set by player", e);
        }
        return list;
    }

    /** Returns the count of ACTIVE bounties on the given target. */
    public int getCountOnTarget(UUID targetUuid) {
        String sql = "SELECT COUNT(*) FROM bounties WHERE target_uuid = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to count bounties on target", e);
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // Updates
    // -------------------------------------------------------------------------

    /**
     * Marks all ACTIVE bounties on the victim as CLAIMED by the given claimer.
     */
    public void claimBounties(UUID targetUuid, UUID claimerUuid, String claimerName) {
        String sql = "UPDATE bounties SET status = 'CLAIMED', claimed_by_uuid = ?, " +
                     "claimed_by_name = ?, claimed_at = ? " +
                     "WHERE target_uuid = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, claimerUuid.toString());
            ps.setString(2, claimerName);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, targetUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to claim bounties", e);
        }
    }

    /**
     * Sets all ACTIVE bounties that have passed their expiry time to EXPIRED.
     */
    public void expireOldBounties() {
        String sql = "UPDATE bounties SET status = 'EXPIRED' " +
                     "WHERE status = 'ACTIVE' AND expires_at < ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to expire old bounties", e);
        }
    }

    /** Deletes a specific bounty by its database ID. */
    public void removeBounty(int id) {
        String sql = "DELETE FROM bounties WHERE id = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove bounty id=" + id, e);
        }
    }

    /** Deletes all bounties (any status) on the given target. */
    public void clearBountiesOnTarget(UUID targetUuid) {
        String sql = "DELETE FROM bounties WHERE target_uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to clear bounties on target", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helper: row -> Bounty
    // -------------------------------------------------------------------------

    private Bounty fromRow(ResultSet rs) throws SQLException {
        String claimedByUuidStr = rs.getString("claimed_by_uuid");
        UUID claimedByUuid = (claimedByUuidStr != null) ? UUID.fromString(claimedByUuidStr) : null;
        long claimedAtRaw  = rs.getLong("claimed_at");
        Long claimedAt     = rs.wasNull() ? null : claimedAtRaw;

        return new Bounty(
                rs.getInt("id"),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getString("target_name"),
                UUID.fromString(rs.getString("setter_uuid")),
                rs.getString("setter_name"),
                rs.getDouble("amount"),
                rs.getInt("anonymous") == 1,
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getString("status"),
                claimedByUuid,
                rs.getString("claimed_by_name"),
                claimedAt
        );
    }

    // -------------------------------------------------------------------------
    // Nested result type
    // -------------------------------------------------------------------------

    public record BountyTarget(UUID uuid, String name, double totalAmount) {}
}
