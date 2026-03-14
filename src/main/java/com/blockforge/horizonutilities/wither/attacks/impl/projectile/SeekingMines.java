package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

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
 * Spawns 5 floating orbs (ItemDisplay with MAGMA_CREAM) at random positions
 * 5-10 blocks from the wither. Each orb slowly chases the nearest player
 * (velocity 0.15/tick). If within 2 blocks of a player, explodes for 8 HP
 * damage + knockback. 10-second (200 tick) lifetime with pulsing glow.
 */
public class SeekingMines implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "seeking_mines";
    }

    @Override
    public String getDisplayName() {
        return "Seeking Mines";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 14.0;
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
        World world = boss.getWither().getWorld();
        Location witherLoc = boss.getWither().getLocation();
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        List<ItemDisplay> mines = new ArrayList<>();

        // Spawn 5 floating orbs at random positions 5-10 blocks from wither
        for (int i = 0; i < 5; i++) {
            double angle = rand.nextDouble(0, 2 * Math.PI);
            double distance = rand.nextDouble(5.0, 10.0);
            double yOffset = rand.nextDouble(1.0, 3.0);

            Location spawnLoc = witherLoc.clone().add(
                    Math.cos(angle) * distance,
                    yOffset,
                    Math.sin(angle) * distance
            );

            ItemDisplay mine = boss.getDisplayPool().spawnItemDisplay(
                    spawnLoc,
                    new ItemStack(Material.MAGMA_CREAM),
                    200 // 10 second lifetime
            );
            mines.add(mine);
        }

        // Spawn sound
        world.playSound(witherLoc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.5f);

        // Tracking booleans for each mine
        boolean[] exploded = new boolean[mines.size()];

        // Chase + proximity check task
        BukkitTask chaseTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            for (int i = 0; i < mines.size(); i++) {
                if (exploded[i]) continue;

                ItemDisplay mine = mines.get(i);
                if (!mine.isValid()) {
                    exploded[i] = true;
                    continue;
                }

                Location mineLoc = mine.getLocation();

                // Find nearest player
                Player nearest = null;
                double nearestDistSq = Double.MAX_VALUE;
                for (Player player : boss.getPlayersInRange(64)) {
                    double distSq = player.getLocation().distanceSquared(mineLoc);
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = player;
                    }
                }

                if (nearest == null) continue;

                // Pulsing glow effect
                world.spawnParticle(Particle.FLAME, mineLoc, 3, 0.2, 0.2, 0.2, 0.01);

                // Check proximity — 2 blocks
                if (nearestDistSq <= 4.0) { // 2^2
                    // Explode
                    exploded[i] = true;
                    explodeMine(boss, mine, mineLoc);
                    continue;
                }

                // Chase nearest player — move toward them at 0.15 blocks/tick
                Vector toPlayer = nearest.getLocation().add(0, 1, 0).toVector()
                        .subtract(mineLoc.toVector());
                if (toPlayer.lengthSquared() > 0.01) {
                    Vector moveVec = toPlayer.normalize().multiply(0.15);
                    mine.teleport(mineLoc.add(moveVec));
                }
            }
        }, 1L, 1L);
        activeTasks.add(chaseTask);

        // Timeout: explode remaining mines after 200 ticks
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            chaseTask.cancel();
            activeTasks.remove(chaseTask);

            for (int i = 0; i < mines.size(); i++) {
                if (!exploded[i]) {
                    ItemDisplay mine = mines.get(i);
                    if (mine.isValid()) {
                        explodeMine(boss, mine, mine.getLocation());
                    }
                    exploded[i] = true;
                }
            }
        }, 200L);
        activeTasks.add(timeout);
    }

    private void explodeMine(WitherBossEntity boss, ItemDisplay mine, Location loc) {
        World world = loc.getWorld();

        // Explosion particles
        world.spawnParticle(Particle.EXPLOSION, loc, 2, 0.3, 0.3, 0.3, 0);
        world.spawnParticle(Particle.FLAME, loc, 20, 0.8, 0.8, 0.8, 0.05);
        world.spawnParticle(Particle.SMOKE, loc, 10, 0.5, 0.5, 0.5, 0.03);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        // Damage + knockback within 2 blocks
        double radius = 2.0;
        double radiusSq = radius * radius;

        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(loc) <= radiusSq) {
                player.damage(8.0, boss.getWither());

                // Knockback away from mine
                Vector knockback = player.getLocation().toVector().subtract(loc.toVector());
                if (knockback.lengthSquared() < 0.01) {
                    knockback = new Vector(0, 1, 0);
                } else {
                    knockback.normalize();
                }
                knockback.setY(0.6);
                knockback.multiply(0.8);
                player.setVelocity(player.getVelocity().add(knockback));
            }
        }

        // Remove display
        if (mine.isValid()) {
            boss.getDisplayPool().remove(mine);
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
