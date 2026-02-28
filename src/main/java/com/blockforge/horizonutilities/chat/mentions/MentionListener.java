package com.blockforge.horizonutilities.chat.mentions;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MentionListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final MentionManager mentionManager;

    public MentionListener(HorizonUtilitiesPlugin plugin, MentionManager mentionManager) {
        this.plugin = plugin;
        this.mentionManager = mentionManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getChatPlaceholdersConfig().isMentionsEnabled()) return;
        event.message(mentionManager.process(event.getPlayer(), event.message()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getChatPlaceholdersConfig().isMentionsEnabled()) return;
        var player = event.getPlayer();
        // Give the joining player @completions for all online players
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                mentionManager.addCompletions(player);
            }
        }, 5L);
        // Tell all online players about the new @completion
        mentionManager.broadcastAddCompletion(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getChatPlaceholdersConfig().isMentionsEnabled()) return;
        mentionManager.broadcastRemoveCompletion(event.getPlayer());
    }
}
