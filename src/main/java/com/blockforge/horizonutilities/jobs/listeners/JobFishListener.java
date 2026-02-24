package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Awards job income when a player successfully catches a fish.
 */
public class JobFishListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public JobFishListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        plugin.getJobManager().processAction(event.getPlayer(), JobAction.FISH, "FISH");
    }
}
