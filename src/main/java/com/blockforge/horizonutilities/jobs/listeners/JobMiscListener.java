package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

/**
 * Handles miscellaneous job actions: taming, shearing, milking, repairing
 * (anvil), and villager trading.
 */
public class JobMiscListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public JobMiscListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Tame
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        plugin.getJobManager().processAction(player, JobAction.TAME,
                event.getEntity().getType().name());
    }

    // -------------------------------------------------------------------------
    // Shear
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        plugin.getJobManager().processAction(event.getPlayer(), JobAction.SHEAR,
                event.getEntity().getType().name());
    }

    // -------------------------------------------------------------------------
    // Milk / water bucket fill
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Material bucket = event.getItemStack().getType();
        // Only milk buckets count; water/lava/powder-snow do not
        if (bucket == Material.MILK_BUCKET) {
            plugin.getJobManager().processAction(event.getPlayer(), JobAction.MILK, "COW");
        }
    }

    // -------------------------------------------------------------------------
    // Anvil repair
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilRepair(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        // Slot 2 is the output slot of the anvil
        if (event.getRawSlot() != 2) return;
        var result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;
        plugin.getJobManager().processAction(player, JobAction.REPAIR, result.getType().name());
    }

    // -------------------------------------------------------------------------
    // Villager trade
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        plugin.getJobManager().processAction(event.getPlayer(), JobAction.TRADE_PLAYER, "VILLAGER");
    }
}
