package com.blockforge.horizonutilities.jobs.leaderboard;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 6-row chest GUI showing ranked players for a specific job (or overall).
 * Top 5 rows: player skulls with rank, level, xp, and earnings in lore.
 * Bottom row: job-selector tabs and a close button.
 */
public class JobLeaderboardGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int CLOSE_SLOT = 49;

    private final HorizonUtilitiesPlugin plugin;
    private final Player viewer;
    private final String jobId; // null = overall
    private Inventory inventory;

    public JobLeaderboardGUI(HorizonUtilitiesPlugin plugin, Player viewer, String jobId) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.jobId = jobId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Builds the GUI and opens it for the viewer. */
    public void open() {
        String title = jobId == null ? "Top Players (Overall)" : "Top Players: " + resolveDisplayName(jobId);
        inventory = Bukkit.createInventory(this, SIZE,
                Component.text(title, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        populate();
        viewer.openInventory(inventory);
    }

    private void populate() {
        // Fill border with gray glass
        ItemStack border = borderPane();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, border);
        }

        // Fetch leaderboard data async, then set on main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ItemStack> entries = buildEntryItems();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < entries.size() && i < ENTRY_SLOTS.length; i++) {
                    inventory.setItem(ENTRY_SLOTS[i], entries.get(i));
                }
                // Job tab buttons along the bottom
                placeJobTabs();
                // Close button
                inventory.setItem(CLOSE_SLOT, closeButton());
            });
        });
    }

    private List<ItemStack> buildEntryItems() {
        List<ItemStack> result = new ArrayList<>();

        if (jobId == null) {
            // Overall leaderboard
            List<Map.Entry<String, Integer>> top = plugin.getJobManager().getLeaderboard().getTopOverall(ENTRY_SLOTS.length);
            int rank = 1;
            for (Map.Entry<String, Integer> entry : top) {
                result.add(buildOverallSkull(rank++, entry.getKey(), entry.getValue()));
            }
        } else {
            // Per-job leaderboard
            List<JobPlayer> top = plugin.getJobManager().getLeaderboard().getTopByJob(jobId, ENTRY_SLOTS.length);
            int rank = 1;
            for (JobPlayer jp : top) {
                result.add(buildJobSkull(rank++, jp));
            }
        }
        return result;
    }

    private ItemStack buildJobSkull(int rank, JobPlayer jp) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(jp.getPlayerUuid()));
        meta.displayName(Component.text("#" + rank + " " + jp.getPlayerName(), rankColour(rank))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Level: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(jp.getLevel(), NamedTextColor.AQUA)));
        lore.add(Component.text("Prestige: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(jp.getPrestige(), NamedTextColor.LIGHT_PURPLE)));
        lore.add(Component.text("Total Earned: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(plugin.getVaultHook().format(jp.getTotalEarned()), NamedTextColor.GREEN)));
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildOverallSkull(int rank, String playerName, int totalLevel) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
        meta.displayName(Component.text("#" + rank + " " + playerName, rankColour(rank))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Total Level: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(totalLevel, NamedTextColor.AQUA)));
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private void placeJobTabs() {
        // Overall tab at slot 45
        inventory.setItem(45, jobTab(null));
        // Individual job tabs starting at 46
        Collection<Job> jobs = plugin.getJobManager().getAllJobs();
        int slot = 46;
        for (Job job : jobs) {
            if (slot >= CLOSE_SLOT) break;
            inventory.setItem(slot++, jobTab(job));
        }
    }

    private ItemStack jobTab(Job job) {
        Material mat = job == null ? Material.NETHER_STAR : job.getIcon();
        String name = job == null ? "Overall" : job.getDisplayName();
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        boolean active = (job == null && jobId == null)
                || (job != null && job.getId().equalsIgnoreCase(jobId));
        NamedTextColor colour = active ? NamedTextColor.GOLD : NamedTextColor.GRAY;
        meta.displayName(Component.text(name, colour).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Close", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack borderPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private NamedTextColor rankColour(int rank) {
        return switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.WHITE;
        };
    }

    private String resolveDisplayName(String id) {
        Job job = plugin.getJobManager().getJob(id);
        return job != null ? job.getDisplayName() : id;
    }
}
