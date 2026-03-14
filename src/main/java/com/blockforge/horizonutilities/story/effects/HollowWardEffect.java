package com.blockforge.horizonutilities.story.effects;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Passive effect for the Hollow's Ward story item.
 * When held in the offhand, reduces WITHER potion effect duration by 50%.
 */
public class HollowWardEffect implements Listener {

    private final NamespacedKey storyItemKey;

    public HollowWardEffect(HorizonUtilitiesPlugin plugin) {
        this.storyItemKey = new NamespacedKey(plugin, "story_item");
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getNewEffect() == null) return;
        if (event.getNewEffect().getType() != PotionEffectType.WITHER) return;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.isEmpty() || !offhand.hasItemMeta()) return;

        String itemId = offhand.getItemMeta().getPersistentDataContainer()
                .get(storyItemKey, PersistentDataType.STRING);
        if (!"hollows_ward".equals(itemId)) return;

        // Cancel the original effect and apply one with halved duration
        event.setCancelled(true);

        PotionEffect original = event.getNewEffect();
        int reducedDuration = original.getDuration() / 2;

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WITHER,
                reducedDuration,
                original.getAmplifier(),
                original.isAmbient(),
                original.hasParticles(),
                original.hasIcon()
        ));
    }
}
