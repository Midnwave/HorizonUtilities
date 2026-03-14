package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plays a warning sound 1 second before firing. Fires a skull toward the target
 * with setYield(0). Uses a repeating task to teleport the skull forward along its
 * path, ignoring blocks (piercing through). Deals damage to any entity it passes
 * through. Red dust trail. Expires after 3 seconds.
 */
public class PiercingSkull implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "piercing_skull";
    }

    @Override
    public String getDisplayName() {
        return "Piercing Skull";
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
        return 1.5;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Player target = boss.getNearestPlayer(64);
        if (target == null) return;

        Location witherLoc = boss.getWither().getLocation();

        // Warning sound 1 second before firing
        witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);

        BukkitTask fireTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            if (boss.isDead() || !boss.getWither().isValid()) return;

            Player currentTarget = boss.getNearestPlayer(64);
            if (currentTarget == null) return;

            Location origin = boss.getWither().getLocation().add(0, 1, 0);
            Vector direction = currentTarget.getLocation().add(0, 1, 0).toVector()
                    .subtract(origin.toVector()).normalize();

            WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction.multiply(0.01));
            skull.setYield(0);
            boss.getDisplayPool().track(skull);

            double speed = 1.5; // blocks per tick
            Set<UUID> hitEntities = new HashSet<>();
            Particle.DustOptions redDust = new Particle.DustOptions(Color.RED, 1.5f);

            // Piercing movement task — teleport skull forward, ignoring blocks
            BukkitTask pierceTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                if (!skull.isValid() || skull.isDead()) return;

                Location current = skull.getLocation();
                Location next = current.clone().add(direction.clone().multiply(speed));

                // Teleport skull to next position (piercing through blocks)
                skull.teleport(next);
                skull.setVelocity(direction.clone().multiply(0.01)); // Keep skull alive

                // Red trail particles
                current.getWorld().spawnParticle(Particle.DUST, current, 3, 0.1, 0.1, 0.1, 0, redDust);

                // Check for nearby entities to damage
                double hitRadius = 1.5;
                for (Entity entity : skull.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                    if (entity instanceof Player player && !hitEntities.contains(player.getUniqueId())) {
                        hitEntities.add(player.getUniqueId());
                        player.damage(10.0, boss.getWither()); // 5 hearts
                        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0),
                                5, 0.3, 0.3, 0.3, 0);
                    }
                }
            }, 1L, 1L);
            activeTasks.add(pierceTask);

            // Expire after 3 seconds (60 ticks)
            BukkitTask expireTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                pierceTask.cancel();
                activeTasks.remove(pierceTask);
                if (skull.isValid() && !skull.isDead()) {
                    skull.remove();
                }
            }, 60L);
            activeTasks.add(expireTask);
        }, 20L); // 1 second delay after warning
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
