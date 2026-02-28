package com.blockforge.horizonutilities.gems;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class GemsStorageManager {

    private final HorizonUtilitiesPlugin plugin;

    public GemsStorageManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public double getBalance(UUID playerUuid) {
        String sql = "SELECT balance FROM gems WHERE player_uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get gems balance", e);
        }
        return -1; // not found
    }

    public boolean createAccount(UUID playerUuid, String playerName, double startingBalance) {
        String sql = "INSERT OR IGNORE INTO gems (player_uuid, player_name, balance, total_earned, total_spent, last_modified) VALUES (?, ?, ?, 0, 0, ?)";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setDouble(3, startingBalance);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create gems account", e);
        }
        return false;
    }

    public boolean deposit(UUID playerUuid, double amount) {
        String sql = "UPDATE gems SET balance = balance + ?, total_earned = total_earned + ?, last_modified = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setDouble(2, amount);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, playerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deposit gems", e);
        }
        return false;
    }

    public boolean withdraw(UUID playerUuid, double amount) {
        String sql = "UPDATE gems SET balance = balance - ?, total_spent = total_spent + ?, last_modified = ? WHERE player_uuid = ? AND balance >= ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setDouble(2, amount);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, playerUuid.toString());
            ps.setDouble(5, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to withdraw gems", e);
        }
        return false;
    }

    public boolean setBalance(UUID playerUuid, double balance) {
        String sql = "UPDATE gems SET balance = ?, last_modified = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setDouble(1, balance);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, playerUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to set gems balance", e);
        }
        return false;
    }

    public void updateName(UUID playerUuid, String playerName) {
        String sql = "UPDATE gems SET player_name = ? WHERE player_uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, playerName);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update gems player name", e);
        }
    }

    /**
     * Returns top players by gem balance. Each entry: [name, balance].
     */
    public List<Map.Entry<String, Double>> getTopBalances(int limit) {
        List<Map.Entry<String, Double>> top = new ArrayList<>();
        String sql = "SELECT player_name, balance FROM gems ORDER BY balance DESC LIMIT ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    top.add(Map.entry(rs.getString("player_name"), rs.getDouble("balance")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get top gems", e);
        }
        return top;
    }

    public void logTransaction(UUID playerUuid, String type, double amount, double balanceAfter, String reason) {
        String sql = "INSERT INTO gems_transactions (player_uuid, transaction_type, amount, balance_after, reason, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, type);
            ps.setDouble(3, amount);
            ps.setDouble(4, balanceAfter);
            ps.setString(5, reason);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to log gems transaction", e);
        }
    }
}
