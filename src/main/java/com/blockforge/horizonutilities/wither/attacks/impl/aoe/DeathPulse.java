package com.blockforge.horizonutilities.wither.attacks.impl.aoe;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Expanding purple particle ring at ground level that knocks players upward.
 * Players can jump over the ring to avoid damage.
 */
public class DeathPulse implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "death_pulse";
    }

    @Override
    public String getDisplayName() {
        return "Death Pulse";
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
        Location center = boss.getWither().getLocation().clone();
        // Set to ground level (1.5 blocks above the wither's Y)
        center.setY(center.getY() + 1.5);

        boss.getWither().getWorld().playSound(center, Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.5f);

        final double maxRadius = 12.0;
        final int durationTicks = 20;
        final double radiusPerTick = maxRadius / durationTicks;
        final double hitWidth = 1.5;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= durationTicks || boss.isDead()) {
                    // Cancel handled by reference below
                    return;
                }

                double currentRadius = tick * radiusPerTick;

                // Spawn purple particle ring
                WitherParticles.ring(center, currentRadius, (int) (currentRadius * 4) + 8,
                        Particle.DRAGON_BREATH, null);

                // Check players
                for (Player player : boss.getPlayersInRange(maxRadius + 5)) {
                    double playerDist = player.getLocation().distance(center);
                    double verticalDist = Math.abs(player.getLocation().getY() - center.getY());

                    // Player is near the ring radius and at ground level (can jump over)
                    if (Math.abs(playerDist - currentRadius) <= hitWidth && verticalDist <= 2.0) {
                        // Damage: 6 hearts = 12 HP
                        player.damage(12.0, boss.getWither());

                        // Knockback upward and away
                        Vector knockback = player.getLocation().toVector()
                                .subtract(center.toVector()).normalize()
                                .multiply(1.2).setY(0.8);
                        player.setVelocity(knockback);
                    }
                }

                tick++;
            }
        }, 0L, 1L);
        activeTasks.add(task);

        // Auto-cancel after duration + buffer
        Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            task.cancel();
            activeTasks.remove(task);
        }, durationTicks + 5L);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
