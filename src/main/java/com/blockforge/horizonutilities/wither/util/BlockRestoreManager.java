package com.blockforge.horizonutilities.wither.util;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks blocks modified during Wither Sovereign boss fights and restores them
 * to their original state when the fight ends or after a configurable delay.
 */
public class BlockRestoreManager {

    private final HorizonUtilitiesPlugin plugin;

    /**
     * Maps fight ID -> (block key -> original block state).
     * Uses ConcurrentHashMap for thread safety on the outer map.
     */
    private final Map<UUID, Map<BlockKey, BlockState>> modifiedBlocks = new ConcurrentHashMap<>();

    public BlockRestoreManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Records a block's current state before it is modified by the boss fight.
     * Only records the block if it hasn't already been recorded for this fight
     * (preserves the original state, not intermediate states).
     *
     * @param fightId the UUID of the boss fight
     * @param block   the block about to be modified
     */
    public void recordBlock(UUID fightId, Block block) {
        Map<BlockKey, BlockState> fightBlocks = modifiedBlocks.computeIfAbsent(
                fightId, k -> new HashMap<>());

        BlockKey key = new BlockKey(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        );

        // Only record if not already tracked for this fight (preserve original)
        if (!fightBlocks.containsKey(key)) {
            fightBlocks.put(key, block.getState());
        }
    }

    /**
     * Restores all blocks modified during a fight to their original state.
     * Removes the fight from tracking after restoration.
     *
     * @param fightId the UUID of the boss fight
     */
    public void restoreAll(UUID fightId) {
        Map<BlockKey, BlockState> fightBlocks = modifiedBlocks.remove(fightId);
        if (fightBlocks == null || fightBlocks.isEmpty()) {
            return;
        }

        // Restore must happen on the main thread
        if (Bukkit.isPrimaryThread()) {
            doRestore(fightBlocks);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> doRestore(fightBlocks));
        }
    }

    /**
     * Performs the actual block state restoration.
     */
    private void doRestore(Map<BlockKey, BlockState> fightBlocks) {
        int count = 0;
        for (BlockState state : fightBlocks.values()) {
            // update(true, false) = force update, don't apply physics
            state.update(true, false);
            count++;
        }
        plugin.getLogger().info("Restored " + count + " blocks from boss fight.");
    }

    /**
     * Schedules a delayed restoration of all blocks for a fight.
     * Useful for restoring terrain after the fight ends with a grace period.
     *
     * @param fightId      the UUID of the boss fight
     * @param delaySeconds how many seconds to wait before restoring
     */
    public void scheduleRestore(UUID fightId, int delaySeconds) {
        long delayTicks = delaySeconds * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreAll(fightId), delayTicks);
    }

    /**
     * Returns the number of blocks tracked for a specific fight.
     *
     * @param fightId the fight UUID
     * @return number of tracked blocks, or 0 if fight is not tracked
     */
    public int getTrackedBlockCount(UUID fightId) {
        Map<BlockKey, BlockState> fightBlocks = modifiedBlocks.get(fightId);
        return fightBlocks != null ? fightBlocks.size() : 0;
    }

    /**
     * Record key combining world name and block coordinates.
     * Used as a map key to uniquely identify block positions.
     */
    private record BlockKey(String world, int x, int y, int z) {}
}
