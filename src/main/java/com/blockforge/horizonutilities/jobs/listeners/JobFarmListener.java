package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;

/**
 * Awards FARM job income when a player breaks a fully-grown crop.
 * Runs before {@link JobBlockListener} so we can process it as a FARM action
 * rather than a plain BREAK; the block-tracker check is not applied here
 * because naturally-grown crops are never player-placed in the tracker.
 */
public class JobFarmListener implements Listener {

    /** Materials that count as harvestable crops. */
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.CAVE_VINES_PLANT
    );

    private final HorizonUtilitiesPlugin plugin;

    public JobFarmListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Use HIGH priority so this fires before MONITOR (which is where
     * {@link JobBlockListener} runs), giving us first pick on crop breaks.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material mat = event.getBlock().getType();
        if (!CROPS.contains(mat)) return;

        // Only pay for fully grown crops
        if (event.getBlock().getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;
        }

        plugin.getJobManager().processAction(event.getPlayer(), JobAction.FARM, mat.name());
    }
}
