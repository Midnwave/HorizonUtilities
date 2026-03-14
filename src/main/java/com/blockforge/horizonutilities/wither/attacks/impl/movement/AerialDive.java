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
 * Phase 1 (40 ticks): Wither flies upward (Y+1.5 each tick, capped 30 blocks above target).
 * A growing shadow circle appears on ground using DUST_PLUME ring.
 * Phase 2 (20 ticks): Dive-bomb straight down at target location.
 * On impact: 6-block AoE for 20 HP damage, EXPLOSION burst, knockback upward and outward.
 */
public class AerialDive implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "aerial_dive";
    }

    @Override
    public String getDisplayName() {
        return "Aerial Dive";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 16.0;
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
        Player target = boss.getNearestPlayer(64);
        if (target == null) return;

        World world = boss.getWither().getWorld();
        final Location targetGround = target.getLocation().clone();
        targetGround.setY(world.getHighestBlockYAt(targetGround) + 1);

        // Phase 1: Ascent — 40 ticks
        final double maxHeight = targetGround.getY() + 30;
        final int[] ascentTick = {0};

        BukkitTask ascentTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            ascentTick[0]++;

            Location witherLoc = boss.getWither().getLocation();

            // Fly upward, capped at 30 blocks above target
            if (witherLoc.getY() < maxHeight) {
                boss.getWither().setVelocity(new Vector(0, 1.5, 0));
            } else {
                boss.getWither().setVelocity(new Vector(0, 0, 0));
            }

            // Growing shadow circle on ground — radius increases with ascent
            double progress = (double) ascentTick[0] / 40;
            double radius = 1.0 + progress * 5.0;
            WitherParticles.ring(targetGround, radius, (int) (12 + progress * 20), Particle.DUST_PLUME, null);

            // Upward trail particles
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, witherLoc, 5, 0.3, 0.1, 0.3, 0.02);
        }, 0L, 1L);
        activeTasks.add(ascentTask);

        // Phase 2: Dive after 40 ticks
        BukkitTask diveStart = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            ascentTask.cancel();
            activeTasks.remove(ascentTask);

            if (boss.isDead()) return;

            // Snapshot target position for dive
            Player currentTarget = boss.getNearestPlayer(64);
            final Location diveTarget;
            if (currentTarget != null) {
                diveTarget = currentTarget.getLocation().clone();
                diveTarget.setY(world.getHighestBlockYAt(diveTarget) + 1);
            } else {
                diveTarget = targetGround;
            }

            // Warning sound
            world.playSound(boss.getWither().getLocation(), Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.5f);

            final int[] diveTick = {0};

            BukkitTask diveTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                if (boss.isDead()) return;
                diveTick[0]++;

                Location witherLoc = boss.getWither().getLocation();
                Vector toDiveTarget = diveTarget.toVector().subtract(witherLoc.toVector());

                if (toDiveTarget.lengthSquared() > 4.0) { // Still above target
                    Vector diveVelocity = toDiveTarget.normalize().multiply(3.0);
                    boss.getWither().setVelocity(diveVelocity);
                } else {
                    // Impact reached
                    onDiveImpact(boss, diveTarget);
                }

                // Dive trail particles
                world.spawnParticle(Particle.SMOKE, witherLoc, 5, 0.2, 0.2, 0.2, 0.05);
                world.spawnParticle(Particle.FLAME, witherLoc, 3, 0.1, 0.1, 0.1, 0.03);
            }, 0L, 1L);
            activeTasks.add(diveTask);

            // Force impact after 20 ticks if still diving
            BukkitTask diveTimeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                diveTask.cancel();
                activeTasks.remove(diveTask);
                if (!boss.isDead()) {
                    onDiveImpact(boss, diveTarget);
                }
            }, 20L);
            activeTasks.add(diveTimeout);

        }, 40L);
        activeTasks.add(diveStart);
    }

    private void onDiveImpact(WitherBossEntity boss, Location impact) {
        World world = impact.getWorld();

        // Explosion burst
        world.spawnParticle(Particle.EXPLOSION, impact, 5, 1.0, 0.5, 1.0, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, impact, 40, 2.0, 0.3, 2.0, 0.08);
        world.spawnParticle(Particle.SMOKE, impact, 30, 2.0, 0.5, 2.0, 0.05);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);

        // Expanding ring shockwave
        WitherParticles.expandingRing(impact, 1.0, 6.0, 8, 2, Particle.DUST_PLUME, boss.getPlugin());

        // 6-block AoE, 20 HP damage, knockback upward and outward
        double radius = 6.0;
        double radiusSq = radius * radius;

        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(impact) <= radiusSq) {
                player.damage(20.0, boss.getWither());

                // Knockback upward and outward
                Vector knockback = player.getLocation().toVector().subtract(impact.toVector());
                if (knockback.lengthSquared() < 0.01) {
                    knockback = new Vector(0, 1.5, 0);
                } else {
                    knockback.normalize().multiply(1.2);
                    knockback.setY(1.5);
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
