package com.blockforge.horizonutilities.games.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class GameAnswerListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public GameAnswerListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getChatGameManager().isGameActive()) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("horizonutilities.chatgames.play")) return;

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // run answer check on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getChatGameManager().handleAnswer(player, message);
        });
    }
}
