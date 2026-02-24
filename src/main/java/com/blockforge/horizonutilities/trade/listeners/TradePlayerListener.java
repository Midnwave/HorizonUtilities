package com.blockforge.horizonutilities.trade.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.trade.TradeManager;
import com.blockforge.horizonutilities.trade.TradeRequest;
import com.blockforge.horizonutilities.trade.TradeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles player lifecycle events that affect active trade sessions or pending requests.
 */
public class TradePlayerListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final TradeManager tradeManager;

    public TradePlayerListener(HorizonUtilitiesPlugin plugin, TradeManager tradeManager) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cancel any active trade session
        if (tradeManager.hasActiveSession(uuid)) {
            tradeManager.cancelSession(uuid);
        }

        // Remove any pending request this player had as a target
        if (tradeManager.hasPendingRequest(uuid)) {
            TradeRequest request = tradeManager.getPendingRequest(uuid);
            if (request != null) {
                Player sender = plugin.getServer().getPlayer(request.getSenderUuid());
                if (sender != null && sender.isOnline()) {
                    plugin.getMessagesManager().send(sender, "trade-sender-offline");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!tradeManager.getTradeConfig().isSameWorldOnly()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        TradeSession session = tradeManager.getSession(uuid);
        if (session == null) return;

        UUID otherUuid = session.getOtherPlayerUuid(uuid);
        if (otherUuid == null) return;

        Player other = plugin.getServer().getPlayer(otherUuid);
        if (other == null || !other.isOnline()) {
            tradeManager.cancelSession(uuid);
            return;
        }

        // If the two players are now in different worlds, cancel
        if (!player.getWorld().equals(other.getWorld())) {
            plugin.getMessagesManager().send(player, "trade-different-world");
            plugin.getMessagesManager().send(other, "trade-different-world");
            tradeManager.cancelSession(uuid);
        }
    }
}
