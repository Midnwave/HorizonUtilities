package com.blockforge.horizonutilities.jobs.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import com.blockforge.horizonutilities.jobs.leaderboard.JobLeaderboardGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;

/**
 * Handles all clicks inside every Jobs GUI (Browse, Info, Leaderboard).
 */
public class JobsGUIListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public JobsGUIListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof JobBrowseGUI browseGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            handleBrowseClick(player, browseGUI, event.getRawSlot());

        } else if (holder instanceof JobInfoGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            // Close slot = 26
            if (event.getRawSlot() == 26) player.closeInventory();

        } else if (holder instanceof JobLeaderboardGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            // Close slot = 49
            if (event.getRawSlot() == 49) {
                player.closeInventory();
            } else if (event.getRawSlot() >= 45 && event.getRawSlot() <= 53) {
                // Job tab clicked — reopen with selected job
                handleLeaderboardTabClick(player, event.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Nothing to clean up currently; state is held per-GUI instance.
    }

    // -------------------------------------------------------------------------
    // Browse GUI handler
    // -------------------------------------------------------------------------

    private void handleBrowseClick(Player player, JobBrowseGUI gui, int slot) {
        if (slot == 49) { // close
            player.closeInventory();
            return;
        }
        String jobId = gui.getJobIdAt(slot);
        if (jobId == null) return;

        Job job = plugin.getJobManager().getJob(jobId);
        if (job == null) return;

        Optional<JobPlayer> jp = plugin.getJobManager()
                .getPlayerJobs(player.getUniqueId()).stream()
                .filter(j -> j.getJobId().equalsIgnoreCase(jobId))
                .findFirst();

        if (jp.isPresent()) {
            // Open info GUI
            player.closeInventory();
            new JobInfoGUI(plugin, player, job, jp.get()).open();
        } else {
            // Attempt to join
            player.closeInventory();
            plugin.getJobManager().joinJob(player, jobId);
        }
    }

    // -------------------------------------------------------------------------
    // Leaderboard tab handler
    // -------------------------------------------------------------------------

    private void handleLeaderboardTabClick(Player player, int slot) {
        // Slot 45 = overall, 46+ = individual jobs in definition order
        if (slot == 45) {
            player.closeInventory();
            new JobLeaderboardGUI(plugin, player, null).open();
            return;
        }
        int idx = slot - 46;
        var jobs = plugin.getJobManager().getAllJobs().stream().toList();
        if (idx < jobs.size()) {
            player.closeInventory();
            new JobLeaderboardGUI(plugin, player, jobs.get(idx).getId()).open();
        }
    }

}

