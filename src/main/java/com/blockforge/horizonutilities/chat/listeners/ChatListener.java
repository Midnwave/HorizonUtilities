package com.blockforge.horizonutilities.chat.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public ChatListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        event.message(plugin.getPlaceholderManager().process(player, event.message()));
    }
}
