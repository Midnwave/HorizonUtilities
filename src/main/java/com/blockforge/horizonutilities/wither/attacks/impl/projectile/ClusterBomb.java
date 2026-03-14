package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fires one large WitherSkull. When it dies/hits something, spawns 6 smaller
 * WitherSkulls radiating outward from the impact point in evenly-spaced
 * horizontal directions.
 */
public class ClusterBomb implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "cluster_bomb";
    }

    @Override
    public String getDisplayName() {
        return "Cluster Bomb";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 10.0;
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
                .subtract(origin.toVector()).normalize();

        WitherSkull mainSkull = boss.getWither().launchProjectile(WitherSkull.class, direction.multiply(1.2));
        mainSkull.setCharged(true);
        mainSkull.setYield(0);
        boss.getDisplayPool().track(mainSkull);

        // Track the main skull; when it dies, spawn cluster
        BukkitTask trackTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!mainSkull.isValid() || mainSkull.isDead()) {
                spawnCluster(boss, mainSkull.getLocation());
                // Cancel this task from within — scheduled for next tick removal
                return;
            }
            // Trail particle for main skull
            mainSkull.getWorld().spawnParticle(Particle.FLAME, mainSkull.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
        }, 1L, 2L);
        activeTasks.add(trackTask);

        // Timeout: force cluster if skull hasn't hit after 5 seconds
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            trackTask.cancel();
            activeTasks.remove(trackTask);
            if (mainSkull.isValid() && !mainSkull.isDead()) {
                Location impactLoc = mainSkull.getLocation();
                mainSkull.remove();
                spawnCluster(boss, impactLoc);
            }
        }, 100L);
        activeTasks.add(timeoutTask);

        // Wrap the track task to auto-cancel when skull dies
        BukkitTask wrapperTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!mainSkull.isValid() || mainSkull.isDead()) {
                trackTask.cancel();
                activeTasks.remove(trackTask);
            }
        }, 1L, 2L);
        activeTasks.add(wrapperTask);

        // Auto-cleanup wrapper after timeout
        Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            wrapperTask.cancel();
            activeTasks.remove(wrapperTask);
        }, 102L);
    }

    private void spawnCluster(WitherBossEntity boss, Location impact) {
        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 5, 0.5, 0.5, 0.5, 0);

        int clusterCount = 6;
        double angleStep = (2 * Math.PI) / clusterCount;

        for (int i = 0; i < clusterCount; i++) {
            double angle = i * angleStep;
            Vector dir = new Vector(Math.cos(angle), 0.15, Math.sin(angle)).normalize().multiply(0.8);

            WitherSkull cluster = impact.getWorld().spawn(impact.clone().add(0, 0.5, 0), WitherSkull.class);
            cluster.setVelocity(dir);
            cluster.setYield(0);
            boss.getDisplayPool().track(cluster);
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
