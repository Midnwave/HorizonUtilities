package com.blockforge.horizonutilities.blackmarket.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class BlackMarketGUIListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public BlackMarketGUIListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackMarketGUI gui)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player)) return;

        // Only handle clicks in the top (GUI) inventory
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        gui.handleClick(slot, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackMarketGUI)) return;
        event.setCancelled(true);
    }
}
