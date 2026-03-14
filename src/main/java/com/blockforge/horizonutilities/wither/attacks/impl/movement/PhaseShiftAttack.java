package com.blockforge.horizonutilities.wither.attacks.impl.movement;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wither becomes invulnerable for 3 seconds with AI disabled. White glow
 * particles (END_ROD) orbit the wither. After 3 seconds, teleports to a
 * random position 15-25 blocks away (same Y level), restores AI, and
 * spawns a FLASH particle at the new location.
 */
public class PhaseShiftAttack implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "phase_shift";
    }

    @Override
    public String getDisplayName() {
        return "Phase Shift";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 20.0;
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
        World world = boss.getWither().getWorld();
        Location startLoc = boss.getWither().getLocation().clone();

        // Make wither invulnerable and disable AI
        boss.getWither().setAI(false);
        boss.getWither().setInvulnerable(true);

        // Phase shift sound
        world.playSound(startLoc, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.5f);

        // Orbiting white glow particles for 60 ticks (3 seconds)
        final int[] tick = {0};
        BukkitTask orbitTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead()) return;
            tick[0]++;

            Location witherLoc = boss.getWither().getLocation().add(0, 1, 0);

            // Two orbiting END_ROD particles at different phases
            double angle1 = (tick[0] * Math.PI / 10);
            double angle2 = angle1 + Math.PI;
            double orbitRadius = 2.0;

            Location orbit1 = witherLoc.clone().add(
                    Math.cos(angle1) * orbitRadius,
                    Math.sin(tick[0] * 0.15) * 0.5,
                    Math.sin(angle1) * orbitRadius
            );
            Location orbit2 = witherLoc.clone().add(
                    Math.cos(angle2) * orbitRadius,
                    Math.sin(tick[0] * 0.15 + Math.PI) * 0.5,
                    Math.sin(angle2) * orbitRadius
            );

            world.spawnParticle(Particle.END_ROD, orbit1, 2, 0.05, 0.05, 0.05, 0);
            world.spawnParticle(Particle.END_ROD, orbit2, 2, 0.05, 0.05, 0.05, 0);

            // Subtle glow sphere around the wither
            if (tick[0] % 5 == 0) {
                WitherParticles.sphere(witherLoc, 1.5, 12, Particle.END_ROD, null);
            }
        }, 0L, 1L);
        activeTasks.add(orbitTask);

        // After 3 seconds, teleport and restore
        BukkitTask resolveTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            orbitTask.cancel();
            activeTasks.remove(orbitTask);

            if (boss.isDead()) return;

            // Calculate random teleport destination 15-25 blocks away, same Y
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            double angle = rand.nextDouble(0, 2 * Math.PI);
            double distance = rand.nextDouble(15.0, 25.0);

            Location currentLoc = boss.getWither().getLocation();
            Location newLocation = currentLoc.clone().add(
                    Math.cos(angle) * distance,
                    0,
                    Math.sin(angle) * distance
            );
            newLocation.setYaw(currentLoc.getYaw());
            newLocation.setPitch(currentLoc.getPitch());

            // Teleport
            boss.getWither().teleport(newLocation);

            // Restore AI and vulnerability
            boss.getWither().setAI(true);
            boss.getWither().setInvulnerable(false);

            // Flash effect at new location
            world.spawnParticle(Particle.FLASH, newLocation.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.END_ROD, newLocation.clone().add(0, 1, 0), 30, 1.5, 1.5, 1.5, 0.1);
            world.playSound(newLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 1.5f);

            // Smoke poof at old location
            world.spawnParticle(Particle.SMOKE, currentLoc.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);

        }, 60L);
        activeTasks.add(resolveTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
