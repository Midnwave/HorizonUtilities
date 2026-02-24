package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Loads player job data from the DB on join and saves it on quit.
 */
public class JobPlayerListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public JobPlayerListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Load asynchronously to avoid blocking the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getJobManager().loadPlayerData(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Save async, then remove from cache on main thread after save
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getJobManager().savePlayerData(uuid);
            // Clean up anti-exploit caches
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getJobManager().getCooldownManager().cleanup(uuid);
                plugin.getJobManager().getAreaFarmingDetector().clearPlayer(uuid);
            });
        });
    }
}
