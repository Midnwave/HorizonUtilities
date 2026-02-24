package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

/**
 * Awards job income when a brewing operation completes.
 *
 * <p>Because {@link BrewEvent} does not expose the player who started the brew,
 * we use a metadata key set on the BrewingStand block when a player opens its
 * inventory (set separately via {@link org.bukkit.event.inventory.InventoryOpenEvent}
 * in a companion listener or a future enhancement). If no owner metadata is
 * found, the event is silently ignored — income is only paid when we can
 * attribute it to a specific player.
 */
public class JobBrewListener implements Listener {

    /** Metadata key stored on BrewingStand blocks. */
    public static final String OWNER_META = "horizonutilities_brew_owner";

    private final HorizonUtilitiesPlugin plugin;

    public JobBrewListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (!(event.getBlock().getState() instanceof BrewingStand stand)) return;

        List<MetadataValue> meta = stand.getMetadata(OWNER_META);
        if (meta.isEmpty()) return;

        Object ownerObj = meta.get(0).value();
        if (!(ownerObj instanceof java.util.UUID ownerUuid)) return;

        Player player = plugin.getServer().getPlayer(ownerUuid);
        if (player == null) return;

        // Count how many output slots have items to determine brews completed
        int count = 0;
        var contents = event.getContents();
        for (int i = 0; i < 3; i++) {
            if (contents.getItem(i) != null) count++;
        }

        for (int i = 0; i < count; i++) {
            plugin.getJobManager().processAction(player, JobAction.BREW, "POTION");
        }
    }
}
