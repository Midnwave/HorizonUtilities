package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fires a skull at the ground beneath the target player. On impact, spawns an
 * expanding ring of particles at ground level, damages all players within 5 blocks,
 * and launches them upward.
 */
public class GroundSlamSkull implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "ground_slam_skull";
    }

    @Override
    public String getDisplayName() {
        return "Ground Slam";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(1, 2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 7.0;
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
        Player target = boss.getNearestPlayer(64);
        if (target == null) return;

        // Aim at the ground beneath the target
        Location groundTarget = target.getLocation().clone();
        groundTarget.setY(groundTarget.getWorld().getHighestBlockYAt(groundTarget));

        Location origin = boss.getWither().getLocation().add(0, 1, 0);
        Vector direction = groundTarget.toVector().subtract(origin.toVector()).normalize();

        WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction.multiply(1.5));
        skull.setYield(0);
        boss.getDisplayPool().track(skull);

        // Track skull for impact
        BukkitTask trackTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) {
                onImpact(boss, skull.getLocation());
                return;
            }
        }, 1L, 2L);
        activeTasks.add(trackTask);

        // Auto-cancel on skull death
        BukkitTask deathCheck = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) {
                trackTask.cancel();
                activeTasks.remove(trackTask);
            }
        }, 1L, 2L);
        activeTasks.add(deathCheck);

        // Timeout after 5 seconds
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            trackTask.cancel();
            deathCheck.cancel();
            activeTasks.remove(trackTask);
            activeTasks.remove(deathCheck);
            if (skull.isValid() && !skull.isDead()) {
                Location impactLoc = skull.getLocation();
                skull.remove();
                onImpact(boss, impactLoc);
            }
        }, 100L);
        activeTasks.add(timeout);
    }

    private void onImpact(WitherBossEntity boss, Location impact) {
        World world = impact.getWorld();

        // Ground-level impact location
        Location groundImpact = impact.clone();
        groundImpact.setY(world.getHighestBlockYAt(impact) + 1);

        // Expanding ring effect
        WitherParticles.expandingRing(groundImpact, 0.5, 5.0, 8, 2, Particle.SOUL_FIRE_FLAME, boss.getPlugin());

        // Damage and launch players within 5 blocks
        double radius = 5.0;
        double radiusSq = radius * radius;
        double damage = 10.0; // 5 hearts

        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(groundImpact) <= radiusSq) {
                player.damage(damage, boss.getWither());

                // Launch upward
                Vector launch = player.getLocation().toVector().subtract(groundImpact.toVector());
                if (launch.lengthSquared() < 0.01) {
                    launch = new Vector(0, 1, 0);
                } else {
                    launch.normalize();
                }
                launch.setY(1.2);
                launch.multiply(0.8);
                player.setVelocity(player.getVelocity().add(launch));
            }
        }

        // Impact particles
        world.spawnParticle(Particle.EXPLOSION, groundImpact, 3, 0.5, 0.2, 0.5, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, groundImpact, 30, 1.5, 0.2, 1.5, 0.05);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
