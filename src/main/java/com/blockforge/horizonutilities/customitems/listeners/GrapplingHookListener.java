package com.blockforge.horizonutilities.customitems.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.customitems.items.GrapplingHookItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GrapplingHookListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final Map<UUID, Long> cooldowns        = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fallDamageCancel  = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activePulls = new ConcurrentHashMap<>();

    private final int maxRange;
    private final long cooldownMs;
    private final long cancelFallMs;

    /** Pull speed per tick (blocks/tick). Player arrives over ~0.5-2 seconds. */
    private static final double PULL_SPEED = 0.8;
    /** Distance at which the pull stops (blocks). */
    private static final double ARRIVAL_DISTANCE = 1.5;
    /** Max ticks before the pull auto-cancels (safety net). */
    private static final int MAX_PULL_TICKS = 80; // 4 seconds
    /** Particle color for the rope line. */
    private static final Particle.DustOptions ROPE_DUST =
            new Particle.DustOptions(Color.fromRGB(139, 90, 43), 1.0f); // brown rope

    public GrapplingHookListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        var config  = loadSection();
        this.maxRange     = config[0];
        this.cooldownMs   = config[1] * 1000L;
        this.cancelFallMs = config[2] * 1000L;
    }

    private int[] loadSection() {
        var f = new java.io.File(plugin.getDataFolder(), "custom-items.yml");
        if (!f.exists()) {
            try { plugin.saveResource("custom-items.yml", false); } catch (Exception ignored) {}
        }
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

        // Cancel any existing pull
        cancelPull(player.getUniqueId());

        // Cooldown check
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownMs) {
            long remaining = (cooldownMs - (now - last)) / 1000 + 1;
            player.sendMessage(Component.text("Grappling Hook on cooldown: " + remaining + "s", NamedTextColor.RED));
            return;
        }

        // Raycast to find target block
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), maxRange);

        if (result == null || result.getHitBlock() == null) {
            player.sendMessage(Component.text("No block in range!", NamedTextColor.RED));
            return;
        }

        Block target = result.getHitBlock();
        Location targetLoc = target.getLocation().add(0.5, 1.0, 0.5);

        // Record cooldown and fall damage cancel
        cooldowns.put(player.getUniqueId(), now);
        fallDamageCancel.put(player.getUniqueId(), now + cancelFallMs);

        // Play leash attach sound
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.2f);

        // Start the gradual pull with rope particle line
        final UUID uuid = player.getUniqueId();
        final World world = player.getWorld();
        final int[] tickCount = {0};

        BukkitTask pullTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tickCount[0]++;

            // Safety: stop if player went offline, changed worlds, or max ticks exceeded
            if (!player.isOnline() || !player.getWorld().equals(world) || tickCount[0] > MAX_PULL_TICKS) {
                cancelPull(uuid);
                return;
            }

            Location playerLoc = player.getLocation().add(0, 0.5, 0);
            double distance = playerLoc.distance(targetLoc);

            // Arrived — stop pulling
            if (distance < ARRIVAL_DISTANCE) {
                player.setVelocity(new Vector(0, 0, 0)); // stop momentum
                cancelPull(uuid);
                world.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.8f, 1.0f);
                return;
            }

            // Draw rope particle line from player hand to target
            drawParticleLine(world, playerLoc, targetLoc);

            // Apply pull velocity toward target
            Vector direction = targetLoc.toVector().subtract(playerLoc.toVector()).normalize();
            double speed = Math.min(PULL_SPEED, distance * 0.3); // ease in, cap at PULL_SPEED
            player.setVelocity(direction.multiply(speed));

        }, 1L, 1L); // every tick

        activePulls.put(uuid, pullTask);

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

    /** Draws a particle line between two locations. */
    private void drawParticleLine(World world, Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        dir.normalize();
        // One particle every 0.5 blocks
        for (double d = 0; d < length; d += 0.5) {
            Location point = from.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, ROPE_DUST);
        }
    }

    /** Cancels an active pull for the given player. */
    private void cancelPull(UUID uuid) {
        BukkitTask task = activePulls.remove(uuid);
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Long cancel = fallDamageCancel.get(player.getUniqueId());
        if (cancel != null && System.currentTimeMillis() < cancel) {
            event.setCancelled(true);
            fallDamageCancel.remove(player.getUniqueId());
        }
    }
}
