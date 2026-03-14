package com.blockforge.horizonutilities.wither.attacks.impl.movement;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wither dashes backward 10 blocks away from the nearest player (opposite direction).
 * SMOKE poof at origin. Immediately after dash (5 tick delay), fires a Shotgun Blast:
 * 12 WitherSkulls in a tight forward spread toward the nearest player with SMOKE trails.
 */
public class EvasiveDash implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "evasive_dash";
    }

    @Override
    public String getDisplayName() {
        return "Evasive Dash";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(1, 2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 8.0;
    }

    @Override
    public double getWeight(int phase) {
        return 3.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Player target = boss.getNearestPlayer(64);
        if (target == null) return;

        World world = boss.getWither().getWorld();
        Location witherLoc = boss.getWither().getLocation();

        // Calculate backward direction (away from target)
        Vector awayFromTarget = witherLoc.toVector()
                .subtract(target.getLocation().toVector()).normalize();
        awayFromTarget.setY(0); // Keep horizontal
        if (awayFromTarget.lengthSquared() < 0.01) {
            awayFromTarget = new Vector(1, 0, 0); // Fallback
        }
        awayFromTarget.normalize();

        // Smoke poof at origin
        world.spawnParticle(Particle.SMOKE, witherLoc.clone().add(0, 1, 0), 25,
                0.5, 0.5, 0.5, 0.08);
        world.playSound(witherLoc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 1.5f);

        // Dash backward — apply strong velocity over a few ticks
        final Vector dashVelocity = awayFromTarget.clone().multiply(3.0);
        final int[] dashTick = {0};

        BukkitTask dashTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            dashTick[0]++;

            boss.getWither().setVelocity(dashVelocity);

            // Smoke trail during dash
            Location currentLoc = boss.getWither().getLocation();
            world.spawnParticle(Particle.SMOKE, currentLoc.add(0, 1, 0), 5,
                    0.3, 0.3, 0.3, 0.02);
        }, 0L, 1L);
        activeTasks.add(dashTask);

        // Stop dash after 4 ticks (~10 blocks at 3.0 speed)
        BukkitTask stopDash = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            dashTask.cancel();
            activeTasks.remove(dashTask);
        }, 4L);
        activeTasks.add(stopDash);

        // Shotgun blast 5 ticks after dash starts
        BukkitTask shotgunTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            if (boss.isDead()) return;

            Player shotgunTarget = boss.getNearestPlayer(64);
            if (shotgunTarget == null) return;

            Location fireOrigin = boss.getWither().getLocation().add(0, 1, 0);
            Vector baseDirection = shotgunTarget.getLocation().add(0, 1, 0).toVector()
                    .subtract(fireOrigin.toVector()).normalize();

            // Build orthogonal basis for cone spread
            Vector up = new Vector(0, 1, 0);
            Vector right = baseDirection.clone().crossProduct(up).normalize();
            if (right.lengthSquared() < 0.01) {
                right = baseDirection.clone().crossProduct(new Vector(1, 0, 0)).normalize();
            }
            Vector actualUp = right.clone().crossProduct(baseDirection).normalize();

            double halfSpread = Math.toRadians(15);
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            int skullCount = 12;
            List<WitherSkull> skulls = new ArrayList<>();

            // Fire sound
            world.playSound(fireOrigin, Sound.ENTITY_WITHER_SHOOT, 2.0f, 1.0f);

            for (int i = 0; i < skullCount; i++) {
                double yawOffset = rand.nextDouble(-halfSpread, halfSpread);
                double pitchOffset = rand.nextDouble(-halfSpread, halfSpread);

                Vector direction = baseDirection.clone()
                        .add(right.clone().multiply(Math.sin(yawOffset)))
                        .add(actualUp.clone().multiply(Math.sin(pitchOffset)))
                        .normalize()
                        .multiply(1.3);

                WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction);
                skull.setYield(0);
                boss.getDisplayPool().track(skull);
                skulls.add(skull);
            }

            // Smoke origin burst
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, fireOrigin, 15,
                    0.5, 0.3, 0.5, 0.02);

            // Smoke trails on each skull
            BukkitTask trailTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                boolean anyAlive = false;
                for (WitherSkull skull : skulls) {
                    if (skull.isValid() && !skull.isDead()) {
                        anyAlive = true;
                        skull.getWorld().spawnParticle(Particle.SMOKE, skull.getLocation(), 2,
                                0.1, 0.1, 0.1, 0.01);
                    }
                }
                if (!anyAlive) {
                    // All dead — timeout will clean up
                }
            }, 1L, 2L);
            activeTasks.add(trailTask);

            // Cancel trail after 5 seconds
            BukkitTask trailTimeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                trailTask.cancel();
                activeTasks.remove(trailTask);
            }, 100L);
            activeTasks.add(trailTimeout);

        }, 5L);
        activeTasks.add(shotgunTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
