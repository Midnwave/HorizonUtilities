package com.blockforge.horizonutilities.wither;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.UUID;

/**
 * Bukkit Listener for the custom Wither Sovereign boss fight.
 * Handles damage tracking, death events, explosion prevention,
 * target override, and vanilla wither spawn control.
 */
public class WitherBossListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final WitherBossManager manager;

    public WitherBossListener(HorizonUtilitiesPlugin plugin, WitherBossManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Tracks damage dealt to the custom Wither by players, handles
     * wither-effect immunity and explosion damage reduction.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        if (!(damaged instanceof Wither wither)) return;

        UUID witherUuid = wither.getUniqueId();
        if (!manager.isCustomWither(witherUuid)) return;

        WitherBossEntity boss = manager.getBoss(witherUuid);
        if (boss == null) return;

        WitherBossConfig config = manager.getConfig();

        // Wither effect immunity — cancel damage from the WITHER damage cause
        if (config.isWitherImmune()
                && event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
            event.setCancelled(true);
            return;
        }

        // Explosion resistance — reduce explosion damage by 80%
        if (config.isExplosionResistant()
                && (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) {
            event.setDamage(event.getDamage() * 0.2);
        }

        // Record damage to aggro table if the damager is a player
        Entity damager = event.getDamager();

        // Resolve the actual player — handle projectiles etc.
        Player attacker = resolvePlayerDamager(damager);
        if (attacker != null) {
            boss.recordDamage(attacker.getUniqueId(), event.getFinalDamage());
        }
    }

    /**
     * Handles custom Wither death — triggers loot distribution, stops music,
     * and cleans up the boss. Also handles minion death tracking.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();
        UUID deadUuid = dead.getUniqueId();

        // Check if this is a tracked custom Wither
        if (dead instanceof Wither && manager.isCustomWither(deadUuid)) {
            WitherBossEntity boss = manager.getBoss(deadUuid);
            if (boss == null) return;

            // Clear vanilla drops — we handle our own loot
            event.getDrops().clear();
            event.setDroppedExp(0);

            // Drop custom loot
            manager.getLootManager().dropLoot(boss);

            // Remove the boss (stops music, cleans up resources)
            manager.removeBoss(boss);

            plugin.getLogger().info("Wither Sovereign defeated after "
                    + boss.getFightDurationSeconds() + "s by "
                    + boss.getParticipants().size() + " participants.");
            return;
        }

        // Check if this entity is a minion of any active boss
        WitherBossEntity ownerBoss = manager.findBossOwningMinion(deadUuid);
        if (ownerBoss != null) {
            ownerBoss.removeMinion(deadUuid);
            ownerBoss.recordMinionDeath(
                    dead.getLocation(),
                    dead.getType().name()
            );
        }
    }

    /**
     * Prevents the custom Wither from destroying blocks via its vanilla explosion.
     * Our custom attacks handle terrain destruction separately.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Wither && manager.isCustomWither(entity.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancels vanilla targeting for the custom Wither — we handle
     * all target selection via the AI controller.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Wither && manager.isCustomWither(entity.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Controls vanilla Wither spawning via the soul sand/soul soil T-shape.
     * If vanilla withers are disabled in config, cancels the spawn and
     * informs nearby players. Custom withers are spawned via the
     * WitherBossManager, not through vanilla mechanics.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.WITHER) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_WITHER) return;

        WitherBossConfig config = manager.getConfig();
        if (!config.isAllowVanillaWither()) {
            event.setCancelled(true);

            // Notify nearby players
            event.getLocation().getNearbyPlayers(16).forEach(player ->
                    player.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                            .append(Component.text(
                                    "Vanilla Wither spawning is disabled. Use the ritual altar to summon the Wither Sovereign!",
                                    NamedTextColor.GRAY)))
            );
        }
        // If allowed, let it spawn normally — it won't be tracked as a custom boss
    }

    /**
     * Resolves the actual Player from a damager entity.
     * Handles direct player hits and projectiles shot by players.
     */
    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        if (damager instanceof org.bukkit.entity.Tameable tameable) {
            if (tameable.getOwner() instanceof Player owner) {
                return owner;
            }
        }
        return null;
    }
}
