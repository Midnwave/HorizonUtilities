package com.blockforge.horizonutilities.wither.attacks.impl.terrain;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A line of sequential explosions (no block damage) toward a target player, 20 blocks long.
 * Every 3 ticks, the fissure advances 1 block with crack displays, explosion particles,
 * and fire rising from the crack. Players within 2 blocks take 8 HP damage.
 */
public class GroundFissure implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "ground_fissure";
    }

    @Override
    public String getDisplayName() {
        return "Ground Fissure";
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
        Player target = boss.getHighestAggroTarget(64);
        if (target == null) target = boss.getNearestPlayer(64);
        if (target == null) return;

        Location witherLoc = boss.getWither().getLocation().clone();
        Location targetLoc = target.getLocation().clone();

        // Calculate direction from wither to target along the ground
        Vector direction = targetLoc.toVector().subtract(witherLoc.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 0.01) {
            direction = new Vector(1, 0, 0); // Fallback direction
        }
        direction.normalize();

        final int fissureLength = 20;
        final Vector fissureDir = direction.clone();

        // Starting sound
        witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);

        // Sequential fissure advancement — every 3 ticks
        BukkitTask fissureTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
            int step = 0;

            @Override
            public void run() {
                if (step >= fissureLength || boss.isDead()) return;

                World world = witherLoc.getWorld();

                // Calculate current fissure position
                Location fissureLoc = witherLoc.clone().add(fissureDir.clone().multiply(step));
                // Snap to ground level
                fissureLoc.setY(world.getHighestBlockYAt(fissureLoc) + 1);

                // Crack block display (CRACKED_STONE_BRICKS)
                boss.getDisplayPool().spawnBlockDisplay(
                        fissureLoc.clone().add(0, -0.5, 0),
                        Material.CRACKED_STONE_BRICKS.createBlockData(),
                        60 // 3 second display lifetime
                );

                // Explosion particles (small, no block damage)
                world.spawnParticle(Particle.EXPLOSION, fissureLoc, 1, 0, 0, 0, 0);

                // Fire particles rising from the crack
                world.spawnParticle(Particle.FLAME, fissureLoc, 8, 0.2, 0.3, 0.2, 0.03);
                world.spawnParticle(Particle.LAVA, fissureLoc, 3, 0.2, 0.1, 0.2, 0);

                // Small explosion sound at low volume
                world.playSound(fissureLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);

                // Damage players within 2 blocks
                for (Player player : boss.getPlayersInRange(fissureLength + 5)) {
                    if (player.getLocation().distanceSquared(fissureLoc) <= 4.0) { // 2^2
                        player.damage(8.0, boss.getWither());
                    }
                }

                step++;
            }
        }, 0L, 3L);
        activeTasks.add(fissureTask);

        // Cancel after all steps complete + buffer
        long totalDuration = (long) fissureLength * 3 + 5;
        BukkitTask cancelTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            fissureTask.cancel();
            activeTasks.remove(fissureTask);
        }, totalDuration);
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
