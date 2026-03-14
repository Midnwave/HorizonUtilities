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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Anti-healing zone that applies Wither and strips regeneration/absorption
 * from players within a 15-block radius for 8 seconds.
 */
public class NecroticAura implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "necrotic_aura";
    }

    @Override
    public String getDisplayName() {
        return "Necrotic Aura";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 22.0;
    }

    @Override
    public double getWeight(int phase) {
        return 1.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        double auraRadius = 15.0;
        int durationTicks = 160; // 8 seconds

        Location center = boss.getWither().getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_HURT, 2.0f, 0.3f);

        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(100, 0, 160), 1.5f);

        // Repeating aura task every 20 ticks
        BukkitTask auraTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            Location witherLoc = boss.getWither().getLocation();

            // Purple particle ring at zone boundary
            WitherParticles.ring(witherLoc.clone().add(0, 0.5, 0), auraRadius, 48,
                    Particle.DUST, purpleDust);

            // Ambient particles inside the zone
            for (int i = 0; i < 15; i++) {
                double angle = Math.random() * Math.PI * 2;
                double dist = Math.random() * auraRadius;
                double x = witherLoc.getX() + Math.cos(angle) * dist;
                double z = witherLoc.getZ() + Math.sin(angle) * dist;
                Location particleLoc = new Location(witherLoc.getWorld(), x,
                        witherLoc.getY() + Math.random() * 3, z);
                witherLoc.getWorld().spawnParticle(Particle.SOUL, particleLoc, 1,
                        0.1, 0.1, 0.1, 0.01);
            }

            // Apply anti-healing effects to players in range
            for (Player player : boss.getPlayersInRange(auraRadius)) {
                // Apply Wither I
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 30, 0, false, false, true));

                // Strip regeneration and absorption
                player.removePotionEffect(PotionEffectType.REGENERATION);
                player.removePotionEffect(PotionEffectType.ABSORPTION);
            }
        }, 0L, 20L);
        activeTasks.add(auraTask);

        // Cancel aura after duration
        BukkitTask cancelTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            auraTask.cancel();
            activeTasks.remove(auraTask);
        }, durationTicks);
        activeTasks.add(cancelTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
