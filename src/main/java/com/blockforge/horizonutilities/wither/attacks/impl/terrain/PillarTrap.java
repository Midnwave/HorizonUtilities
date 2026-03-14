package com.blockforge.horizonutilities.wither.attacks.impl.terrain;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.util.WitherGriefBypass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * For each player within 20 blocks (max 4), spawns obsidian pillars at their position.
 * A 0.5 second warning with rumble particles precedes the pillar placement.
 * Pillars are 1x1x4 obsidian columns that encase the player.
 * Blocks are restored after 10 seconds.
 */
public class PillarTrap implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final List<BlockState> originalStates = new ArrayList<>();

    @Override
    public String getId() {
        return "pillar_trap";
    }

    @Override
    public String getDisplayName() {
        return "Pillar Trap";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 16.0;
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
        List<Player> targets = boss.getPlayersInRange(20);
        if (targets.isEmpty()) return;

        // Limit to 4 players
        if (targets.size() > 4) {
            targets = targets.subList(0, 4);
        }

        // Capture target locations at the moment of casting
        List<Location> targetLocations = new ArrayList<>();
        for (Player player : targets) {
            targetLocations.add(player.getLocation().getBlock().getLocation().clone());
        }

        // Warning phase: rumble particles + anvil sound at each location
        for (Location loc : targetLocations) {
            loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    loc.clone().add(0.5, 0.1, 0.5), 15, 0.4, 0.1, 0.4, 0.01);
            loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
        }

        // After 0.5 second warning (10 ticks), place pillars
        BukkitTask placeTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            if (boss.isDead()) return;

            for (Location base : targetLocations) {
                // Place 4 obsidian blocks upward (y+0 to y+3)
                for (int y = 0; y < 4; y++) {
                    Block block = base.clone().add(0, y, 0).getBlock();

                    if (!WitherGriefBypass.canModifyBlock(block.getLocation(), boss)) continue;

                    // Record original state
                    originalStates.add(block.getState());
                    block.setType(Material.OBSIDIAN);
                }

                // Pillar placement particles
                base.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0.5, 2, 0.5),
                        20, 0.3, 1.0, 0.3, 0.1, Material.OBSIDIAN.createBlockData());
                base.getWorld().playSound(base, Sound.BLOCK_STONE_PLACE, 1.5f, 0.5f);
            }

            // Restore blocks after 10 seconds (200 ticks)
            BukkitTask restoreTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                for (BlockState state : originalStates) {
                    Block current = state.getBlock();
                    if (current.getType() == Material.OBSIDIAN) {
                        state.update(true, false);
                    }
                }
                originalStates.clear();
            }, 200L);
            activeTasks.add(restoreTask);
        }, 10L);
        activeTasks.add(placeTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();

        // Restore any remaining pillar blocks
        for (BlockState state : originalStates) {
            Block current = state.getBlock();
            if (current.getType() == Material.OBSIDIAN) {
                state.update(true, false);
            }
        }
        originalStates.clear();
    }
}
