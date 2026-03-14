package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fires 20 skulls straight upward with slight random X/Z spread. Skulls arc back
 * down due to gravity. Shadow circles on the ground mark landing zones. On impact:
 * 6 hearts damage + Wither I for 3 seconds.
 */
public class SkullRain implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "skull_rain";
    }

    @Override
    public String getDisplayName() {
        return "Skull Rain";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 15.0;
    }

    @Override
    public double getWeight(int phase) {
        return 1.5;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Location origin = boss.getWither().getLocation().add(0, 2, 0);
        World world = origin.getWorld();
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        int skullCount = 20;
        List<WitherSkull> skulls = new ArrayList<>();
        List<Location> estimatedLandings = new ArrayList<>();

        for (int i = 0; i < skullCount; i++) {
            double spreadX = rand.nextDouble(-0.3, 0.3);
            double spreadZ = rand.nextDouble(-0.3, 0.3);
            Vector velocity = new Vector(spreadX, 1.8, spreadZ);

            WitherSkull skull = world.spawn(origin.clone(), WitherSkull.class);
            skull.setVelocity(velocity);
            skull.setYield(0);
            boss.getDisplayPool().track(skull);
            skulls.add(skull);

            // Estimate landing position (rough horizontal displacement)
            double landX = origin.getX() + spreadX * 20;
            double landZ = origin.getZ() + spreadZ * 20;
            double landY = world.getHighestBlockYAt((int) landX, (int) landZ) + 0.05;
            estimatedLandings.add(new Location(world, landX, landY, landZ));
        }

        // Spawn shadow circles at estimated landing positions
        for (Location landing : estimatedLandings) {
            boss.getDisplayPool().spawnBlockDisplay(
                    landing,
                    Bukkit.createBlockData(Material.BLACK_CONCRETE),
                    60 // 3 seconds
            );
        }

        // Track skulls for impact damage
        BukkitTask impactTracker = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            boolean anyAlive = false;
            for (WitherSkull skull : skulls) {
                if (skull.isValid() && !skull.isDead()) {
                    anyAlive = true;
                    // Trail particle
                    world.spawnParticle(Particle.SMOKE, skull.getLocation(), 1, 0, 0, 0, 0.01);
                } else if (skull.isDead()) {
                    // Impact effects at skull's last location
                    Location impactLoc = skull.getLocation();
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, impactLoc, 10, 0.5, 0.2, 0.5, 0.05);

                    double radius = 3.0;
                    double radiusSq = radius * radius;
                    for (Player player : boss.getPlayersInRange(64)) {
                        if (player.getLocation().distanceSquared(impactLoc) <= radiusSq) {
                            player.damage(12.0, boss.getWither()); // 6 hearts
                            player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.WITHER, 60, 0, false, true, true));
                        }
                    }
                }
            }
            // Remove dead skulls from list to avoid re-processing
            skulls.removeIf(s -> !s.isValid() || s.isDead());
            if (!anyAlive && skulls.isEmpty()) {
                // All skulls handled
            }
        }, 10L, 4L);
        activeTasks.add(impactTracker);

        // Timeout after 6 seconds
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            impactTracker.cancel();
            activeTasks.remove(impactTracker);
            for (WitherSkull skull : skulls) {
                if (skull.isValid() && !skull.isDead()) {
                    skull.remove();
                }
            }
            skulls.clear();
        }, 120L);
        activeTasks.add(timeout);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
