package com.blockforge.horizonutilities.wither.attacks.impl.phase;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * The arena goes dark for 8 seconds: Darkness + Blindness applied to all nearby players,
 * the Wither turns invisible, lightning strikes at random player positions, and eerie
 * ambient cave sounds play. Ends with a flash of white particles on reveal.
 */
public class DeathsEmbrace implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "deaths_embrace";
    }

    @Override
    public String getDisplayName() {
        return "Death's Embrace";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 45.0;
    }

    @Override
    public double getWeight(int phase) {
        return 0.8;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        World world = boss.getWither().getWorld();
        int durationTicks = 160; // 8 seconds

        // Make the wither invisible
        boss.getWither().setInvisible(true);

        // Initial sound
        world.playSound(boss.getWither().getLocation(), Sound.ENTITY_WITHER_AMBIENT, 3.0f, 0.3f);

        // Apply Darkness + Blindness to all nearby players and refresh every 40 ticks
        BukkitTask debuffTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            for (Player player : boss.getPlayersInRange(30)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, true));
            }
        }, 0L, 40L);
        activeTasks.add(debuffTask);

        // Lightning strikes at random player positions every 40 ticks
        BukkitTask lightningTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            List<Player> targets = boss.getPlayersInRange(30);
            if (targets.isEmpty()) return;

            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Player target = targets.get(rng.nextInt(targets.size()));
            Location strikeLoc = target.getLocation().clone();

            // Effect-only lightning — no fire, no damage
            world.strikeLightningEffect(strikeLoc);
        }, 40L, 40L);
        activeTasks.add(lightningTask);

        // Eerie ambient cave sound every 2 seconds (40 ticks)
        BukkitTask ambientTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            Location witherLoc = boss.getWither().getLocation();
            world.playSound(witherLoc, Sound.AMBIENT_CAVE, 2.0f, 0.5f);

            // Atmospheric soul particles floating around the wither
            world.spawnParticle(Particle.SOUL, witherLoc.clone().add(0, 1, 0), 8,
                    4.0, 2.0, 4.0, 0.02);
        }, 0L, 40L);
        activeTasks.add(ambientTask);

        // After duration: remove effects, make wither visible, flash of white particles
        BukkitTask finishTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            // Cancel ongoing tasks
            debuffTask.cancel();
            activeTasks.remove(debuffTask);
            lightningTask.cancel();
            activeTasks.remove(lightningTask);
            ambientTask.cancel();
            activeTasks.remove(ambientTask);

            if (boss.isDead()) return;

            // Make wither visible again
            boss.getWither().setInvisible(false);

            // Remove debuffs from nearby players
            for (Player player : boss.getPlayersInRange(30)) {
                player.removePotionEffect(PotionEffectType.DARKNESS);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }

            // Flash of white particles on reveal
            Location revealLoc = boss.getWither().getLocation().add(0, 1, 0);
            WitherParticles.sphere(revealLoc, 4.0, 80, Particle.END_ROD, null);
            WitherParticles.explosion(revealLoc, 40, Particle.FLASH);

            // Reveal sound
            world.playSound(revealLoc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 1.2f);
        }, durationTicks);
        activeTasks.add(finishTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
