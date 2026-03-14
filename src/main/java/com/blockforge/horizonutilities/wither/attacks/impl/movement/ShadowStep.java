package com.blockforge.horizonutilities.wither.attacks.impl.movement;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 3-4 rapid teleport blinks (every 10 ticks). Each blink teleports the wither
 * 5-8 blocks toward a random nearby player. Leaves a dark afterimage
 * (ItemDisplay of WITHER_SKELETON_SKULL, 40 tick lifetime) at the old location.
 * Each afterimage deals 6 HP damage to players within 2 blocks when it expires.
 * SMOKE burst at each teleport.
 */
public class ShadowStep implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "shadow_step";
    }

    @Override
    public String getDisplayName() {
        return "Shadow Step";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
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
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int blinkCount = rand.nextInt(3, 5); // 3 or 4 blinks

        for (int i = 0; i < blinkCount; i++) {
            long delay = (long) i * 10; // Every 10 ticks

            BukkitTask blinkTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                if (boss.isDead()) return;

                World world = boss.getWither().getWorld();
                Location oldLoc = boss.getWither().getLocation().clone();

                // Pick a random nearby player to blink toward
                List<Player> nearby = boss.getPlayersInRange(40);
                if (nearby.isEmpty()) return;
                Player blinkTarget = nearby.get(rand.nextInt(nearby.size()));

                // Calculate blink destination: 5-8 blocks toward the target
                double blinkDist = rand.nextDouble(5.0, 8.0);
                Vector toTarget = blinkTarget.getLocation().toVector()
                        .subtract(oldLoc.toVector());

                Location newLoc;
                if (toTarget.lengthSquared() <= blinkDist * blinkDist) {
                    // Already close — blink to a position near the target
                    newLoc = blinkTarget.getLocation().clone().add(
                            rand.nextDouble(-2, 2), 0, rand.nextDouble(-2, 2));
                } else {
                    newLoc = oldLoc.clone().add(toTarget.normalize().multiply(blinkDist));
                }
                newLoc.setYaw(oldLoc.getYaw());
                newLoc.setPitch(oldLoc.getPitch());

                // Teleport wither
                boss.getWither().teleport(newLoc);

                // Smoke burst at both locations
                world.spawnParticle(Particle.SMOKE, oldLoc.add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.05);
                world.spawnParticle(Particle.SMOKE, newLoc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.03);
                world.playSound(oldLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.6f);

                // Spawn dark afterimage at old location
                ItemDisplay afterimage = boss.getDisplayPool().spawnItemDisplay(
                        oldLoc,
                        new ItemStack(Material.WITHER_SKELETON_SKULL),
                        40 // 2 second lifetime
                );

                // Afterimage ambient particles
                BukkitTask afterimageParticles = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                    if (!afterimage.isValid()) return;
                    world.spawnParticle(Particle.SMOKE, afterimage.getLocation(), 2,
                            0.2, 0.3, 0.2, 0.01);
                }, 1L, 5L);
                activeTasks.add(afterimageParticles);

                // Delayed damage when afterimage expires — after 40 ticks
                BukkitTask damageTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                    afterimageParticles.cancel();
                    activeTasks.remove(afterimageParticles);

                    Location afterimageLoc = oldLoc;

                    // Damage players within 2 blocks
                    double radiusSq = 4.0; // 2^2
                    for (Player player : boss.getPlayersInRange(64)) {
                        if (player.getLocation().distanceSquared(afterimageLoc) <= radiusSq) {
                            player.damage(6.0, boss.getWither());
                        }
                    }

                    // Small dark burst on detonation
                    world.spawnParticle(Particle.SMOKE, afterimageLoc.clone().add(0, 0.5, 0),
                            15, 0.5, 0.5, 0.5, 0.03);
                    world.playSound(afterimageLoc, Sound.ENTITY_WITHER_HURT, 0.8f, 1.5f);
                }, 40L);
                activeTasks.add(damageTask);

            }, delay);
            activeTasks.add(blinkTask);
        }
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
