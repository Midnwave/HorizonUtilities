package com.blockforge.horizonutilities.games;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LeaderboardManager {

    private final HorizonUtilitiesPlugin plugin;

    public record LeaderboardEntry(String playerName, int wins, int currentStreak, int bestStreak, long fastestTimeMs) {}

    public LeaderboardManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void recordWin(UUID uuid, String name, long timeMs) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try {
            // try update
            PreparedStatement check = conn.prepareStatement(
                    "SELECT wins, current_streak, best_streak, fastest_time_ms FROM chatgames_stats WHERE player_uuid = ?");
            check.setString(1, uuid.toString());
            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                int wins = rs.getInt("wins") + 1;
                int streak = rs.getInt("current_streak") + 1;
                int bestStreak = Math.max(rs.getInt("best_streak"), streak);
                long fastest = rs.getLong("fastest_time_ms");
                if (fastest == 0 || timeMs < fastest) fastest = timeMs;

                PreparedStatement update = conn.prepareStatement(
                        "UPDATE chatgames_stats SET player_name = ?, wins = ?, current_streak = ?, best_streak = ?, fastest_time_ms = ?, last_win = ? WHERE player_uuid = ?");
                update.setString(1, name);
                update.setInt(2, wins);
                update.setInt(3, streak);
                update.setInt(4, bestStreak);
                update.setLong(5, fastest);
                update.setLong(6, System.currentTimeMillis());
                update.setString(7, uuid.toString());
                update.executeUpdate();
            } else {
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO chatgames_stats (player_uuid, player_name, wins, current_streak, best_streak, fastest_time_ms, last_win) VALUES (?, ?, 1, 1, 1, ?, ?)");
                insert.setString(1, uuid.toString());
                insert.setString(2, name);
                insert.setLong(3, timeMs);
                insert.setLong(4, System.currentTimeMillis());
                insert.executeUpdate();
            }

            // reset everyone else's streak
            PreparedStatement resetStreaks = conn.prepareStatement(
                    "UPDATE chatgames_stats SET current_streak = 0 WHERE player_uuid != ?");
            resetStreaks.setString(1, uuid.toString());
            resetStreaks.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to record game win: " + e.getMessage());
        }
    }

    public List<LeaderboardEntry> getTop(int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        Connection conn = plugin.getDatabaseManager().getConnection();
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT player_name, wins, current_streak, best_streak, fastest_time_ms FROM chatgames_stats ORDER BY wins DESC LIMIT ?");
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                entries.add(new LeaderboardEntry(
                        rs.getString("player_name"),
                        rs.getInt("wins"),
                        rs.getInt("current_streak"),
                        rs.getInt("best_streak"),
                        rs.getLong("fastest_time_ms")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch leaderboard: " + e.getMessage());
        }
        return entries;
    }

    public LeaderboardEntry getStats(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT player_name, wins, current_streak, best_streak, fastest_time_ms FROM chatgames_stats WHERE player_uuid = ?");
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new LeaderboardEntry(
                        rs.getString("player_name"),
                        rs.getInt("wins"),
                        rs.getInt("current_streak"),
                        rs.getInt("best_streak"),
                        rs.getLong("fastest_time_ms")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch player stats: " + e.getMessage());
        }
        return null;
    }

    public void reset() {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try {
            conn.createStatement().executeUpdate("DELETE FROM chatgames_stats");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to reset leaderboard: " + e.getMessage());
        }
    }
}
