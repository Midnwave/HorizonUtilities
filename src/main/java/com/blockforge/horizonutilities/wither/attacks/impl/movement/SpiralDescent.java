package com.blockforge.horizonutilities.wither.attacks.impl.movement;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wither corkscrews downward in a spiral pattern over 40 ticks. Each tick
 * the velocity follows a helical path (decreasing Y, rotating X/Z).
 * Expanding dark ring at ground level using SOUL_FIRE_FLAME. On reaching
 * ground: 8-block AoE, 15 HP damage, knockback.
 */
public class SpiralDescent implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "spiral_descent";
    }

    @Override
    public String getDisplayName() {
        return "Spiral Descent";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 14.0;
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
        World world = boss.getWither().getWorld();

        Player target = boss.getNearestPlayer(64);
        if (target == null) return;

        Location groundTarget = target.getLocation().clone();
        groundTarget.setY(world.getHighestBlockYAt(groundTarget) + 1);

        // Spiral parameters
        double spiralRadius = 4.0;
        double angularSpeed = Math.PI / 8; // radians per tick
        double descentRate = -0.6; // blocks per tick downward

        // Warning sound
        world.playSound(boss.getWither().getLocation(), Sound.ENTITY_WITHER_AMBIENT, 2.0f, 1.5f);

        final int[] tick = {0};
        final boolean[] impacted = {false};

        BukkitTask spiralTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead() || impacted[0]) return;
            tick[0]++;

            double angle = angularSpeed * tick[0];

            // Helical velocity: rotating X/Z + descending Y
            double vx = -Math.sin(angle) * spiralRadius * angularSpeed;
            double vz = Math.cos(angle) * spiralRadius * angularSpeed;
            double vy = descentRate;

            boss.getWither().setVelocity(new Vector(vx, vy, vz));

            // Spiral trail particles
            Location witherLoc = boss.getWither().getLocation();
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, witherLoc, 5, 0.2, 0.1, 0.2, 0.02);
            world.spawnParticle(Particle.SMOKE, witherLoc, 3, 0.3, 0.1, 0.3, 0.01);

            // Expanding dark ring at ground level — grows as wither descends
            double progress = (double) tick[0] / 40;
            double ringRadius = 2.0 + progress * 6.0;
            WitherParticles.ring(groundTarget, ringRadius, (int) (16 + progress * 16), Particle.SOUL_FIRE_FLAME, null);

            // Check if wither has reached ground level
            double groundY = world.getHighestBlockYAt(witherLoc) + 1;
            if (witherLoc.getY() <= groundY + 2) {
                impacted[0] = true;
                onGroundImpact(boss, witherLoc);
            }
        }, 0L, 1L);
        activeTasks.add(spiralTask);

        // Force impact after 40 ticks if still descending
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            spiralTask.cancel();
            activeTasks.remove(spiralTask);

            if (!boss.isDead() && !impacted[0]) {
                impacted[0] = true;
                onGroundImpact(boss, boss.getWither().getLocation());
            }
        }, 40L);
        activeTasks.add(timeout);
    }

    private void onGroundImpact(WitherBossEntity boss, Location impact) {
        World world = impact.getWorld();

        // Snap to ground
        Location groundImpact = impact.clone();
        groundImpact.setY(world.getHighestBlockYAt(impact) + 1);

        // Impact particles
        world.spawnParticle(Particle.EXPLOSION, groundImpact, 5, 1.0, 0.5, 1.0, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, groundImpact, 50, 3.0, 0.3, 3.0, 0.1);
        world.spawnParticle(Particle.SMOKE, groundImpact, 30, 2.0, 0.5, 2.0, 0.05);
        world.playSound(groundImpact, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);

        // Expanding ring shockwave
        WitherParticles.expandingRing(groundImpact, 1.0, 8.0, 10, 2, Particle.SOUL_FIRE_FLAME, boss.getPlugin());

        // 8-block AoE, 15 HP damage, knockback
        double radius = 8.0;
        double radiusSq = radius * radius;

        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(groundImpact) <= radiusSq) {
                player.damage(15.0, boss.getWither());

                // Knockback away from impact
                Vector knockback = player.getLocation().toVector().subtract(groundImpact.toVector());
                if (knockback.lengthSquared() < 0.01) {
                    knockback = new Vector(0, 1.2, 0);
                } else {
                    knockback.normalize().multiply(1.0);
                    knockback.setY(0.8);
                }
                player.setVelocity(player.getVelocity().add(knockback));
            }
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
