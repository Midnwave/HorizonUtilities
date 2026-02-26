package com.blockforge.horizonutilities.maintenance;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class MaintenanceListener implements Listener {

    private final MaintenanceManager manager;

    public MaintenanceListener(MaintenanceManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!manager.isEnabled()) return;
        if (manager.isAuthorized(event.getUniqueId())) return;

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                manager.getKickMessage());
    }

    @EventHandler
    public void onServerListPing(PaperServerListPingEvent event) {
        if (!manager.isEnabled()) return;
        event.motd(manager.getMotd());
    }
}
