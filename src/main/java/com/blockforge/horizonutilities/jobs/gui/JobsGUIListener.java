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
 * Handles all clicks inside every Jobs GUI
 * (Browse, Info, Quests, QuestEditor, Leaderboard).
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

        } else if (holder instanceof JobQuestsGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            // Close slot = 22
            if (event.getRawSlot() == 22) player.closeInventory();

        } else if (holder instanceof QuestEditorGUI editorGUI) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            handleEditorClick(player, editorGUI, event.getRawSlot(),
                    event.getClick().isRightClick());

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

    // -------------------------------------------------------------------------
    // Quest editor handler
    // -------------------------------------------------------------------------

    private void handleEditorClick(Player player, QuestEditorGUI gui, int slot, boolean rightClick) {
        QuestEditorGUI.EditorState state = gui.getState();

        // Close
        if (slot == 49) { player.closeInventory(); return; }

        // Back
        if (slot == 45 && state.page != QuestEditorGUI.Page.JOB_SELECT) {
            player.closeInventory();
            if (state.page == QuestEditorGUI.Page.QUEST_DETAIL) {
                state.page = QuestEditorGUI.Page.QUEST_LIST;
            } else {
                state.page = QuestEditorGUI.Page.JOB_SELECT;
            }
            new QuestEditorGUI(plugin, player, state).open();
            return;
        }

        switch (state.page) {
            case JOB_SELECT -> {
                // Clicking a job icon opens quest list for that job
                var item = gui.getInventory().getItem(slot);
                if (item == null) return;
                for (Job job : plugin.getJobManager().getAllJobs()) {
                    if (job.getIcon() == item.getType()) {
                        state.selectedJobId = job.getId();
                        state.page = QuestEditorGUI.Page.QUEST_LIST;
                        player.closeInventory();
                        new QuestEditorGUI(plugin, player, state).open();
                        return;
                    }
                }
            }
            case QUEST_LIST -> {
                // Add button
                if (slot == 53) {
                    player.closeInventory();
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            "[Jobs] Quest creation via chat is not yet implemented.", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    return;
                }
                // Quest item click — detail or delete
                var item = gui.getInventory().getItem(slot);
                if (item == null) return;
                // We can't easily reverse-lookup the quest def from the item here without storing it;
                // left-click opens detail, right-click is handled in a real implementation via NBT tags.
                // For now just close; a full implementation would store quest ids in PDC.
            }
            case QUEST_DETAIL -> {
                // No interactive elements beyond back/close
            }
        }
    }
}
