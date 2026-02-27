package com.blockforge.horizonutilities.warps.player;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerWarpStorageManager {

    private final HorizonUtilitiesPlugin plugin;

    public PlayerWarpStorageManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        initTables();
    }

    private void initTables() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_warps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    name TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL DEFAULT 0,
                    pitch REAL NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    visits INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(owner_uuid, name)
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_warp_ratings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    warp_id INTEGER NOT NULL,
                    rater_uuid TEXT NOT NULL,
                    rating INTEGER NOT NULL,
                    rated_at INTEGER NOT NULL,
                    UNIQUE(warp_id, rater_uuid),
                    FOREIGN KEY(warp_id) REFERENCES player_warps(id) ON DELETE CASCADE
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pwarps_owner ON player_warps(owner_uuid)");
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] Failed to create tables: " + e.getMessage());
        }
    }

    public List<PlayerWarp> getAll() {
        List<PlayerWarp> list = new ArrayList<>();
        String sql = """
            SELECT w.*, COALESCE(AVG(r.rating),0) AS avg_rating, COUNT(r.id) AS rating_count
            FROM player_warps w
            LEFT JOIN player_warp_ratings r ON r.warp_id = w.id
            GROUP BY w.id
            ORDER BY w.name ASC""";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(fromRow(rs));
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] getAll failed: " + e.getMessage());
        }
        return list;
    }

    public List<PlayerWarp> getByOwner(UUID ownerUuid) {
        List<PlayerWarp> list = new ArrayList<>();
        String sql = """
            SELECT w.*, COALESCE(AVG(r.rating),0) AS avg_rating, COUNT(r.id) AS rating_count
            FROM player_warps w
            LEFT JOIN player_warp_ratings r ON r.warp_id = w.id
            WHERE w.owner_uuid = ?
            GROUP BY w.id""";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromRow(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] getByOwner failed: " + e.getMessage());
        }
        return list;
    }

    public int countByOwner(UUID ownerUuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM player_warps WHERE owner_uuid = ?")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] countByOwner failed: " + e.getMessage());
        }
        return 0;
    }

    public PlayerWarp getByOwnerAndName(UUID ownerUuid, String name) {
        String sql = """
            SELECT w.*, COALESCE(AVG(r.rating),0) AS avg_rating, COUNT(r.id) AS rating_count
            FROM player_warps w
            LEFT JOIN player_warp_ratings r ON r.warp_id = w.id
            WHERE w.owner_uuid = ? AND LOWER(w.name) = LOWER(?)
            GROUP BY w.id""";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return fromRow(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] getByOwnerAndName failed: " + e.getMessage());
        }
        return null;
    }

    public PlayerWarp getByName(String ownerName, String warpName) {
        String sql = """
            SELECT w.*, COALESCE(AVG(r.rating),0) AS avg_rating, COUNT(r.id) AS rating_count
            FROM player_warps w
            LEFT JOIN player_warp_ratings r ON r.warp_id = w.id
            WHERE LOWER(w.owner_name) = LOWER(?) AND LOWER(w.name) = LOWER(?)
            GROUP BY w.id""";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerName);
            ps.setString(2, warpName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return fromRow(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] getByName failed: " + e.getMessage());
        }
        return null;
    }

    public boolean create(UUID ownerUuid, String ownerName, String name,
                          String world, double x, double y, double z, float yaw, float pitch) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO player_warps(owner_uuid,owner_name,name,world,x,y,z,yaw,pitch,created_at,visits) " +
                     "VALUES(?,?,?,?,?,?,?,?,?,?,0)")) {
            ps.setString(1, ownerUuid.toString());
            ps.setString(2, ownerName);
            ps.setString(3, name);
            ps.setString(4, world);
            ps.setDouble(5, x);
            ps.setDouble(6, y);
            ps.setDouble(7, z);
            ps.setFloat(8, yaw);
            ps.setFloat(9, pitch);
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] create failed: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(int warpId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM player_warps WHERE id = ?")) {
            ps.setInt(1, warpId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] delete failed: " + e.getMessage());
            return false;
        }
    }

    public void incrementVisits(int warpId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE player_warps SET visits = visits + 1 WHERE id = ?")) {
            ps.setInt(1, warpId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] incrementVisits failed: " + e.getMessage());
        }
    }

    public boolean setRating(int warpId, UUID raterUuid, int rating) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO player_warp_ratings(warp_id,rater_uuid,rating,rated_at) VALUES(?,?,?,?) " +
                     "ON CONFLICT(warp_id,rater_uuid) DO UPDATE SET rating=excluded.rating, rated_at=excluded.rated_at")) {
            ps.setInt(1, warpId);
            ps.setString(2, raterUuid.toString());
            ps.setInt(3, rating);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("[PlayerWarps] setRating failed: " + e.getMessage());
            return false;
        }
    }

    private PlayerWarp fromRow(ResultSet rs) throws SQLException {
        return new PlayerWarp(
                rs.getInt("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getLong("created_at"),
                rs.getInt("visits"),
                rs.getDouble("avg_rating"),
                rs.getInt("rating_count"));
    }
}
