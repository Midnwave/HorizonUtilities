package com.blockforge.horizonutilities.anvil;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;

/**
 * Overrides the vanilla "Too Expensive!" anvil cap with a configurable value.
 * Setting max-repair-cost to -1 in config removes the limit entirely.
 */
public class AnvilListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public AnvilListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        int maxCost = plugin.getConfigManager().getAnvilMaxRepairCost();
        if (maxCost == 0) return; // 0 = use vanilla default, don't touch

        AnvilView view = (AnvilView) event.getView();
        if (maxCost < 0) {
            // Remove the limit entirely — set to Integer.MAX_VALUE
            view.setMaximumRepairCost(Integer.MAX_VALUE);
        } else {
            view.setMaximumRepairCost(maxCost);
        }
    }
}
