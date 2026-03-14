package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
 * Fires 7 WitherSkulls in a horizontal line perpendicular to the direction
 * toward the target, spaced 2 blocks apart. A connecting particle beam is
 * drawn between adjacent skulls via a trail task.
 */
public class SkullWall implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "skull_wall";
    }

    @Override
    public String getDisplayName() {
        return "Skull Wall";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
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

        Location origin = boss.getWither().getLocation().add(0, 1, 0);
        Vector baseDirection = target.getLocation().add(0, 1, 0).toVector()
                .subtract(origin.toVector()).normalize();

        // Build perpendicular (right) vector for the wall spread
        Vector up = new Vector(0, 1, 0);
        Vector right = baseDirection.clone().crossProduct(up).normalize();
        if (right.lengthSquared() < 0.01) {
            right = baseDirection.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        }

        int skullCount = 7;
        double spacing = 2.0;
        double totalWidth = spacing * (skullCount - 1);
        List<WitherSkull> skulls = new ArrayList<>();

        for (int i = 0; i < skullCount; i++) {
            double offset = -totalWidth / 2.0 + i * spacing;
            Location spawnLoc = origin.clone().add(right.clone().multiply(offset));

            Vector velocity = baseDirection.clone().multiply(1.2);
            WitherSkull skull = boss.getWither().getWorld().spawn(spawnLoc, WitherSkull.class);
            skull.setVelocity(velocity);
            skull.setYield(0);
            boss.getDisplayPool().track(skull);
            skulls.add(skull);
        }

        // Connecting beam trail between adjacent skulls
        Particle.DustOptions darkDust = new Particle.DustOptions(Color.fromRGB(60, 0, 80), 1.0f);
        BukkitTask trailTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            boolean anyAlive = false;

            for (int i = 0; i < skulls.size() - 1; i++) {
                WitherSkull a = skulls.get(i);
                WitherSkull b = skulls.get(i + 1);

                if (a.isValid() && !a.isDead() && b.isValid() && !b.isDead()) {
                    anyAlive = true;
                    WitherParticles.beam(a.getLocation(), b.getLocation(), Particle.DUST, darkDust, 2.0);
                }
            }

            // Also draw individual skull trails
            for (WitherSkull skull : skulls) {
                if (skull.isValid() && !skull.isDead()) {
                    anyAlive = true;
                    skull.getWorld().spawnParticle(Particle.SMOKE, skull.getLocation(), 2,
                            0.1, 0.1, 0.1, 0.01);
                }
            }

            if (!anyAlive) {
                // All skulls dead — task will be cleaned up by timeout
            }
        }, 1L, 2L);
        activeTasks.add(trailTask);

        // Auto-cancel after 5 seconds
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            trailTask.cancel();
            activeTasks.remove(trailTask);
        }, 100L);
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
