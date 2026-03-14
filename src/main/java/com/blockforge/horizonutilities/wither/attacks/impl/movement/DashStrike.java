package com.blockforge.horizonutilities.wither.attacks.impl.movement;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The wither telegraphs with a red beam, then charges at the target player.
 * Contact during the dash deals massive damage. Leaves a soul fire trail.
 */
public class DashStrike implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "dash_strike";
    }

    @Override
    public String getDisplayName() {
        return "Dash Strike";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 10.0;
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
        Player target = boss.getHighestAggroTarget(64);
        if (target == null) target = boss.getNearestPlayer(64);
        if (target == null) return;

        final Player finalTarget = target;
        Location witherLoc = boss.getWither().getLocation().add(0, 1, 0);
        Location targetLoc = finalTarget.getLocation().add(0, 1, 0);

        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.5f);

        // Phase 1: Telegraph — red beam from wither to target for 1.5 seconds
        BukkitTask telegraphTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            Location from = boss.getWither().getLocation().add(0, 1, 0);
            Location to = finalTarget.getLocation().add(0, 1, 0);
            WitherParticles.beam(from, to, Particle.DUST, redDust, 3.0);
            boss.getWither().getWorld().playSound(from, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }, 0L, 5L);
        activeTasks.add(telegraphTask);

        // Phase 2: Dash after 30 ticks
        BukkitTask dashStart = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            telegraphTask.cancel();
            activeTasks.remove(telegraphTask);

            Location dashOrigin = boss.getWither().getLocation();
            Location dashTarget = finalTarget.getLocation();

            boss.getWither().getWorld().playSound(dashOrigin, Sound.ENTITY_WITHER_SHOOT, 2.0f, 1.2f);

            // Track already-hit players to prevent multi-hit
            Set<UUID> hitPlayers = new HashSet<>();

            // Repeating dash task for 20 ticks
            BukkitTask dashTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                Location currentLoc = boss.getWither().getLocation();
                Location currentTarget = finalTarget.isOnline() && !finalTarget.isDead()
                        ? finalTarget.getLocation() : dashTarget;

                Vector direction = currentTarget.toVector()
                        .subtract(currentLoc.toVector());

                if (direction.lengthSquared() > 1.0) {
                    direction.normalize().multiply(2.0); // Charge speed
                    boss.getWither().setVelocity(direction);
                }

                // Soul fire trail
                boss.getWither().getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        currentLoc, 5, 0.3, 0.3, 0.3, 0.02);

                // Contact damage check — 3 block radius
                for (Player player : boss.getPlayersInRange(5)) {
                    if (hitPlayers.contains(player.getUniqueId())) continue;
                    if (player.getLocation().distanceSquared(currentLoc) <= 9.0) { // 3^2
                        // 15 hearts = 30 HP
                        player.damage(30.0, boss.getWither());
                        hitPlayers.add(player.getUniqueId());
                    }
                }
            }, 0L, 1L);
            activeTasks.add(dashTask);

            // Cancel dash after 20 ticks
            BukkitTask cancelDash = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                dashTask.cancel();
                activeTasks.remove(dashTask);
            }, 20L);
            activeTasks.add(cancelDash);
        }, 30L);
        activeTasks.add(dashStart);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
