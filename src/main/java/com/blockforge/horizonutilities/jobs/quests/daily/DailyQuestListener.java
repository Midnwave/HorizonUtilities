package com.blockforge.horizonutilities.jobs.quests.daily;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join/quit for the daily quest system.
 */
public class DailyQuestListener implements Listener {

    private final DailyQuestManager manager;

    public DailyQuestListener(HorizonUtilitiesPlugin plugin, DailyQuestManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.onPlayerJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
