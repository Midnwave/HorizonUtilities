package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fires a skull toward the target. When the skull is about to hit a block,
 * its velocity is reflected off the surface normal (bounce). Max 3 bounces.
 * Spark particles (CRIT) on each bounce. Killed after 3 bounces or 5 seconds.
 */
public class BouncingSkull implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "bouncing_skull";
    }

    @Override
    public String getDisplayName() {
        return "Bouncing Skull";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(1, 2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 6.0;
    }

    @Override
    public double getWeight(int phase) {
        return 2.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Player target = boss.getNearestPlayer(64);
        if (target == null) return;

        Location origin = boss.getWither().getLocation().add(0, 1, 0);
        Vector direction = target.getLocation().add(0, 1, 0).toVector()
                .subtract(origin.toVector()).normalize().multiply(1.2);

        WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction);
        skull.setYield(0);
        boss.getDisplayPool().track(skull);

        final int[] bounceCount = {0};
        final int maxBounces = 3;

        BukkitTask bounceTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) return;

            Vector velocity = skull.getVelocity();
            if (velocity.lengthSquared() < 0.001) return;

            // Check ahead for block collision
            Location current = skull.getLocation();
            Location ahead = current.clone().add(velocity.clone().normalize().multiply(0.5));
            Block blockAhead = ahead.getBlock();

            if (blockAhead.getType() != Material.AIR && !blockAhead.isLiquid()) {
                if (bounceCount[0] >= maxBounces) {
                    skull.remove();
                    return;
                }

                // Determine surface normal based on which face was hit
                Vector normal = determineSurfaceNormal(current, blockAhead);

                // Reflect velocity: v' = v - 2(v . n)n
                double dot = velocity.dot(normal);
                Vector reflected = velocity.subtract(normal.multiply(2 * dot));
                reflected.multiply(0.85); // Energy loss on bounce

                skull.setVelocity(reflected);
                bounceCount[0]++;

                // Spark particles on bounce
                skull.getWorld().spawnParticle(Particle.CRIT, skull.getLocation(), 15,
                        0.3, 0.3, 0.3, 0.1);
                skull.getWorld().spawnParticle(Particle.FLAME, skull.getLocation(), 5,
                        0.1, 0.1, 0.1, 0.05);
            }

            // Trail particles
            skull.getWorld().spawnParticle(Particle.SMOKE, skull.getLocation(), 1, 0, 0, 0, 0.01);
        }, 1L, 1L);
        activeTasks.add(bounceTask);

        // Kill after 5 seconds
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            bounceTask.cancel();
            activeTasks.remove(bounceTask);
            if (skull.isValid() && !skull.isDead()) {
                skull.remove();
            }
        }, 100L);
        activeTasks.add(timeout);
    }

    private Vector determineSurfaceNormal(Location skullLoc, Block hitBlock) {
        Location blockCenter = hitBlock.getLocation().add(0.5, 0.5, 0.5);
        Vector diff = skullLoc.toVector().subtract(blockCenter.toVector());

        // Find the dominant axis to determine which face was hit
        double absX = Math.abs(diff.getX());
        double absY = Math.abs(diff.getY());
        double absZ = Math.abs(diff.getZ());

        if (absX >= absY && absX >= absZ) {
            return new Vector(Math.signum(diff.getX()), 0, 0);
        } else if (absY >= absX && absY >= absZ) {
            return new Vector(0, Math.signum(diff.getY()), 0);
        } else {
            return new Vector(0, 0, Math.signum(diff.getZ()));
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
