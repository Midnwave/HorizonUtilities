package com.blockforge.horizonutilities.story.effects;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.story.StoryManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * Passive effect for the Veilborn Compass story item.
 * Pulses an action bar hint and subtle sound when the player has undiscovered books.
 * Runs every 40 ticks (2 seconds).
 */
public class VeilbornCompassEffect implements Runnable {

    private static final Set<String> ALL_BOOKS = Set.of(
            "book1", "book2", "book3", "book4", "book5", "book6"
    );

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager storyManager;
    private final NamespacedKey storyItemKey;

    public VeilbornCompassEffect(HorizonUtilitiesPlugin plugin, StoryManager storyManager) {
        this.plugin = plugin;
        this.storyManager = storyManager;
        this.storyItemKey = new NamespacedKey(plugin, "story_item");
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 40L, 40L);
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.isEmpty() || !offhand.hasItemMeta()) continue;

            String itemId = offhand.getItemMeta().getPersistentDataContainer()
                    .get(storyItemKey, PersistentDataType.STRING);
            if (!"veilborn_compass".equals(itemId)) continue;

            StoryManager.StoryPlayerData data = storyManager.getPlayerData(player.getUniqueId());
            if (data == null) continue;

            Set<String> found = data.getBooksFound();
            long undiscovered = ALL_BOOKS.stream().filter(b -> !found.contains(b)).count();

            if (undiscovered > 0) {
                player.sendActionBar(Component.text()
                        .append(Component.text("\u2726 ", NamedTextColor.AQUA))
                        .append(Component.text("The compass trembles... ", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Component.text(undiscovered + " chronicle" + (undiscovered == 1 ? "" : "s")
                                + " remain hidden", NamedTextColor.DARK_AQUA))
                        .append(Component.text(" \u2726", NamedTextColor.AQUA))
                        .build());

                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                        0.3f, 1.8f);
            }
        }
    }
}
