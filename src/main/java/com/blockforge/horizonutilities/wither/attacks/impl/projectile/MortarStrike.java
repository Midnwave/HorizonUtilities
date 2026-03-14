package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fires a single WitherSkull in a high arc toward the target. Before impact,
 * a target circle appears on the ground as a warning. 4-block AoE explosion
 * on impact dealing 10 HP damage.
 */
public class MortarStrike implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "mortar_strike";
    }

    @Override
    public String getDisplayName() {
        return "Mortar Strike";
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
        Location targetLoc = target.getLocation().clone();
        World world = origin.getWorld();

        // Calculate high arc direction — add significant upward component
        Vector toTarget = targetLoc.toVector().subtract(origin.toVector());
        double horizontalDist = Math.sqrt(toTarget.getX() * toTarget.getX() + toTarget.getZ() * toTarget.getZ());
        Vector arcDirection = toTarget.clone().normalize();
        arcDirection.setY(Math.max(1.0, horizontalDist * 0.4)); // High arc
        arcDirection.normalize().multiply(1.2);

        WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, arcDirection);
        skull.setYield(0);
        boss.getDisplayPool().track(skull);

        // Spawn ground target circle at target's position
        Location groundTarget = targetLoc.clone();
        groundTarget.setY(world.getHighestBlockYAt(targetLoc) + 0.1);

        // Block display warning ring
        boss.getDisplayPool().spawnBlockDisplay(
                groundTarget.clone().add(-0.5, 0, -0.5),
                Material.RED_CONCRETE.createBlockData(),
                60 // 3 second lifetime
        );

        // Pulsing target ring particles
        BukkitTask warningTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            WitherParticles.ring(groundTarget, 4.0, 24, Particle.DUST_PLUME, null);
            WitherParticles.ring(groundTarget, 2.0, 16, Particle.DUST_PLUME, null);
        }, 0L, 5L);
        activeTasks.add(warningTask);

        // Track skull for impact
        BukkitTask trackTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) {
                onImpact(boss, skull.getLocation());
                warningTask.cancel();
                activeTasks.remove(warningTask);
            }
        }, 1L, 2L);
        activeTasks.add(trackTask);

        // Auto-cancel on skull death
        BukkitTask deathCheck = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) {
                trackTask.cancel();
                activeTasks.remove(trackTask);
            }
        }, 1L, 2L);
        activeTasks.add(deathCheck);

        // Timeout after 5 seconds
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            trackTask.cancel();
            deathCheck.cancel();
            warningTask.cancel();
            activeTasks.remove(trackTask);
            activeTasks.remove(deathCheck);
            activeTasks.remove(warningTask);
            if (skull.isValid() && !skull.isDead()) {
                Location impactLoc = skull.getLocation();
                skull.remove();
                onImpact(boss, impactLoc);
            }
        }, 100L);
        activeTasks.add(timeout);
    }

    private void onImpact(WitherBossEntity boss, Location impact) {
        World world = impact.getWorld();

        Location groundImpact = impact.clone();
        groundImpact.setY(world.getHighestBlockYAt(impact) + 1);

        // Explosion particles
        world.spawnParticle(Particle.EXPLOSION, groundImpact, 3, 0.5, 0.3, 0.5, 0);
        world.spawnParticle(Particle.FLAME, groundImpact, 30, 1.5, 0.3, 1.5, 0.08);
        WitherParticles.ring(groundImpact, 4.0, 24, Particle.DUST_PLUME, null);

        // Damage players within 4 blocks
        double radius = 4.0;
        double radiusSq = radius * radius;

        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(groundImpact) <= radiusSq) {
                player.damage(10.0, boss.getWither());
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
