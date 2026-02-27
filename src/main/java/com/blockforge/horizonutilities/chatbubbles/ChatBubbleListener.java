package com.blockforge.horizonutilities.chatbubbles;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatBubbleListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final ChatBubbleManager manager;

    public ChatBubbleListener(HorizonUtilitiesPlugin plugin, ChatBubbleManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        // Capture the rendered message and dispatch to the main thread for entity spawning
        net.kyori.adventure.text.Component message = event.message();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> manager.spawnBubble(player, message));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.loadPreference(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        manager.removeBubble(player.getUniqueId());
        manager.unloadPreference(player.getUniqueId());
    }
}
