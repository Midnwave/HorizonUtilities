package com.blockforge.horizonutilities.lottery;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite CRUD for lottery_instances and lottery_tickets tables.
 */
public class LotteryStorageManager {

    /** Lightweight record used during the weighted-random draw. */
    public record TicketEntry(UUID uuid, String playerName, int ticketCount) {}

    /** Record returned from getRecentWinners. */
    public record WinnerRecord(String tierId, String tierDisplayName, String winnerName,
                                UUID winnerUuid, double pot, long drawnAt) {}

    private final HorizonUtilitiesPlugin plugin;

    public LotteryStorageManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Instances
    // -------------------------------------------------------------------------

    /**
     * Creates a new lottery instance row and returns its generated id, or -1 on failure.
     */
    public int createInstance(String tierId, double startingPot, long startedAt, long drawAt) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO lottery_instances (tier_id, pot, started_at, draw_at, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, tierId);
            ps.setDouble(2, startingPot);
            ps.setLong(3, startedAt);
            ps.setLong(4, drawAt);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to create instance: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Returns the single ACTIVE instance for the given tier, or null if none exists.
     */
    public LotteryInstance getActiveInstance(String tierId, LotteryTierConfig config) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM lottery_instances WHERE tier_id = ? AND status = 'ACTIVE' LIMIT 1");
            ps.setString(1, tierId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return fromResultSet(rs, config);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to get active instance: " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns all ACTIVE instances across all tiers.
     * Caller is responsible for resolving configs by tierId.
     */
    public List<LotteryInstance> getAllActiveInstances(java.util.Map<String, LotteryTierConfig> configs) {
        List<LotteryInstance> list = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM lottery_instances WHERE status = 'ACTIVE'");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String tierId = rs.getString("tier_id");
                LotteryTierConfig cfg = configs.get(tierId);
                if (cfg != null) list.add(fromResultSet(rs, cfg));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to get all active instances: " + e.getMessage());
        }
        return list;
    }

    /** Updates the status, winner_uuid, and winner_name of an instance. */
    public void updateInstanceStatus(int instanceId, String status, UUID winner, String winnerName) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE lottery_instances SET status = ?, winner_uuid = ?, winner_name = ? WHERE id = ?");
            ps.setString(1, status);
            ps.setString(2, winner != null ? winner.toString() : null);
            ps.setString(3, winnerName);
            ps.setInt(4, instanceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to update instance status: " + e.getMessage());
        }
    }

    /** Updates the pot amount for an instance. */
    public void updateInstancePot(int instanceId, double pot) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE lottery_instances SET pot = ? WHERE id = ?");
            ps.setDouble(1, pot);
            ps.setInt(2, instanceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to update instance pot: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Tickets
    // -------------------------------------------------------------------------

    /** Inserts a ticket purchase row. */
    public void addTicket(int instanceId, UUID playerUuid, String playerName,
                          int count, double totalPaid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO lottery_tickets (instance_id, player_uuid, player_name, ticket_count, total_paid, purchased_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)");
            ps.setInt(1, instanceId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, playerName);
            ps.setInt(4, count);
            ps.setDouble(5, totalPaid);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to add ticket: " + e.getMessage());
        }
    }

    /**
     * Returns the total number of tickets this player has bought across all their
     * ticket rows for the given instance.
     */
    public int getTicketCount(int instanceId, UUID playerUuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(ticket_count), 0) FROM lottery_tickets " +
                    "WHERE instance_id = ? AND player_uuid = ? AND refunded = 0");
            ps.setInt(1, instanceId);
            ps.setString(2, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to get ticket count: " + e.getMessage());
        }
        return 0;
    }

    /** Returns the total number of non-refunded tickets in an instance. */
    public int getTotalTickets(int instanceId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(ticket_count), 0) FROM lottery_tickets " +
                    "WHERE instance_id = ? AND refunded = 0");
            ps.setInt(1, instanceId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to get total tickets: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns one TicketEntry per distinct player who holds non-refunded tickets
     * in this instance, suitable for a weighted-random draw.
     */
    public List<TicketEntry> getAllTickets(int instanceId) {
        List<TicketEntry> entries = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT player_uuid, player_name, SUM(ticket_count) as total " +
                    "FROM lottery_tickets WHERE instance_id = ? AND refunded = 0 " +
                    "GROUP BY player_uuid, player_name");
            ps.setInt(1, instanceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                String name = rs.getString("player_name");
                int count = rs.getInt("total");
                entries.add(new TicketEntry(uuid, name, count));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to get all tickets: " + e.getMessage());
        }
        return entries;
    }

    /** Returns the number of distinct players who hold tickets in this instance. */
    public int getDistinctPlayerCount(int instanceId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT player_uuid) FROM lottery_tickets " +
                    "WHERE instance_id = ? AND refunded = 0");
            ps.setInt(1, instanceId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to count distinct players: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Refunds all non-refunded tickets in an instance. Deposits money back via VaultHook
     * and marks rows as refunded = 1. Runs synchronously — callers should run async.
     */
    public void refundTickets(int instanceId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement select = conn.prepareStatement(
                    "SELECT player_uuid, SUM(total_paid) as refund_amount " +
                    "FROM lottery_tickets WHERE instance_id = ? AND refunded = 0 " +
                    "GROUP BY player_uuid");
            select.setInt(1, instanceId);
            ResultSet rs = select.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                double amount = rs.getDouble("refund_amount");
                plugin.getVaultHook().deposit(Bukkit.getOfflinePlayer(uuid), amount);
            }
            // Mark all as refunded
            PreparedStatement update = conn.prepareStatement(
                    "UPDATE lottery_tickets SET refunded = 1 WHERE instance_id = ? AND refunded = 0");
            update.setInt(1, instanceId);
            update.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to refund tickets: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    /** Returns the most recent drawn instances, up to the given limit. */
    public List<WinnerRecord> getRecentWinners(int limit,
                                                java.util.Map<String, LotteryTierConfig> configs) {
        List<WinnerRecord> records = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT tier_id, winner_uuid, winner_name, pot, draw_at " +
                    "FROM lottery_instances WHERE status = 'DRAWN' AND winner_uuid IS NOT NULL " +
                    "ORDER BY draw_at DESC LIMIT ?");
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String tierId = rs.getString("tier_id");
                LotteryTierConfig cfg = configs.get(tierId);
                String displayName = cfg != null ? cfg.getDisplayName() : tierId;
                UUID winnerUuid = UUID.fromString(rs.getString("winner_uuid"));
                records.add(new WinnerRecord(
                        tierId, displayName,
                        rs.getString("winner_name"), winnerUuid,
                        rs.getDouble("pot"), rs.getLong("draw_at")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Lottery] Failed to get recent winners: " + e.getMessage());
        }
        return records;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LotteryInstance fromResultSet(ResultSet rs, LotteryTierConfig config) throws SQLException {
        int id = rs.getInt("id");
        String tierId = rs.getString("tier_id");
        double pot = rs.getDouble("pot");
        long startedAt = rs.getLong("started_at");
        long drawAt = rs.getLong("draw_at");
        String status = rs.getString("status");
        String winnerUuidStr = rs.getString("winner_uuid");
        String winnerName = rs.getString("winner_name");
        UUID winnerUuid = winnerUuidStr != null ? UUID.fromString(winnerUuidStr) : null;
        return new LotteryInstance(id, tierId, pot, startedAt, drawAt, status,
                winnerUuid, winnerName, config);
    }
}
