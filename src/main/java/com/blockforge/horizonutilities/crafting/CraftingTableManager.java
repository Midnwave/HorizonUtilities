package com.blockforge.horizonutilities.crafting;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.sql.*;

/**
 * Persists crafting table contents per block location using SQLite.
 * Tables act as containers — items stay in the grid even when closed.
 */
public class CraftingTableManager {

    private final HorizonUtilitiesPlugin plugin;
    private final CraftingTableConfig config;

    public CraftingTableManager(HorizonUtilitiesPlugin plugin, CraftingTableConfig config) {
        this.plugin  = plugin;
        this.config  = config;
        initTable();
        scheduleCleanup();
    }

    private void initTable() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS crafting_tables (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "world TEXT NOT NULL," +
                    "x INTEGER NOT NULL," +
                    "y INTEGER NOT NULL," +
                    "z INTEGER NOT NULL," +
                    "slot0 BLOB, slot1 BLOB, slot2 BLOB," +
                    "slot3 BLOB, slot4 BLOB, slot5 BLOB," +
                    "slot6 BLOB, slot7 BLOB, slot8 BLOB," +
                    "last_used INTEGER NOT NULL," +
                    "UNIQUE(world, x, y, z))");
        } catch (SQLException e) {
            plugin.getLogger().warning("[CraftingTables] Failed to create table: " + e.getMessage());
        }
    }

    private void scheduleCleanup() {
        if (config.getCleanupAfterDays() <= 0) return;
        // Run cleanup once on startup (async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long cutoff = System.currentTimeMillis() - (long) config.getCleanupAfterDays() * 86_400_000L;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM crafting_tables WHERE last_used < ?")) {
                ps.setLong(1, cutoff);
                int deleted = ps.executeUpdate();
                if (deleted > 0)
                    plugin.getLogger().info("[CraftingTables] Cleaned up " + deleted + " stale entries.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[CraftingTables] Cleanup failed: " + e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Grid persistence
    // -------------------------------------------------------------------------

    /** Saves slots 1-9 of a workbench inventory (the crafting grid) to the DB. */
    public void saveGrid(Location loc, ItemStack[] grid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO crafting_tables(world,x,y,z," +
                    "slot0,slot1,slot2,slot3,slot4,slot5,slot6,slot7,slot8,last_used) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT(world,x,y,z) DO UPDATE SET " +
                    "slot0=excluded.slot0, slot1=excluded.slot1, slot2=excluded.slot2," +
                    "slot3=excluded.slot3, slot4=excluded.slot4, slot5=excluded.slot5," +
                    "slot6=excluded.slot6, slot7=excluded.slot7, slot8=excluded.slot8," +
                    "last_used=excluded.last_used";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                for (int i = 0; i < 9; i++) {
                    ItemStack item = (grid != null && i < grid.length) ? grid[i] : null;
                    ps.setBytes(5 + i, item == null ? null : item.serializeAsBytes());
                }
                ps.setLong(14, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[CraftingTables] saveGrid failed: " + e.getMessage());
            }
        });
    }

    /** Loads the saved crafting grid for a location. Returns null if none saved. */
    public ItemStack[] loadGrid(Location loc) {
        String sql = "SELECT slot0,slot1,slot2,slot3,slot4,slot5,slot6,slot7,slot8 " +
                "FROM crafting_tables WHERE world=? AND x=? AND y=? AND z=?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ItemStack[] grid = new ItemStack[9];
                for (int i = 0; i < 9; i++) {
                    byte[] data = rs.getBytes(i + 1);
                    grid[i] = data == null ? null : ItemStack.deserializeBytes(data);
                }
                return grid;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[CraftingTables] loadGrid failed: " + e.getMessage());
        }
        return null;
    }

    /** Deletes the saved grid for a location. */
    public void deleteGrid(Location loc) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM crafting_tables WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[CraftingTables] deleteGrid failed: " + e.getMessage());
            }
        });
    }

    /** Drops all items from a saved grid at the block location and deletes the row. */
    public void dropAndDelete(Block block) {
        Location loc = block.getLocation();
        ItemStack[] grid = loadGrid(loc);
        if (grid != null) {
            for (ItemStack item : grid) {
                if (item != null && item.getType() != Material.AIR) {
                    block.getWorld().dropItemNaturally(loc, item);
                }
            }
        }
        deleteGrid(loc);
    }

    public CraftingTableConfig getConfig() { return config; }
}
