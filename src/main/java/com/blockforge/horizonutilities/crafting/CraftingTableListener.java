package com.blockforge.horizonutilities.crafting;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Saves and restores crafting table contents per block location.
 */
public class CraftingTableListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final CraftingTableManager manager;

    public CraftingTableListener(HorizonUtilitiesPlugin plugin, CraftingTableManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    /** When a player right-clicks a crafting table, restore the saved grid 1 tick later. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!manager.getConfig().isEnabled()) return;
        if (!event.getAction().isRightClick()) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CRAFTING_TABLE) return;
        Player player = event.getPlayer();
        Location loc = block.getLocation();

        // Restore items 1 tick after the inventory opens
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            var top = player.getOpenInventory().getTopInventory();
            if (top == null || top.getType() != InventoryType.WORKBENCH) return;
            if (top.getLocation() == null) return;
            // Only restore if at the same table location
            Location invLoc = top.getLocation();
            if (!isSameBlock(loc, invLoc)) return;

            ItemStack[] saved = manager.loadGrid(loc);
            if (saved == null) return;
            // Workbench slots 1-9 are the crafting grid
            for (int i = 0; i < 9; i++) {
                top.setItem(i + 1, saved[i]);
            }
        }, 1L);
    }

    /** When a player closes a workbench, save the crafting grid. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!manager.getConfig().isEnabled()) return;
        if (event.getInventory().getType() != InventoryType.WORKBENCH) return;
        Location loc = event.getInventory().getLocation();
        if (loc == null) return;

        // Slots 1-9 are the crafting grid; slot 0 is the result
        ItemStack[] grid = new ItemStack[9];
        boolean anyItem = false;
        for (int i = 0; i < 9; i++) {
            ItemStack item = event.getInventory().getItem(i + 1);
            grid[i] = (item == null || item.getType() == Material.AIR) ? null : item.clone();
            if (grid[i] != null) anyItem = true;
        }

        if (anyItem) {
            manager.saveGrid(loc, grid);
        } else {
            // Grid is empty - delete any saved data
            manager.deleteGrid(loc);
        }
    }

    /** When a crafting table is broken, drop its saved items and clear DB entry. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!manager.getConfig().isEnabled()) return;
        if (event.getBlock().getType() != Material.CRAFTING_TABLE) return;
        manager.dropAndDelete(event.getBlock());
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
