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
 * Fires a dark purple skull. On hit: deals damage AND heals the Wither for 100%
 * of the damage dealt. Green healing particles fly from impact to the Wither location.
 */
public class SoulDrainSkull implements WitherAttack {

    private static final double DAMAGE = 10.0; // 5 hearts

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "soul_drain_skull";
    }

    @Override
    public String getDisplayName() {
        return "Soul Drain";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 9.0;
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
                .subtract(origin.toVector()).normalize().multiply(1.2);

        WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction);
        skull.setYield(0);
        boss.getDisplayPool().track(skull);

        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.3f);

        // Track skull for impact and trail
        BukkitTask trackTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (!skull.isValid() || skull.isDead()) {
                onImpact(boss, skull.getLocation());
                return;
            }
            // Dark purple trail
            skull.getWorld().spawnParticle(Particle.DUST, skull.getLocation(), 4, 0.1, 0.1, 0.1, 0, purpleDust);
            skull.getWorld().spawnParticle(Particle.SOUL, skull.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
        }, 1L, 2L);
        activeTasks.add(trackTask);

        // Auto-cancel when skull dies
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
                skull.remove();
            }
        }, 100L);
        activeTasks.add(timeout);
    }

    private void onImpact(WitherBossEntity boss, Location impact) {
        // Damage nearby players at impact point
        double radius = 3.0;
        double radiusSq = radius * radius;
        double totalDamageDealt = 0;

        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(impact) <= radiusSq) {
                double healthBefore = player.getHealth();
                player.damage(DAMAGE, boss.getWither());
                double actualDamage = healthBefore - player.getHealth();
                if (actualDamage > 0) {
                    totalDamageDealt += actualDamage;
                }
            }
        }

        // Heal the Wither for damage dealt
        if (totalDamageDealt > 0 && boss.getWither().isValid()) {
            double newHealth = Math.min(
                    boss.getWither().getHealth() + totalDamageDealt,
                    boss.getMaxHealth()
            );
            boss.getWither().setHealth(newHealth);
        }

        // Green healing particles fly from impact to Wither
        if (boss.getWither().isValid()) {
            Location witherLoc = boss.getWither().getLocation().add(0, 1, 0);
            Particle.DustOptions greenDust = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.0f);

            // Animate healing beam over several ticks
            BukkitTask healBeam = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= 10 || !boss.getWither().isValid()) return;
                    Location currentWither = boss.getWither().getLocation().add(0, 1, 0);
                    WitherParticles.beam(impact, currentWither, Particle.DUST, greenDust, 3.0);
                    ticks++;
                }
            }, 1L, 2L);
            activeTasks.add(healBeam);

            // Cancel heal beam after 1 second
            Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                healBeam.cancel();
                activeTasks.remove(healBeam);
            }, 20L);
        }

        // Impact particles
        impact.getWorld().spawnParticle(Particle.SOUL, impact, 15, 0.5, 0.5, 0.5, 0.05);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
