package com.blockforge.horizonutilities.auction.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class AuctionPlayerListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public AuctionPlayerListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // send queued auction notifications after a short delay
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getNotificationManager().sendQueuedNotifications(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        AuctionGUIListener.clearGUI(player.getUniqueId());
    }
}
