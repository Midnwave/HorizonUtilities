package com.blockforge.horizonutilities.wither.attacks.impl.terrain;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 3 dark tendrils erupt under random players within 32 blocks.
 * Each tendril spawns SMOKE + SOUL particles in a 2-block radius, then after 1 second
 * deals 12 damage (6 hearts) and applies Darkness for 1 second.
 * Tendrils are staggered by 10 ticks each.
 */
public class AbyssalGrasp implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "abyssal_grasp";
    }

    @Override
    public String getDisplayName() {
        return "Abyssal Grasp";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 20.0;
    }

    @Override
    public double getWeight(int phase) {
        return 2.5;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        List<Player> targets = boss.getPlayersInRange(32);
        if (targets.isEmpty()) return;

        World world = boss.getWither().getWorld();

        // Shuffle and pick up to 3 players
        List<Player> shuffled = new ArrayList<>(targets);
        Collections.shuffle(shuffled);
        int tendrilCount = Math.min(3, shuffled.size());

        // Capture target locations at casting
        List<Location> tendrilLocations = new ArrayList<>();
        for (int i = 0; i < tendrilCount; i++) {
            Location loc = shuffled.get(i).getLocation().clone();
            loc.setY(world.getHighestBlockYAt(loc) + 1);
            tendrilLocations.add(loc);
        }

        // Initial casting sound
        world.playSound(boss.getWither().getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.5f);

        // Stagger each tendril by 10 ticks
        for (int i = 0; i < tendrilLocations.size(); i++) {
            final Location tendrilLoc = tendrilLocations.get(i);
            long staggerDelay = (long) i * 10L;

            // Warning phase: spawn particles at tendril location
            BukkitTask warningTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                if (boss.isDead()) return;

                // SMOKE + SOUL particles in 2-block radius as warning
                spawnTendrilParticles(world, tendrilLoc);

                // Warning sound
                world.playSound(tendrilLoc, Sound.BLOCK_SCULK_SPREAD, 1.5f, 0.5f);
            }, staggerDelay);
            activeTasks.add(warningTask);

            // Eruption phase: 1 second (20 ticks) after warning
            BukkitTask eruptionTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                if (boss.isDead()) return;

                // Eruption particles — intense burst
                for (double y = 0; y <= 4; y += 0.4) {
                    Location pillarLoc = tendrilLoc.clone().add(0, y, 0);
                    world.spawnParticle(Particle.SOUL, pillarLoc, 5, 0.4, 0.1, 0.4, 0.02);
                    world.spawnParticle(Particle.SMOKE, pillarLoc, 3, 0.3, 0.1, 0.3, 0.01);
                }

                // Eruption sound
                world.playSound(tendrilLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.6f);

                // Damage players within 2-block radius
                double radiusSq = 4.0; // 2^2
                for (Player player : boss.getPlayersInRange(35)) {
                    if (player.getLocation().distanceSquared(tendrilLoc) <= radiusSq) {
                        // 12 damage (6 hearts)
                        player.damage(12.0, boss.getWither());
                        // Darkness for 1 second (20 ticks)
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.DARKNESS, 20, 0, false, true, true));
                    }
                }
            }, staggerDelay + 20L);
            activeTasks.add(eruptionTask);
        }
    }

    private void spawnTendrilParticles(World world, Location center) {
        double radius = 2.0;
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            Location particleLoc = new Location(world, x, center.getY() + 0.1, z);
            world.spawnParticle(Particle.SMOKE, particleLoc, 3, 0.1, 0.05, 0.1, 0.01);
            world.spawnParticle(Particle.SOUL, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);
        }
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
