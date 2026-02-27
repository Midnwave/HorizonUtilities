package com.blockforge.horizonutilities.customitems.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.customitems.items.WolfWhistleItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WolfWhistleListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private final double radius;
    private final long cooldownMs;

    public WolfWhistleListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        var f   = new java.io.File(plugin.getDataFolder(), "custom-items.yml");
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        radius     = cfg.getDouble("items.wolf_whistle.summon-radius", 200);
        cooldownMs = cfg.getLong("items.wolf_whistle.cooldown-seconds", 30) * 1000L;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        var item = event.getItem();
        if (item == null) return;
        var registry = plugin.getCustomItemRegistry();
        if (!WolfWhistleItem.ID.equals(registry.getItemId(item))) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        long now = System.currentTimeMillis();
        if (now - cooldowns.getOrDefault(player.getUniqueId(), 0L) < cooldownMs) {
            long rem = (cooldownMs - (now - cooldowns.get(player.getUniqueId()))) / 1000 + 1;
            player.sendMessage(Component.text("Wolf Whistle cooldown: " + rem + "s", NamedTextColor.RED));
            return;
        }

        // Find and teleport all tamed wolves owned by this player within radius
        var nearby = player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius);
        int count = 0;
        for (var entity : nearby) {
            if (entity.getType() != EntityType.WOLF) continue;
            Wolf wolf = (Wolf) entity;
            if (!wolf.isTamed()) continue;
            if (!(wolf.getOwner() instanceof Player owner)) continue;
            if (!owner.getUniqueId().equals(player.getUniqueId())) continue;
            // Teleport wolf to a spot near the player
            wolf.teleport(player.getLocation().add(
                    (Math.random() - 0.5) * 2,
                    0,
                    (Math.random() - 0.5) * 2));
            count++;
        }

        cooldowns.put(player.getUniqueId(), now);

        if (count > 0) {
            player.sendMessage(Component.text("Summoned ", NamedTextColor.GOLD)
                    .append(Component.text(count + " wolf" + (count != 1 ? "s" : ""), NamedTextColor.YELLOW))
                    .append(Component.text("!", NamedTextColor.GOLD)));
        } else {
            player.sendMessage(Component.text("No tamed wolves found nearby.", NamedTextColor.GRAY));
        }
    }
}
