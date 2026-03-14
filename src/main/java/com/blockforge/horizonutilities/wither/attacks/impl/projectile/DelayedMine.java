package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fires a skull at the target's position. When it impacts, spawns a glowing item
 * display (magma cream) at the impact location. After 3 seconds, creates an explosion
 * effect (particles + damage within 4 blocks). Pulsing redstone particles while active.
 */
public class DelayedMine implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "delayed_mine";
    }

    @Override
    public String getDisplayName() {
        return "Delayed Mine";
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
        Vector direction = target.getLocation().add(0, 1, 0).toVector()
                .subtract(origin.toVector()).normalize().multiply(1.5);

        WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction);
        skull.setYield(0);
        boss.getDisplayPool().track(skull);

        // Track skull for impact
        BukkitTask trackTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) {
                placeMine(boss, skull.getLocation());
            }
        }, 1L, 2L);
        activeTasks.add(trackTask);

        // Auto-cancel tracking when skull dies
        BukkitTask[] deathCheckHolder = new BukkitTask[1];
        deathCheckHolder[0] = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) {
                trackTask.cancel();
                deathCheckHolder[0].cancel();
                activeTasks.remove(trackTask);
            }
        }, 1L, 2L);
        BukkitTask deathCheck = deathCheckHolder[0];
        activeTasks.add(deathCheck);

        // Timeout after 5 seconds
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            trackTask.cancel();
            deathCheck.cancel();
            activeTasks.remove(trackTask);
            activeTasks.remove(deathCheck);
            if (skull.isValid() && !skull.isDead()) {
                Location impactLoc = skull.getLocation();
                skull.remove();
                placeMine(boss, impactLoc);
            }
        }, 100L);
        activeTasks.add(timeout);
    }

    private void placeMine(WitherBossEntity boss, Location impact) {
        // Snap to ground level
        Location mineLoc = impact.clone();
        mineLoc.setY(impact.getWorld().getHighestBlockYAt(impact) + 1);

        // Spawn glowing magma cream item display
        boss.getDisplayPool().spawnItemDisplay(mineLoc, new ItemStack(Material.MAGMA_CREAM), 80);

        // Pulsing redstone particles while mine is active
        Particle.DustOptions redDust = new Particle.DustOptions(Color.RED, 1.0f);
        BukkitTask pulseTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            mineLoc.getWorld().spawnParticle(Particle.DUST, mineLoc, 5, 0.3, 0.3, 0.3, 0, redDust);
        }, 1L, 5L);
        activeTasks.add(pulseTask);

        // Explode after 3 seconds (60 ticks)
        BukkitTask detonateTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            pulseTask.cancel();
            activeTasks.remove(pulseTask);

            // Explosion particles
            mineLoc.getWorld().spawnParticle(Particle.EXPLOSION, mineLoc, 3, 0.5, 0.5, 0.5, 0);
            mineLoc.getWorld().spawnParticle(Particle.FLAME, mineLoc, 40, 1.5, 0.5, 1.5, 0.1);
            mineLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, mineLoc, 20, 1.0, 0.3, 1.0, 0.05);

            // Damage players within 4 blocks
            double radius = 4.0;
            double radiusSq = radius * radius;
            double damage = 12.0; // 6 hearts

            for (Player player : boss.getPlayersInRange(64)) {
                if (player.getLocation().distanceSquared(mineLoc) <= radiusSq) {
                    player.damage(damage, boss.getWither());
                }
            }
        }, 60L);
        activeTasks.add(detonateTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
