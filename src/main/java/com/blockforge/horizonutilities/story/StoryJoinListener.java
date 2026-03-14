package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class StoryJoinListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager manager;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public StoryJoinListener(HorizonUtilitiesPlugin plugin, StoryManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load story data from DB
        manager.loadPlayer(player.getUniqueId());

        // First join: send intro message + lore book
        if (!player.hasPlayedBefore()) {
            StoryConfig config = manager.getStoryConfig();
            if (!config.isEnabled()) return;

            int delay = config.getFirstJoinDelayTicks();

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                // Chat message
                if (config.isFirstJoinMessageEnabled()) {
                    List<String> lines = config.getFirstJoinMessage();
                    for (String line : lines) {
                        player.sendMessage(MINI.deserialize(line));
                    }
                }

                // Lore book
                if (config.isFirstJoinBookEnabled()) {
                    ItemStack book = manager.getBookManager().createFirstJoinBook();
                    if (book != null) {
                        player.getInventory().addItem(book);
                    }
                }
            }, delay);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.unloadPlayer(event.getPlayer().getUniqueId());
    }
}
