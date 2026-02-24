package com.blockforge.horizonutilities.jobs.gui;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 6-row chest GUI listing all available jobs. Each job is shown as its icon
 * with description, pay summary, and the player's current level if enrolled.
 */
public class JobBrowseGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int CLOSE_SLOT = 49;

    private final HorizonUtilitiesPlugin plugin;
    private final Player viewer;
    private Inventory inventory;

    public JobBrowseGUI(HorizonUtilitiesPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, SIZE,
                Component.text("Jobs", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, false));
        populate();
        viewer.openInventory(inventory);
    }

    private void populate() {
        // Gray glass border
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, pane);

        Collection<Job> jobs = plugin.getJobManager().getAllJobs();
        List<JobPlayer> myJobs = plugin.getJobManager().getPlayerJobs(viewer.getUniqueId());

        int slot = 10;
        for (Job job : jobs) {
            if (slot >= CLOSE_SLOT) break;
            // Skip border columns
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }

            Optional<JobPlayer> enrolled = myJobs.stream()
                    .filter(jp -> jp.getJobId().equalsIgnoreCase(job.getId()))
                    .findFirst();

            inventory.setItem(slot, buildJobItem(job, enrolled.orElse(null)));
            slot++;
        }

        inventory.setItem(CLOSE_SLOT, closeButton());
    }

    private ItemStack buildJobItem(Job job, JobPlayer jp) {
        ItemStack item = new ItemStack(job.getIcon());
        var meta = item.getItemMeta();

        meta.displayName(Component.text(job.getDisplayName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(job.getDescription(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (jp != null) {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("Enrolled", NamedTextColor.GREEN)));
            lore.add(Component.text("Level: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(jp.getLevel(), NamedTextColor.AQUA)));
            if (jp.getPrestige() > 0) {
                lore.add(Component.text("Prestige: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(jp.getPrestige(), NamedTextColor.LIGHT_PURPLE)));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Click to view job info", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("Not enrolled", NamedTextColor.RED)));
            lore.add(Component.empty());
            lore.add(Component.text("Click to join", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Close", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the job id whose icon sits at the given slot, or null. */
    public String getJobIdAt(int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR
                || item.getType() == Material.GRAY_STAINED_GLASS_PANE
                || item.getType() == Material.BARRIER) return null;

        // Match by icon material to job
        for (Job job : plugin.getJobManager().getAllJobs()) {
            if (job.getIcon() == item.getType()) return job.getId();
        }
        return null;
    }
}
