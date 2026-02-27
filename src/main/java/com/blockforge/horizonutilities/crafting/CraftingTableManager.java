package com.blockforge.horizonutilities.crafting;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists crafting table contents per block location using SQLite.
 * Also manages floating ItemDisplay entities above tables that have saved grids.
 */
public class CraftingTableManager {

    private final HorizonUtilitiesPlugin plugin;
    private final CraftingTableConfig config;

    /** Location key -> active ItemDisplay entity above that table */
    private final Map<String, ItemDisplay> floatingDisplays = new ConcurrentHashMap<>();

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
        // Update floating display on main thread
        updateFloatingDisplay(loc, grid);
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

    /** Deletes the saved grid for a location and removes floating display. */
    public void deleteGrid(Location loc) {
        removeFloatingDisplay(loc);
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

    // -------------------------------------------------------------------------
    // Floating item display
    // -------------------------------------------------------------------------

    private void updateFloatingDisplay(Location tableLoc, ItemStack[] grid) {
        if (!config.isFloatingItemDisplay()) return;
        // Find first non-null item in grid, or check recipe result
        ItemStack displayItem = getDisplayItem(grid);
        removeFloatingDisplay(tableLoc);
        if (displayItem == null) return;

        Location displayLoc = tableLoc.clone().add(0.5, 1.2, 0.5);
        final ItemStack finalItem = displayItem;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemDisplay display = tableLoc.getWorld().spawn(displayLoc, ItemDisplay.class, d -> {
                d.setItemStack(finalItem);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setViewRange(0.3f);
            });
            floatingDisplays.put(locKey(tableLoc), display);
        });
    }

    private ItemStack getDisplayItem(ItemStack[] grid) {
        if (grid == null) return null;
        // Try to find recipe result
        ItemStack[] matrix = Arrays.copyOf(grid, 9);
        Iterator<Recipe> iter = plugin.getServer().recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
                // Just return first non-null item for simplicity
            }
        }
        // Return first non-null item from grid
        for (ItemStack item : grid) {
            if (item != null && item.getType() != Material.AIR) return item;
        }
        return null;
    }

    private void removeFloatingDisplay(Location loc) {
        ItemDisplay display = floatingDisplays.remove(locKey(loc));
        if (display != null && display.isValid()) display.remove();
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public CraftingTableConfig getConfig() { return config; }
}
