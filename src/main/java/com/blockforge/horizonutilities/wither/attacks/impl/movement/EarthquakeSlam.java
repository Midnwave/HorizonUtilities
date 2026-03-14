package com.blockforge.horizonutilities.wither.attacks.impl.movement;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wither slams to ground level over 15 ticks. On ground impact: 12-block radius
 * shockwave. All players within range launched upward (Y=1.5). 12 HP damage.
 * Crack block displays (CRACKED_STONE_BRICKS) scattered around impact point.
 * BLOCK particles with STONE data. Expanding ring particle effect.
 */
public class EarthquakeSlam implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "earthquake_slam";
    }

    @Override
    public String getDisplayName() {
        return "Earthquake Slam";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 14.0;
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
        World world = boss.getWither().getWorld();

        // Warning sound
        world.playSound(boss.getWither().getLocation(), Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.4f);

        final int[] tick = {0};
        final boolean[] impacted = {false};

        // Slam downward over 15 ticks
        BukkitTask slamTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead() || impacted[0]) return;
            tick[0]++;

            // Force downward velocity
            boss.getWither().setVelocity(new Vector(0, -2.5, 0));

            // Trail particles during descent
            Location witherLoc = boss.getWither().getLocation();
            world.spawnParticle(Particle.SMOKE, witherLoc, 5, 0.3, 0.1, 0.3, 0.02);

            // Check if wither has reached ground
            double groundY = world.getHighestBlockYAt(witherLoc) + 1;
            if (witherLoc.getY() <= groundY + 2) {
                impacted[0] = true;
                onSlamImpact(boss, witherLoc);
            }
        }, 0L, 1L);
        activeTasks.add(slamTask);

        // Force impact after 15 ticks
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            slamTask.cancel();
            activeTasks.remove(slamTask);

            if (!boss.isDead() && !impacted[0]) {
                impacted[0] = true;
                onSlamImpact(boss, boss.getWither().getLocation());
            }
        }, 15L);
        activeTasks.add(timeout);
    }

    private void onSlamImpact(WitherBossEntity boss, Location impact) {
        World world = impact.getWorld();
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // Snap to ground
        Location groundImpact = impact.clone();
        groundImpact.setY(world.getHighestBlockYAt(impact) + 1);

        // Explosion particles
        world.spawnParticle(Particle.EXPLOSION, groundImpact, 5, 1.5, 0.5, 1.5, 0);
        world.spawnParticle(Particle.BLOCK, groundImpact, 60, 4.0, 0.5, 4.0, 0.5,
                Material.STONE.createBlockData());
        world.spawnParticle(Particle.SMOKE, groundImpact, 40, 3.0, 0.5, 3.0, 0.08);
        world.playSound(groundImpact, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);

        // Expanding ring shockwave
        WitherParticles.expandingRing(groundImpact, 1.0, 12.0, 12, 2, Particle.DUST_PLUME, boss.getPlugin());

        // Scatter CRACKED_STONE_BRICKS block displays around impact
        for (int i = 0; i < 8; i++) {
            double offsetX = rand.nextDouble(-6.0, 6.0);
            double offsetZ = rand.nextDouble(-6.0, 6.0);
            Location crackLoc = groundImpact.clone().add(offsetX, 0.1, offsetZ);
            crackLoc.setY(world.getHighestBlockYAt(crackLoc) + 0.1);

            boss.getDisplayPool().spawnBlockDisplay(
                    crackLoc,
                    Material.CRACKED_STONE_BRICKS.createBlockData(),
                    60 // 3 second lifetime
            );
        }

        // 12-block radius, 12 HP damage, launch upward
        double radius = 12.0;
        double radiusSq = radius * radius;

        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(groundImpact) <= radiusSq) {
                player.damage(12.0, boss.getWither());

                // Launch upward
                Vector launch = new Vector(0, 1.5, 0);
                player.setVelocity(player.getVelocity().add(launch));
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
