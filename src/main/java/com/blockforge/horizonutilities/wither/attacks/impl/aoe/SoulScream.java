package com.blockforge.horizonutilities.wither.attacks.impl.aoe;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cone attack with a 1.5-second wind-up, firing a 90-degree cone of soul energy
 * toward the target player. Deals heavy damage and applies Nausea.
 */
public class SoulScream implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "soul_scream";
    }

    @Override
    public String getDisplayName() {
        return "Soul Scream";
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

        Particle.DustOptions soulDust = new Particle.DustOptions(Color.fromRGB(80, 200, 220), 1.0f);

        // Phase 1: Wind-up (30 ticks = 1.5 seconds) — particles gather toward mouth
        BukkitTask windupTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            Location mouthLoc = boss.getWither().getLocation().add(0, 2, 0);

            // Particles converging toward wither's mouth
            for (int i = 0; i < 8; i++) {
                double angle = Math.random() * Math.PI * 2;
                double dist = 3.0 + Math.random() * 3.0;
                double yOff = (Math.random() - 0.5) * 4;

                Location start = mouthLoc.clone().add(
                        Math.cos(angle) * dist,
                        yOff,
                        Math.sin(angle) * dist);

                Vector toMouth = mouthLoc.toVector().subtract(start.toVector()).normalize().multiply(0.3);
                mouthLoc.getWorld().spawnParticle(Particle.SOUL, start, 1,
                        toMouth.getX(), toMouth.getY(), toMouth.getZ(), 0.1);
            }

            mouthLoc.getWorld().playSound(mouthLoc, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 1.5f);
        }, 0L, 5L);
        activeTasks.add(windupTask);

        // Phase 2: Fire cone after wind-up
        BukkitTask fireTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            windupTask.cancel();
            activeTasks.remove(windupTask);

            Location mouthLoc = boss.getWither().getLocation().add(0, 2, 0);
            Location targetLoc = finalTarget.getLocation().add(0, 1, 0);

            Vector coneDirection = targetLoc.toVector().subtract(mouthLoc.toVector()).normalize();
            double coneHalfAngle = Math.toRadians(45); // 90 degree total cone
            double coneLength = 12.0;

            // Sound
            mouthLoc.getWorld().playSound(mouthLoc, Sound.ENTITY_WITHER_SHOOT, 2.5f, 0.4f);

            // Cone particle spray
            WitherParticles.cone(mouthLoc, coneDirection, coneHalfAngle, coneLength, 80,
                    Particle.SOUL_FIRE_FLAME, null);
            WitherParticles.cone(mouthLoc, coneDirection, coneHalfAngle, coneLength, 40,
                    Particle.DUST, soulDust);

            // Damage check: all players in cone
            for (Player player : boss.getPlayersInRange(coneLength + 2)) {
                Location playerLoc = player.getLocation().add(0, 1, 0);
                Vector toPlayer = playerLoc.toVector().subtract(mouthLoc.toVector());
                double distance = toPlayer.length();

                if (distance > coneLength) continue;
                if (distance < 0.1) {
                    // Basically on top of wither — still hit
                } else {
                    double angle = toPlayer.normalize().angle(coneDirection);
                    if (angle > coneHalfAngle) continue;
                }

                // 8 hearts = 16 HP
                player.damage(16.0, boss.getWither());
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, true));
            }
        }, 30L);
        activeTasks.add(fireTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
