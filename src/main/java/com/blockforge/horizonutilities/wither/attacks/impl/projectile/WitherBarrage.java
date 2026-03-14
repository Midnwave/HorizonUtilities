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
 * Fires 8 WitherSkull entities in a cone spread toward the target player.
 * Each skull is slightly offset in direction (~30 degrees total spread).
 * Purple dust trail particles follow each skull via a repeating task.
 */
public class WitherBarrage implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "wither_barrage";
    }

    @Override
    public String getDisplayName() {
        return "Wither Barrage";
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

        Location origin = boss.getWither().getLocation().add(0, 1, 0);
        Vector baseDirection = target.getLocation().add(0, 1, 0).toVector()
                .subtract(origin.toVector()).normalize();

        double totalSpread = Math.toRadians(30);
        int skullCount = 8;
        List<WitherSkull> skulls = new ArrayList<>();

        // Build orthogonal vectors for spreading
        Vector up = new Vector(0, 1, 0);
        Vector right = baseDirection.clone().crossProduct(up).normalize();
        if (right.lengthSquared() < 0.01) {
            right = baseDirection.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        }
        Vector actualUp = right.clone().crossProduct(baseDirection).normalize();

        for (int i = 0; i < skullCount; i++) {
            double fraction = (skullCount == 1) ? 0 : ((double) i / (skullCount - 1)) - 0.5;
            double yawOffset = fraction * totalSpread;
            double pitchOffset = (Math.random() - 0.5) * totalSpread * 0.3;

            Vector direction = baseDirection.clone()
                    .add(right.clone().multiply(Math.sin(yawOffset)))
                    .add(actualUp.clone().multiply(Math.sin(pitchOffset)))
                    .normalize();

            WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction.multiply(1.5));
            skull.setYield(0);
            skulls.add(skull);
            boss.getDisplayPool().track(skull);
        }

        // Particle trail task
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(128, 0, 255), 1.2f);
        BukkitTask trailTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            boolean anyAlive = false;
            for (WitherSkull skull : skulls) {
                if (skull.isValid() && !skull.isDead()) {
                    anyAlive = true;
                    skull.getWorld().spawnParticle(Particle.DUST, skull.getLocation(), 3,
                            0.1, 0.1, 0.1, 0, purpleDust);
                }
            }
            if (!anyAlive) {
                // All skulls are dead; task will be cleaned up next cleanup cycle
            }
        }, 1L, 2L);
        activeTasks.add(trailTask);

        // Auto-cancel trail after 5 seconds
        Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            trailTask.cancel();
            activeTasks.remove(trailTask);
        }, 100L);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
