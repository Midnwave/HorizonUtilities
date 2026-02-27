package com.blockforge.horizonutilities.customitems.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.customitems.items.IceBombItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IceBombListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final Map<UUID, Long> cooldowns       = new ConcurrentHashMap<>();
    /** Tracks which snowball UUIDs are ice bombs */
    private final Map<UUID, UUID> activeBombs     = new ConcurrentHashMap<>(); // projectile -> thrower

    private final int radius;
    private final int freezeTicks;
    private final long cooldownMs;

    public IceBombListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        var f   = new java.io.File(plugin.getDataFolder(), "custom-items.yml");
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        radius     = cfg.getInt("items.ice_bomb.freeze-radius", 5);
        freezeTicks = cfg.getInt("items.ice_bomb.freeze-duration-seconds", 10) * 20;
        cooldownMs  = cfg.getLong("items.ice_bomb.cooldown-seconds", 10) * 1000L;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        var item = event.getItem();
        if (item == null) return;
        var registry = plugin.getCustomItemRegistry();
        if (!IceBombItem.ID.equals(registry.getItemId(item))) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        long now = System.currentTimeMillis();
        if (now - cooldowns.getOrDefault(player.getUniqueId(), 0L) < cooldownMs) {
            long rem = (cooldownMs - (now - cooldowns.get(player.getUniqueId()))) / 1000 + 1;
            player.sendMessage(Component.text("Ice Bomb cooldown: " + rem + "s", NamedTextColor.RED));
            return;
        }

        // Throw snowball projectile and mark it
        Snowball snowball = player.launchProjectile(Snowball.class);
        activeBombs.put(snowball.getUniqueId(), player.getUniqueId());
        cooldowns.put(player.getUniqueId(), now);

        // Consume one snowball from hand
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else item.setAmount(0);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        UUID throwerUuid = activeBombs.remove(snowball.getUniqueId());
        if (throwerUuid == null) return;

        Location center = snowball.getLocation();

        // Freeze nearby water blocks -> ICE
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = center.getBlock().getRelative(dx, dy, dz);
                    if (block.getType() == Material.WATER) {
                        block.setType(Material.ICE);
                        // Schedule melt
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (block.getType() == Material.ICE) block.setType(Material.WATER);
                        }, freezeTicks);
                    }
                }
            }
        }

        // Apply slowness + freeze effect to nearby living entities
        for (var entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player p &&
                    p.getUniqueId().equals(throwerUuid))) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks, 4, false, true));
                living.setFreezeTicks(freezeTicks);
            }
        }
    }
}
