package com.blockforge.horizonutilities.wither.attacks.impl.aoe;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Marks each player's position with a dark circle, then erupts soul fire pillars
 * at those positions after a delay.
 */
public class ShadowEruption implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "shadow_eruption";
    }

    @Override
    public String getDisplayName() {
        return "Shadow Eruption";
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
        return 2.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        List<Player> targets = boss.getPlayersInRange(30);
        if (targets.isEmpty()) return;

        World world = boss.getWither().getWorld();
        List<Location> eruptionPoints = new ArrayList<>();

        // Phase 1: Mark each player's position with warning indicators
        for (Player player : targets) {
            Location footLoc = player.getLocation().clone();
            // Snap to ground
            footLoc.setY(world.getHighestBlockYAt(footLoc) + 1);
            eruptionPoints.add(footLoc);

            // Spawn dark circle block display as warning
            boss.getDisplayPool().spawnBlockDisplay(
                    footLoc.clone().add(-0.5, 0, -0.5),
                    Material.BLACK_CONCRETE.createBlockData(),
                    20 // 1 second lifetime
            );

            // Warning particles — dark circle on ground
            for (int i = 0; i < 16; i++) {
                double angle = (Math.PI * 2 / 16) * i;
                double radius = 2.5;
                Location particleLoc = footLoc.clone().add(
                        Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
                world.spawnParticle(Particle.SMOKE, particleLoc, 2, 0.1, 0.05, 0.1, 0.01);
            }
        }

        // Warning sound
        world.playSound(boss.getWither().getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2.0f, 0.5f);

        // Phase 2: Eruption after 1 second (20 ticks)
        BukkitTask eruptionTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            for (Location point : eruptionPoints) {
                // Vertical pillar of soul fire flame particles (y=0 to y=5)
                for (double y = 0; y <= 5; y += 0.3) {
                    Location pillarLoc = point.clone().add(0, y, 0);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, pillarLoc, 3,
                            0.3, 0.1, 0.3, 0.02);
                }

                // Sound per eruption point
                world.playSound(point, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.7f);

                // Damage players within 3 blocks of each eruption point
                for (Player player : boss.getPlayersInRange(35)) {
                    if (player.getLocation().distanceSquared(point) <= 9.0) { // 3^2
                        // 8 hearts = 16 HP
                        player.damage(16.0, boss.getWither());
                    }
                }
            }
        }, 20L);
        activeTasks.add(eruptionTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
