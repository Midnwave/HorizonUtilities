package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
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
 * Targets one player and places BONE_BLOCK in a 3x3x3 cage around them (walls only).
 * Cage blocks are removed after 10 seconds if player hasn't broken them.
 * Checks WitherGriefBypass before placing blocks. Records blocks for restoration.
 */
public class BoneCage implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "bone_cage";
    }

    @Override
    public String getDisplayName() {
        return "Bone Cage";
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
        Player target = boss.getHighestAggroTarget(64);
        if (target == null) {
            target = boss.getNearestPlayer(64);
        }
        if (target == null) return;

        Location center = target.getLocation().getBlock().getLocation();
        List<BlockState> originalStates = new ArrayList<>();

        // Spiral bone particles during cage formation
        WitherParticles.spiral(center.clone().add(0.5, 0, 0.5), 2.0, 3.0, 30, Particle.BLOCK, Material.BONE_BLOCK.createBlockData());

        // Build 3x3x3 cage walls around the player
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    // Only place on edges (walls), not interior
                    boolean isEdge = (x == -1 || x == 1 || z == -1 || z == 1 || y == 0 || y == 2);
                    if (!isEdge) continue;

                    Block block = center.clone().add(x, y, z).getBlock();

                    if (!WitherGriefBypass.canModifyBlock(block.getLocation(), boss)) {
                        continue;
                    }

                    // Only replace air or replaceable blocks
                    if (!block.getType().isAir() && block.getType().isSolid()) {
                        continue;
                    }

                    originalStates.add(block.getState());
                    block.setType(Material.BONE_BLOCK);
                }
            }
        }

        center.getWorld().playSound(center, Sound.ENTITY_SKELETON_AMBIENT, 2.0f, 0.5f);

        // Schedule cleanup after 10 seconds (200 ticks) — restore original blocks
        BukkitTask cleanupTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            for (BlockState state : originalStates) {
                Block current = state.getBlock();
                if (current.getType() == Material.BONE_BLOCK) {
                    state.update(true, false);
                }
            }
        }, 200L);
        activeTasks.add(cleanupTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
