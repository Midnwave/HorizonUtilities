package com.blockforge.horizonutilities.wither.attacks.impl.movement;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wither vanishes with a teleport sound and portal burst, then reappears 1 second
 * later behind the target player (3 blocks behind their look direction). On reappear,
 * fires a single WitherSkull point-blank at the player with purple explosion particles.
 */
public class TeleportStrike implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "teleport_strike";
    }

    @Override
    public String getDisplayName() {
        return "Teleport Strike";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 12.0;
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
        World world = boss.getWither().getWorld();
        Location oldLocation = boss.getWither().getLocation().clone();

        // Vanish effects at old location
        world.playSound(oldLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.8f);
        world.spawnParticle(Particle.PORTAL, oldLocation, 40, 0.5, 1.0, 0.5, 0.5);
        world.spawnParticle(Particle.SMOKE, oldLocation, 20, 0.3, 0.5, 0.3, 0.05);

        // Teleport wither far away temporarily (hidden)
        Location hiddenLoc = oldLocation.clone().add(0, 300, 0);
        boss.getWither().teleport(hiddenLoc);

        // Reappear after 1 second (20 ticks)
        BukkitTask reappearTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            if (boss.isDead()) return;

            // Calculate position 3 blocks behind the target's look direction
            Vector lookDir = finalTarget.getLocation().getDirection().normalize();
            Location behindTarget = finalTarget.getLocation().clone()
                    .subtract(lookDir.multiply(3.0));
            behindTarget.setY(finalTarget.getLocation().getY());

            // Teleport wither behind target
            boss.getWither().teleport(behindTarget);

            // Reappear effects
            world.playSound(behindTarget, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 1.2f);
            world.spawnParticle(Particle.PORTAL, behindTarget, 30, 0.5, 1.0, 0.5, 0.3);

            // Fire point-blank skull at target
            Vector toTarget = finalTarget.getLocation().add(0, 1, 0).toVector()
                    .subtract(behindTarget.clone().add(0, 1, 0).toVector()).normalize();

            WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, toTarget.multiply(1.5));
            skull.setYield(0);
            boss.getDisplayPool().track(skull);

            // Purple explosion particles at reappear location
            Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(160, 0, 255), 1.5f);
            WitherParticles.sphere(behindTarget.add(0, 1, 0), 1.5, 20, Particle.DUST, purpleDust);

        }, 20L);
        activeTasks.add(reappearTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
