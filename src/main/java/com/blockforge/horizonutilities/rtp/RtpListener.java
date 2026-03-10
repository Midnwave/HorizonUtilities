package com.blockforge.horizonutilities.rtp;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.config.MessagesManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Handles RTP warmup cancellation and first-join auto-RTP.
 */
public class RtpListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final RtpManager rtpManager;
    private final MessagesManager msg;

    public RtpListener(HorizonUtilitiesPlugin plugin, RtpManager rtpManager) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
        this.msg = plugin.getMessagesManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (rtpManager.isInWarmup(player.getUniqueId())) {
            rtpManager.cancelWarmup(player.getUniqueId());
            msg.send(player, "rtp-cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (rtpManager.isInWarmup(player.getUniqueId())) {
            rtpManager.cancelWarmup(player.getUniqueId());
            msg.send(player, "rtp-cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!rtpManager.getConfig().isFirstJoinEnabled()) return;

        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;

        int delay = rtpManager.getConfig().getFirstJoinDelayTicks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                rtpManager.firstJoinRtp(player);
            }
        }, delay);
    }
}
