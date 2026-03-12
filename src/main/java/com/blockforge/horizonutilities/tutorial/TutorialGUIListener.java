package com.blockforge.horizonutilities.tutorial;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class TutorialGUIListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        var holder = event.getInventory().getHolder();

        if (holder instanceof TutorialGUI gui) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null
                    || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= TutorialGUI.SIZE) return;
            gui.handleClick(slot, player);
            return;
        }

        if (holder instanceof TutorialPageGUI pageGui) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null
                    || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= TutorialPageGUI.SIZE) return;
            pageGui.handleClick(slot, player);
            return;
        }

        if (holder instanceof TutorialRecipeGUI recipeGui) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null
                    || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= TutorialRecipeGUI.SIZE) return;
            recipeGui.handleClick(slot, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        var holder = event.getInventory().getHolder();
        if (holder instanceof TutorialGUI
                || holder instanceof TutorialPageGUI
                || holder instanceof TutorialRecipeGUI) {
            event.setCancelled(true);
        }
    }
}
