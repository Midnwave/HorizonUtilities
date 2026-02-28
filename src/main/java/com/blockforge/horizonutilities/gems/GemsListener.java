package com.blockforge.horizonutilities.gems;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class GemsListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final GemsManager gemsManager;

    public GemsListener(HorizonUtilitiesPlugin plugin, GemsManager gemsManager) {
        this.plugin = plugin;
        this.gemsManager = gemsManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        // Create gems account async if it doesn't exist
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                gemsManager.ensureAccount(player.getUniqueId(), player.getName()));
    }
}
