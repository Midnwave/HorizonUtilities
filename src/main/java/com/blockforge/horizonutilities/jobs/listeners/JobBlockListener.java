package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import com.blockforge.horizonutilities.jobs.antiexploit.BlockTracker;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/**
 * Handles block break/place events for the jobs system and keeps
 * {@link BlockTracker} in sync with piston and explosion events.
 */
public class JobBlockListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public JobBlockListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Break
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var player = event.getPlayer();
        var block  = event.getBlock();
        var tracker = plugin.getJobManager().getBlockTracker();

        // Skip player-placed blocks (anti-exploit)
        if (tracker.isPlayerPlaced(block)) {
            tracker.clearBlock(block);
            return;
        }

        tracker.clearBlock(block); // clean up any stale tag

        String material = block.getType().name();
        plugin.getJobManager().processAction(player, JobAction.BREAK, material);
    }

    // -------------------------------------------------------------------------
    // Place
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Tag the block as player-placed; no income paid for placement
        plugin.getJobManager().getBlockTracker()
                .markAsPlaced(event.getBlockPlaced(), event.getPlayer().getUniqueId());
        // Notify JobManager so PLACE action listeners still fire (no payout)
        plugin.getJobManager().processAction(event.getPlayer(), JobAction.PLACE,
                event.getBlockPlaced().getType().name());
    }

    // -------------------------------------------------------------------------
    // Explosions — clear tracker tags for destroyed blocks
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        BlockTracker tracker = plugin.getJobManager().getBlockTracker();
        for (Block block : event.blockList()) {
            tracker.clearBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        BlockTracker tracker = plugin.getJobManager().getBlockTracker();
        for (Block block : event.blockList()) {
            tracker.clearBlock(block);
        }
    }

    // -------------------------------------------------------------------------
    // Pistons — transfer tags when blocks move
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        List<Block> moved = event.getBlocks();
        plugin.getJobManager().getBlockTracker()
                .handlePistonMove(moved, event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky()) return;
        List<Block> moved = event.getBlocks();
        plugin.getJobManager().getBlockTracker()
                .handlePistonMove(moved, event.getDirection());
    }
}
