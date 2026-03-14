package com.blockforge.horizonutilities.wither.attacks.impl.terrain;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import com.blockforge.horizonutilities.wither.util.WitherGriefBypass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts solid blocks in an 8-block radius around the wither to soul sand.
 * A dark wave of particles expands outward during conversion.
 * Original blocks are recorded and restored after 30 seconds.
 */
public class TerrainDecay implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final Map<Location, BlockData> originalBlocks = new HashMap<>();

    @Override
    public String getId() {
        return "terrain_decay";
    }

    @Override
    public String getDisplayName() {
        return "Terrain Decay";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
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
        int radius = 8;

        world.playSound(center, Sound.BLOCK_SOUL_SAND_PLACE, 2.0f, 0.5f);

        // Dark wave particles expanding outward
        WitherParticles.ring(center.clone().add(0, 0.5, 0), radius, 48, Particle.SOUL, null);

        // Scan and convert blocks at ground level
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;

                // Find the highest solid block in the column near the wither's Y level
                Location checkLoc = center.clone().add(x, 0, z);
                Block surface = world.getHighestBlockAt(checkLoc);

                // Only convert if within reasonable vertical range of the wither
                if (Math.abs(surface.getY() - center.getY()) > 5) continue;

                // Skip bedrock and non-solid blocks
                if (surface.getType() == Material.BEDROCK) continue;
                if (!surface.getType().isSolid()) continue;
                if (surface.getType() == Material.SOUL_SAND) continue;

                Location blockLoc = surface.getLocation();
                if (!WitherGriefBypass.canModifyBlock(blockLoc, boss)) continue;

                // Record original block data for restoration
                originalBlocks.put(blockLoc.clone(), surface.getBlockData().clone());

                // Convert to soul sand
                surface.setType(Material.SOUL_SAND);

                // Decay particles at conversion point
                world.spawnParticle(Particle.SMOKE, blockLoc.clone().add(0.5, 1.0, 0.5),
                        5, 0.3, 0.2, 0.3, 0.02);
            }
        }

        // Expanding dark wave particle effect
        Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            WitherParticles.ring(center.clone().add(0, 1, 0), radius * 0.5, 32, Particle.SOUL_FIRE_FLAME, null);
        }, 5L);
        Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            WitherParticles.ring(center.clone().add(0, 1, 0), radius, 48, Particle.SOUL_FIRE_FLAME, null);
        }, 10L);

        // Restore original blocks after 30 seconds (600 ticks)
        BukkitTask restoreTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                Block block = entry.getKey().getBlock();
                if (block.getType() == Material.SOUL_SAND) {
                    block.setBlockData(entry.getValue(), false);
                }
            }
            originalBlocks.clear();
        }, 600L);
        activeTasks.add(restoreTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();

        // Restore any remaining converted blocks
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Block block = entry.getKey().getBlock();
            if (block.getType() == Material.SOUL_SAND) {
                block.setBlockData(entry.getValue(), false);
            }
        }
        originalBlocks.clear();
    }
}
