package com.blockforge.horizonutilities.jobs.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.quests.DailyQuest;
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

/**
 * 3-row GUI showing a player's 3 daily quests.
 * Each quest appears as a paper item with progress, rewards, and completion state.
 */
public class JobQuestsGUI implements InventoryHolder {

    private static final int SIZE = 27;
    // Three quest slots centred in the top row
    private static final int[] QUEST_SLOTS = {11, 13, 15};
    private static final int CLOSE_SLOT = 22;

    private final HorizonUtilitiesPlugin plugin;
    private final Player viewer;
    private Inventory inventory;

    public JobQuestsGUI(HorizonUtilitiesPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        inventory = Bukkit.createInventory(this, SIZE,
                Component.text("Daily Quests", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, false));
        populate();
        viewer.openInventory(inventory);
    }

    private void populate() {
        // Fill with panes
        ItemStack pane = pane();
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, pane);

        // Load quests (already assigned or freshly assigned)
        List<DailyQuest> quests = plugin.getJobManager().getQuestManager().getOrAssignQuests(viewer);

        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            DailyQuest quest = i < quests.size() ? quests.get(i) : null;
            inventory.setItem(QUEST_SLOTS[i], quest == null ? emptyQuest() : buildQuestItem(quest));
        }

        inventory.setItem(CLOSE_SLOT, closeButton());
    }

    private ItemStack buildQuestItem(DailyQuest quest) {
        Material mat = quest.isCompleted() ? Material.LIME_DYE : Material.PAPER;
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();

        NamedTextColor titleColour = quest.isCompleted() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        meta.displayName(Component.text(quest.getDescription(), titleColour)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Job: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(quest.getJobId(), NamedTextColor.AQUA)));
        lore.add(Component.text("Action: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(quest.getTargetType().name(), NamedTextColor.WHITE)));
        if (quest.getTargetMaterial() != null) {
            lore.add(Component.text("Material: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(quest.getTargetMaterial(), NamedTextColor.WHITE)));
        }
        lore.add(Component.empty());

        // Progress bar
        String progress = quest.getCurrentProgress() + " / " + quest.getTargetAmount();
        NamedTextColor progressColour = quest.isCompleted() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        lore.add(Component.text("Progress: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(progress, progressColour)));

        lore.add(Component.empty());
        lore.add(Component.text("Reward: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(plugin.getVaultHook().format(quest.getRewardMoney()), NamedTextColor.GREEN))
                .append(Component.text(" + ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.0f XP", quest.getRewardXp()), NamedTextColor.AQUA)));

        if (quest.isCompleted()) {
            lore.add(Component.empty());
            lore.add(Component.text("COMPLETED", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack emptyQuest() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("No quest available", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
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
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
