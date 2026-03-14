package com.blockforge.horizonutilities.wither.attacks.impl.aoe;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
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
 * 4 geysers at player positions. Each has a 2-tick particle warning (SOUL_FIRE_FLAME),
 * then erupts as an 8-block column of soul fire particles.
 * Deals 20 damage (10 hearts) and launches players upward (velocity Y=1.0).
 * Geysers are staggered by 5 ticks each.
 */
public class WitheringGeyser implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "withering_geyser";
    }

    @Override
    public String getDisplayName() {
        return "Withering Geyser";
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
        return 3.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        List<Player> targets = boss.getPlayersInRange(32);
        if (targets.isEmpty()) return;

        World world = boss.getWither().getWorld();

        // Pick up to 4 players
        int geyserCount = Math.min(4, targets.size());

        // Capture geyser locations at casting time
        List<Location> geyserLocations = new ArrayList<>();
        for (int i = 0; i < geyserCount; i++) {
            Location loc = targets.get(i).getLocation().clone();
            loc.setY(world.getHighestBlockYAt(loc) + 1);
            geyserLocations.add(loc);
        }

        // Initial casting sound
        world.playSound(boss.getWither().getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.5f);

        // Stagger each geyser by 5 ticks
        for (int i = 0; i < geyserLocations.size(); i++) {
            final Location geyserLoc = geyserLocations.get(i);
            long staggerDelay = (long) i * 5L;

            // Warning phase: 2-tick SOUL_FIRE_FLAME warning
            BukkitTask warningTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                if (boss.isDead()) return;

                // Warning particles — ground circle
                for (int j = 0; j < 12; j++) {
                    double angle = (2 * Math.PI / 12) * j;
                    double radius = 1.5;
                    Location particleLoc = geyserLoc.clone().add(
                            Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 2,
                            0.1, 0.05, 0.1, 0.01);
                }

                // Warning sound
                world.playSound(geyserLoc, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.5f);
            }, staggerDelay);
            activeTasks.add(warningTask);

            // Eruption phase: 2 ticks after warning
            BukkitTask eruptionTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                if (boss.isDead()) return;

                // 8-block column of soul fire particles
                for (double y = 0; y <= 8; y += 0.4) {
                    Location pillarLoc = geyserLoc.clone().add(0, y, 0);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, pillarLoc, 4,
                            0.3, 0.1, 0.3, 0.02);
                }

                // Eruption sound
                world.playSound(geyserLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);

                // Damage and launch players within 2-block radius
                double radiusSq = 4.0; // 2^2
                for (Player player : boss.getPlayersInRange(35)) {
                    if (player.getLocation().distanceSquared(geyserLoc) <= radiusSq) {
                        // 20 damage (10 hearts)
                        player.damage(20.0, boss.getWither());

                        // Launch upward
                        Vector velocity = player.getVelocity();
                        velocity.setY(1.0);
                        player.setVelocity(velocity);
                    }
                }
            }, staggerDelay + 2L);
            activeTasks.add(eruptionTask);
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
