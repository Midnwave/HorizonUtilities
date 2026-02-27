package com.blockforge.horizonutilities.tournaments;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * Routes game events into {@link TournamentManager#recordProgress}.
 */
public class TournamentListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final TournamentManager manager;

    public TournamentListener(HorizonUtilitiesPlugin plugin, TournamentManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        manager.recordProgress(event.getPlayer(),
                TournamentObjectiveType.MINE_BLOCKS,
                event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player) {
            manager.recordProgress(killer, TournamentObjectiveType.KILL_PLAYERS,
                    "PLAYER", 1);
        } else {
            manager.recordProgress(killer, TournamentObjectiveType.KILL_MOBS,
                    event.getEntity().getType().name(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var result = event.getRecipe().getResult();
        manager.recordProgress(player, TournamentObjectiveType.CRAFT_ITEMS,
                result.getType().name(), result.getAmount());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        manager.recordProgress(event.getPlayer(), TournamentObjectiveType.COLLECT_ITEMS,
                event.getItem().getItemStack().getType().name(),
                event.getItem().getItemStack().getAmount());
    }
}
