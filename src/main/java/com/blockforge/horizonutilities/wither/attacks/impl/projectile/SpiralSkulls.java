package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Over 3 seconds (60 ticks), fires a skull every 4 ticks in a rotating pattern
 * around the Wither. Each skull's direction rotates by ~24 degrees, creating a
 * spiral pattern. 15 skulls total.
 */
public class SpiralSkulls implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "spiral_skulls";
    }

    @Override
    public String getDisplayName() {
        return "Spiral Skulls";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 12.0;
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
        int totalSkulls = 15;
        double angleIncrement = Math.toRadians(24);

        for (int i = 0; i < totalSkulls; i++) {
            final int index = i;
            long delay = (long) i * 4L; // one skull every 4 ticks

            BukkitTask task = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                if (boss.isDead() || !boss.getWither().isValid()) return;

                double angle = index * angleIncrement;
                double x = Math.cos(angle);
                double z = Math.sin(angle);
                // Slight upward angle for visual effect
                Vector direction = new Vector(x, 0.1, z).normalize().multiply(1.0);

                Location origin = boss.getWither().getLocation().add(0, 1, 0);

                WitherSkull skull = origin.getWorld().spawn(origin.clone().add(x * 1.5, 0, z * 1.5), WitherSkull.class);
                skull.setVelocity(direction);
                skull.setYield(0);
                boss.getDisplayPool().track(skull);

                // Spiral particle effect at spawn
                origin.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, skull.getLocation(), 5,
                        0.1, 0.1, 0.1, 0.02);
            }, delay);
            activeTasks.add(task);
        }

        // Trail particles for all active skulls
        BukkitTask trailTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            // Particles spawned at skull locations handled by individual spawn above
        }, 1L, 4L);
        activeTasks.add(trailTask);

        // Cleanup trail task after spiral completes + travel time
        Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            trailTask.cancel();
            activeTasks.remove(trailTask);
        }, 120L);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
