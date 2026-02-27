package com.blockforge.horizonutilities.customitems.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.customitems.items.GrapplingHookItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GrapplingHookListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final Map<UUID, Long> cooldowns          = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fallDamageCancel   = new ConcurrentHashMap<>();

    private final int maxRange;
    private final long cooldownMs;
    private final long cancelFallMs;

    public GrapplingHookListener(HorizonUtilitiesPlugin plugin) {
        this.plugin       = plugin;
        var cfg           = plugin.getServer().getPluginManager();
        // Read from custom-items.yml section
        var config        = loadSection();
        this.maxRange     = config[0];
        this.cooldownMs   = config[1] * 1000L;
        this.cancelFallMs = config[2] * 1000L;
    }

    private int[] loadSection() {
        var f = new java.io.File(plugin.getDataFolder(), "custom-items.yml");
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        return new int[]{
                cfg.getInt("items.grappling_hook.max-range", 30),
                cfg.getInt("items.grappling_hook.cooldown-seconds", 3),
                cfg.getInt("items.grappling_hook.cancel-fall-damage-seconds", 4)
        };
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        var item = event.getItem();
        if (item == null) return;
        var registry = plugin.getCustomItemRegistry();
        if (!GrapplingHookItem.ID.equals(registry.getItemId(item))) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Cooldown check
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownMs) {
            long remaining = (cooldownMs - (now - last)) / 1000 + 1;
            player.sendMessage(Component.text("Grappling Hook on cooldown: " + remaining + "s", NamedTextColor.RED));
            return;
        }

        // Raycast
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), maxRange);

        if (result == null || result.getHitBlock() == null) {
            player.sendMessage(Component.text("No block in range!", NamedTextColor.RED));
            return;
        }

        Block target = result.getHitBlock();
        Location targetLoc = target.getLocation().add(0.5, 1.0, 0.5);
        Vector direction = targetLoc.toVector().subtract(player.getLocation().toVector()).normalize();
        double distance = player.getLocation().distance(targetLoc);
        double speed = Math.min(2.5, 0.8 + distance * 0.05);
        player.setVelocity(direction.multiply(speed));

        // Cancel fall damage briefly
        cooldowns.put(player.getUniqueId(), now);
        fallDamageCancel.put(player.getUniqueId(), now + cancelFallMs);

        // Consume durability
        if (item.getType().getMaxDurability() > 0) {
            var meta = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
            meta.setDamage(meta.getDamage() + 1);
            item.setItemMeta(meta);
            if (meta.getDamage() >= item.getType().getMaxDurability()) {
                item.setAmount(0);
                player.sendMessage(Component.text("Your Grappling Hook broke!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        Long cancel = fallDamageCancel.get(player.getUniqueId());
        if (cancel != null && System.currentTimeMillis() < cancel) {
            event.setCancelled(true);
            fallDamageCancel.remove(player.getUniqueId());
        }
    }
}
