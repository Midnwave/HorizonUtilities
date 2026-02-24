package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

/**
 * Awards job income when a player enchants an item at an enchanting table.
 */
public class JobEnchantListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public JobEnchantListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        plugin.getJobManager().processAction(
                event.getEnchanter(),
                JobAction.ENCHANT,
                event.getItem().getType().name());
    }
}
