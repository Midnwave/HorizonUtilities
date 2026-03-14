package com.blockforge.horizonutilities.story.effects;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Passive effect for the Vigil Crystal story item.
 * Grants Night Vision to players holding it in their offhand.
 * Runs every 200 ticks (10 seconds) and applies 300-tick (15s) Night Vision.
 */
public class VigilCrystalEffect implements Runnable {

    private final HorizonUtilitiesPlugin plugin;
    private final NamespacedKey storyItemKey;

    public VigilCrystalEffect(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.storyItemKey = new NamespacedKey(plugin, "story_item");
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 200L, 200L);
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.isEmpty() || !offhand.hasItemMeta()) continue;

            String itemId = offhand.getItemMeta().getPersistentDataContainer()
                    .get(storyItemKey, PersistentDataType.STRING);
            if (!"vigil_crystal".equals(itemId)) continue;

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION,
                    300, // 15 seconds
                    0,
                    true,  // ambient
                    false, // no particles
                    true   // show icon
            ));
        }
    }
}
