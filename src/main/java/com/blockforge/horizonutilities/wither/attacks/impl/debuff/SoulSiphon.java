package com.blockforge.horizonutilities.wither.attacks.impl.debuff;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Targets the highest-aggro player and drains XP levels, healing the wither.
 * Green orb particles fly from the player to the boss over 2 seconds.
 * Player loses 3-5 XP levels, wither heals for 50 HP.
 */
public class SoulSiphon implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "soul_siphon";
    }

    @Override
    public String getDisplayName() {
        return "Soul Siphon";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
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
        Player target = boss.getHighestAggroTarget(64);
        if (target == null) target = boss.getNearestPlayer(64);
        if (target == null) return;

        final Player finalTarget = target;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int xpDrain = rng.nextInt(3, 6); // 3-5 levels

        Particle.DustOptions greenDust = new Particle.DustOptions(Color.fromRGB(0, 200, 50), 1.2f);

        // Start sound (reversed pitch — high pitch simulates "reversed" feel)
        finalTarget.getWorld().playSound(finalTarget.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 0.3f);

        // Siphon beam over 2 seconds (40 ticks) — green dust beam from player to wither
        BukkitTask beamTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead() || !finalTarget.isOnline() || finalTarget.isDead()) return;

            Location playerLoc = finalTarget.getLocation().add(0, 1, 0);
            Location witherLoc = boss.getWither().getLocation().add(0, 1.5, 0);

            // Green dust beam from player to wither
            WitherParticles.beam(playerLoc, witherLoc, Particle.DUST, greenDust, 4.0);

            // Green orb particles flying from player toward wither
            finalTarget.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, playerLoc, 5,
                    0.3, 0.5, 0.3, 0.1);
        }, 0L, 4L);
        activeTasks.add(beamTask);

        // After 2 seconds, apply the drain and heal
        BukkitTask completeTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            beamTask.cancel();
            activeTasks.remove(beamTask);

            if (boss.isDead()) return;
            if (!finalTarget.isOnline() || finalTarget.isDead()) return;

            // Drain XP levels
            finalTarget.giveExpLevels(-xpDrain);

            // Sound at player
            finalTarget.getWorld().playSound(finalTarget.getLocation(),
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 2.0f);

            // Heal wither
            double newHealth = Math.min(boss.getMaxHealth(), boss.getWither().getHealth() + 50.0);
            boss.getWither().setHealth(newHealth);

            // Green healing particles at wither
            Location witherLoc = boss.getWither().getLocation().add(0, 1.5, 0);
            witherLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, witherLoc, 30,
                    1.0, 1.0, 1.0, 0.2);

            // Completion sound at wither
            witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
        }, 40L);
        activeTasks.add(completeTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
