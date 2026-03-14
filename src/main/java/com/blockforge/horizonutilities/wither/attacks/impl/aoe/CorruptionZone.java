package com.blockforge.horizonutilities.wither.attacks.impl.aoe;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.util.WitherGriefBypass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Places WITHER_ROSE flowers in a 10-block radius around the wither.
 * Roses are removed after 30 seconds. Checks grief bypass before placing.
 */
public class CorruptionZone implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final List<Block> placedRoses = new ArrayList<>();

    @Override
    public String getId() {
        return "corruption_zone";
    }

    @Override
    public String getDisplayName() {
        return "Corruption Zone";
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
        return 1.5;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Location center = boss.getWither().getLocation();
        World world = center.getWorld();
        int radius = 10;

        world.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.3f);

        // Place wither roses on valid ground blocks
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;

                // Find the highest solid block in the column
                Location checkLoc = center.clone().add(x, 0, z);
                Block surface = world.getHighestBlockAt(checkLoc);
                Block above = surface.getRelative(BlockFace.UP);

                // Verify placement conditions
                if (!above.getType().isAir()) continue;
                if (!surface.getType().isSolid()) continue;

                Location blockLoc = above.getLocation();
                if (!WitherGriefBypass.canModifyBlock(blockLoc, boss)) continue;

                // Random scatter — don't fill every block
                if (Math.random() > 0.15) continue;

                // Falling dust particles during placement
                world.spawnParticle(Particle.FALLING_DUST,
                        blockLoc.clone().add(0.5, 1.0, 0.5), 3,
                        0.3, 0.5, 0.3, 0,
                        Material.SOUL_SOIL.createBlockData());

                above.setType(Material.WITHER_ROSE);
                placedRoses.add(above);
            }
        }

        // Schedule removal after 30 seconds (600 ticks)
        BukkitTask removeTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            for (Block rose : placedRoses) {
                if (rose.getType() == Material.WITHER_ROSE) {
                    rose.setType(Material.AIR);
                }
            }
            placedRoses.clear();
        }, 600L);
        activeTasks.add(removeTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();

        // Remove any remaining roses
        for (Block rose : placedRoses) {
            if (rose.getType() == Material.WITHER_ROSE) {
                rose.setType(Material.AIR);
            }
        }
        placedRoses.clear();
    }
}
