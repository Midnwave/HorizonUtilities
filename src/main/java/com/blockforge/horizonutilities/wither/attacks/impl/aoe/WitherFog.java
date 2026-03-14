package com.blockforge.horizonutilities.wither.attacks.impl.aoe;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Creates a dense fog zone around the wither for 6 seconds.
 * Players within the zone receive Blindness and Wither II periodically.
 */
public class WitherFog implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "wither_fog";
    }

    @Override
    public String getDisplayName() {
        return "Wither Fog";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 18.0;
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
        double fogRadius = 20.0;
        int durationTicks = 120; // 6 seconds

        Location center = boss.getWither().getLocation();
        World world = center.getWorld();

        world.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 3.0f, 0.4f);

        // Repeating fog effect task (every 20 ticks = 1 second)
        BukkitTask fogTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            Location witherLoc = boss.getWither().getLocation();

            // Dense particle fog
            for (int i = 0; i < 80; i++) {
                double offsetX = (Math.random() - 0.5) * fogRadius * 2;
                double offsetY = Math.random() * 6;
                double offsetZ = (Math.random() - 0.5) * fogRadius * 2;

                if (offsetX * offsetX + offsetZ * offsetZ > fogRadius * fogRadius) continue;

                Location particleLoc = witherLoc.clone().add(offsetX, offsetY, offsetZ);
                world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, particleLoc, 1,
                        0.2, 0.1, 0.2, 0.01);
            }

            // Soul particles for ambient effect
            for (int i = 0; i < 20; i++) {
                double offsetX = (Math.random() - 0.5) * fogRadius * 2;
                double offsetY = Math.random() * 4;
                double offsetZ = (Math.random() - 0.5) * fogRadius * 2;

                if (offsetX * offsetX + offsetZ * offsetZ > fogRadius * fogRadius) continue;

                Location particleLoc = witherLoc.clone().add(offsetX, offsetY, offsetZ);
                world.spawnParticle(Particle.SOUL, particleLoc, 1,
                        0.1, 0.1, 0.1, 0.02);
            }

            // Apply debuffs to players in range
            for (Player player : boss.getPlayersInRange(fogRadius)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1, false, false, true));
            }

            // Ambient sound
            world.playSound(witherLoc, Sound.ENTITY_WITHER_AMBIENT, 1.5f, 0.3f);
        }, 0L, 20L);
        activeTasks.add(fogTask);

        // Cancel fog after duration
        BukkitTask cancelTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            fogTask.cancel();
            activeTasks.remove(fogTask);
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
