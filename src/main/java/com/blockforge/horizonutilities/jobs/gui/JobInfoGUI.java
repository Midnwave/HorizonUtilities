package com.blockforge.horizonutilities.jobs.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.JobAction;
import com.blockforge.horizonutilities.jobs.JobActionEntry;
import com.blockforge.horizonutilities.jobs.JobLevelCalculator;
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
import java.util.List;
import java.util.Map;

/**
 * 3-row GUI showing detailed info about a player's enrolled job:
 * level, XP bar (coloured glass panes), recent earnings, and top pay actions.
 */
public class JobInfoGUI implements InventoryHolder {

    private static final int SIZE = 27;
    // XP bar spans slots 9-17 (middle row)
    private static final int XP_BAR_START = 9;
    private static final int XP_BAR_LENGTH = 9;
    private static final int CLOSE_SLOT = 26;
    private static final int JOB_ICON_SLOT = 4;
    private static final int STATS_SLOT = 0;
    private static final int ACTIONS_SLOT = 18;

    private final HorizonUtilitiesPlugin plugin;
    private final Player viewer;
    private final Job job;
    private final JobPlayer jp;
    private Inventory inventory;

    public JobInfoGUI(HorizonUtilitiesPlugin plugin, Player viewer, Job job, JobPlayer jp) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.job = job;
        this.jp = jp;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        inventory = Bukkit.createInventory(this, SIZE,
                Component.text(job.getDisplayName() + " Info", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, false));
        populate();
        viewer.openInventory(inventory);
    }

    private void populate() {
        // Job icon
        inventory.setItem(JOB_ICON_SLOT, buildJobIcon());

        // Stats item
        inventory.setItem(STATS_SLOT, buildStatsItem());

        // XP bar
        buildXpBar();

        // Top actions
        inventory.setItem(ACTIONS_SLOT, buildActionsItem());

        // Close
        inventory.setItem(CLOSE_SLOT, closeButton());

        // Fill remaining with air-coloured panes
        ItemStack pane = pane();
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, pane);
        }
    }

    private ItemStack buildJobIcon() {
        ItemStack item = new ItemStack(job.getIcon());
        var meta = item.getItemMeta();
        meta.displayName(Component.text(job.getDisplayName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(job.getDescription(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildStatsItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Your Stats", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        double xpNeeded = JobLevelCalculator.getXpToNextLevel(
                jp.getXp(), jp.getLevel(),
                plugin.getJobManager().getConfig().getXpBase(),
                plugin.getJobManager().getConfig().getXpExponent());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Level: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(jp.getLevel(), NamedTextColor.AQUA)));
        lore.add(Component.text("Prestige: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(jp.getPrestige(), NamedTextColor.LIGHT_PURPLE)));
        lore.add(Component.text("XP: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("%.0f", jp.getXp()), NamedTextColor.GREEN)));
        lore.add(Component.text("To Next Level: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("%.0f XP", xpNeeded), NamedTextColor.YELLOW)));
        lore.add(Component.text("Total Earned: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(plugin.getVaultHook().format(jp.getTotalEarned()), NamedTextColor.GREEN)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void buildXpBar() {
        double xpBase = plugin.getJobManager().getConfig().getXpBase();
        double xpExp  = plugin.getJobManager().getConfig().getXpExponent();
        double xpThis = jp.getLevel() <= 1 ? 0 : JobLevelCalculator.getXpRequired(jp.getLevel(), xpBase, xpExp);
        double xpNext = JobLevelCalculator.getXpRequired(jp.getLevel() + 1, xpBase, xpExp);
        double ratio  = (xpNext <= xpThis) ? 1.0 : (jp.getXp() - xpThis) / (xpNext - xpThis);
        ratio = Math.max(0, Math.min(1, ratio));

        int filled = (int) Math.round(ratio * XP_BAR_LENGTH);
        for (int i = 0; i < XP_BAR_LENGTH; i++) {
            Material mat = i < filled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            ItemStack pane = new ItemStack(mat);
            var meta = pane.getItemMeta();
            meta.displayName(Component.text(
                    String.format("XP: %.0f / %.0f", jp.getXp(), xpNext), NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            pane.setItemMeta(meta);
            inventory.setItem(XP_BAR_START + i, pane);
        }
    }

    private ItemStack buildActionsItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Top Pay Actions", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        int shown = 0;
        outer:
        for (Map.Entry<JobAction, Map<String, JobActionEntry>> actionEntry : job.getActions().entrySet()) {
            for (Map.Entry<String, JobActionEntry> matEntry : actionEntry.getValue().entrySet()) {
                if (shown++ >= 8) break outer;
                JobActionEntry e = matEntry.getValue();
                lore.add(Component.text(actionEntry.getKey().name() + " " + matEntry.getKey()
                        + ": ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(plugin.getVaultHook().format(e.getMoney()), NamedTextColor.GREEN))
                        .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.format("%.1f XP", e.getXp()), NamedTextColor.AQUA)));
            }
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

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        var meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
