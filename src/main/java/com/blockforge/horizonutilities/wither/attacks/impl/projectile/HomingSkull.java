package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spawns a single charged (blue) WitherSkull that homes toward the nearest player.
 * A repeating task adjusts the skull's velocity every 2 ticks to curve toward its target.
 * Max tracking duration: 5 seconds (50 ticks). Soul particle trail.
 */
public class HomingSkull implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "homing_skull";
    }

    @Override
    public String getDisplayName() {
        return "Homing Skull";
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

        Vector direction = target.getLocation().add(0, 1, 0).toVector()
                .subtract(boss.getWither().getLocation().add(0, 1, 0).toVector()).normalize();

        WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction.multiply(0.8));
        skull.setCharged(true);
        skull.setYield(0);
        boss.getDisplayPool().track(skull);

        final double turnRate = 0.15;
        final double speed = 0.8;

        BukkitTask homingTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) return;

            // Trail particles
            skull.getWorld().spawnParticle(Particle.SOUL, skull.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);

            // Find current nearest player for tracking
            Player currentTarget = boss.getNearestPlayer(64);
            if (currentTarget == null) return;

            Vector toTarget = currentTarget.getLocation().add(0, 1, 0).toVector()
                    .subtract(skull.getLocation().toVector()).normalize();

            Vector currentVelocity = skull.getVelocity();
            Vector currentDir = currentVelocity.lengthSquared() > 0.001
                    ? currentVelocity.clone().normalize() : toTarget.clone();

            // Lerp direction toward target
            Vector newDir = currentDir.multiply(1.0 - turnRate).add(toTarget.multiply(turnRate)).normalize();
            skull.setVelocity(newDir.multiply(speed));
        }, 2L, 2L);
        activeTasks.add(homingTask);

        // Kill skull and cancel task after 5 seconds (50 ticks)
        BukkitTask killTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            homingTask.cancel();
            activeTasks.remove(homingTask);
            if (skull.isValid() && !skull.isDead()) {
                skull.remove();
            }
        }, 100L);
        activeTasks.add(killTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
