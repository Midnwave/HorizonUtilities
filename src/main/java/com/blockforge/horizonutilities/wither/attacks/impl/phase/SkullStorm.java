package com.blockforge.horizonutilities.wither.attacks.impl.phase;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase transition attack: The Wither becomes invulnerable for 5 seconds,
 * surrounded by a white glow, and fires 80+ skulls in random directions
 * before a massive explosion reveal.
 */
public class SkullStorm implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "skull_storm";
    }

    @Override
    public String getDisplayName() {
        return "Skull Storm";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(1, 2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 999.0;
    }

    @Override
    public double getWeight(int phase) {
        return 0.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        World world = boss.getWither().getWorld();
        Location witherLoc = boss.getWither().getLocation();

        // Make the wither invulnerable and disable AI
        boss.getWither().setAI(false);
        boss.getWither().setInvulnerable(true);

        // Spawn sound
        world.playSound(witherLoc, Sound.ENTITY_WITHER_SPAWN, 3.0f, 1.0f);

        // Phase 1: 5 seconds (100 ticks) of invulnerability with white glow and skull barrage
        // Every tick: white END_ROD sphere around wither
        BukkitTask glowTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            Location currentLoc = boss.getWither().getLocation();
            WitherParticles.sphere(currentLoc.clone().add(0, 1, 0), 3.0, 40, Particle.END_ROD, null);
        }, 0L, 1L);
        activeTasks.add(glowTask);

        // Every 5 ticks: fire 8 WitherSkulls in random directions (20 volleys = 160 skulls over 100 ticks)
        BukkitTask skullTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Location currentLoc = boss.getWither().getLocation().add(0, 1, 0);

            for (int i = 0; i < 8; i++) {
                double yaw = rng.nextDouble() * 2 * Math.PI;
                double pitch = rng.nextDouble(-0.5, 0.8);
                Vector direction = new Vector(
                        Math.cos(yaw) * Math.cos(pitch),
                        Math.sin(pitch),
                        Math.sin(yaw) * Math.cos(pitch)
                ).normalize().multiply(0.8 + rng.nextDouble() * 0.6);

                WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction);
                skull.setYield(0);
                boss.getDisplayPool().track(skull);
            }

            // Firing sound
            world.playSound(currentLoc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.8f + rng.nextFloat() * 0.4f);
        }, 0L, 5L);
        activeTasks.add(skullTask);

        // Soul fire flame trail on each skull — track skulls with a particle task
        BukkitTask trailTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(WitherSkull.class)) {
                if (entity.isValid()) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation(), 2,
                            0.1, 0.1, 0.1, 0.02);
                }
            }
        }, 1L, 2L);
        activeTasks.add(trailTask);

        // Phase 2: After 100 ticks — massive explosion burst, restore AI, remove invulnerability
        BukkitTask finishTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            // Cancel ongoing tasks
            glowTask.cancel();
            activeTasks.remove(glowTask);
            skullTask.cancel();
            activeTasks.remove(skullTask);
            trailTask.cancel();
            activeTasks.remove(trailTask);

            if (boss.isDead()) return;

            Location finalLoc = boss.getWither().getLocation().add(0, 1, 0);

            // Massive explosion burst
            WitherParticles.explosion(finalLoc, 80, Particle.EXPLOSION);
            WitherParticles.sphere(finalLoc, 5.0, 60, Particle.END_ROD, null);
            world.spawnParticle(Particle.FLASH, finalLoc, 3, 0, 0, 0, 0);

            // Explosion sound
            world.playSound(finalLoc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);

            // Restore AI and remove invulnerability
            boss.getWither().setAI(true);
            boss.getWither().setInvulnerable(false);
        }, 100L);
        activeTasks.add(finishTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
