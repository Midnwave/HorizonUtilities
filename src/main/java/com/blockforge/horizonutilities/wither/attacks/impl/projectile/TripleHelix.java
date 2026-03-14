package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
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
 * Launches 3 WitherSkull entities that travel in a DNA helix pattern toward the target.
 * Each skull has a different colored dust trail (red, blue, purple) and is offset
 * 120 degrees apart using sin/cos perpendicular to the travel direction.
 */
public class TripleHelix implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "triple_helix";
    }

    @Override
    public String getDisplayName() {
        return "Triple Helix";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 12.0;
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
        Vector baseDirection = target.getLocation().add(0, 1, 0).toVector()
                .subtract(origin.toVector()).normalize();

        // Build orthogonal basis vectors for the helix plane
        Vector up = new Vector(0, 1, 0);
        Vector right = baseDirection.clone().crossProduct(up).normalize();
        if (right.lengthSquared() < 0.01) {
            right = baseDirection.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        }
        Vector orthoUp = right.clone().crossProduct(baseDirection).normalize();

        // Colors for each skull trail
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.2f);
        Particle.DustOptions blueDust = new Particle.DustOptions(Color.fromRGB(40, 80, 255), 1.2f);
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(180, 40, 255), 1.2f);
        Particle.DustOptions[] dustColors = {redDust, blueDust, purpleDust};

        double helixRadius = 1.5;
        double angularSpeed = Math.PI / 5; // radians per tick
        double forwardSpeed = 1.0;

        List<WitherSkull> skulls = new ArrayList<>();

        // Launch 3 skulls, each offset 120 degrees apart
        for (int i = 0; i < 3; i++) {
            double phaseOffset = (2 * Math.PI / 3) * i;
            Vector initialOffset = right.clone().multiply(Math.cos(phaseOffset) * helixRadius)
                    .add(orthoUp.clone().multiply(Math.sin(phaseOffset) * helixRadius));

            Location spawnLoc = origin.clone().add(initialOffset);
            Vector initialVelocity = baseDirection.clone().multiply(forwardSpeed);

            WitherSkull skull = boss.getWither().getWorld().spawn(spawnLoc, WitherSkull.class);
            skull.setVelocity(initialVelocity);
            skull.setYield(0);
            boss.getDisplayPool().track(skull);
            skulls.add(skull);
        }

        // Helix movement + trail task
        final Vector fRight = right;
        final Vector fOrthoUp = orthoUp;
        final int[] tickCounter = {0};

        BukkitTask helixTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            tickCounter[0]++;
            boolean anyAlive = false;

            for (int i = 0; i < skulls.size(); i++) {
                WitherSkull skull = skulls.get(i);
                if (!skull.isValid() || skull.isDead()) continue;
                anyAlive = true;

                double phaseOffset = (2 * Math.PI / 3) * i;
                double angle = angularSpeed * tickCounter[0] + phaseOffset;

                // Set velocity: forward component + perpendicular oscillation
                Vector perpVelocity = fRight.clone().multiply(-Math.sin(angle) * helixRadius * angularSpeed)
                        .add(fOrthoUp.clone().multiply(Math.cos(angle) * helixRadius * angularSpeed));
                Vector totalVelocity = baseDirection.clone().multiply(forwardSpeed).add(perpVelocity.multiply(0.3));
                skull.setVelocity(totalVelocity);

                // Colored dust trail
                skull.getWorld().spawnParticle(Particle.DUST, skull.getLocation(), 3,
                        0.1, 0.1, 0.1, 0, dustColors[i]);
            }

            if (!anyAlive) {
                // All skulls dead — task will be cleaned up by timeout
            }
        }, 1L, 1L);
        activeTasks.add(helixTask);

        // Auto-cancel after 6 seconds (120 ticks)
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            helixTask.cancel();
            activeTasks.remove(helixTask);
            for (WitherSkull skull : skulls) {
                if (skull.isValid() && !skull.isDead()) {
                    skull.remove();
                }
            }
        }, 120L);
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
