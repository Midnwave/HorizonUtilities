package com.blockforge.horizonutilities.jobs.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.quests.QuestDefinition;
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

/**
 * Multi-page admin GUI for editing quest definitions.
 *
 * <ul>
 *   <li>Page 1 – Job selector (click a job to view its quests)</li>
 *   <li>Page 2 – Quest list for the selected job, with Add/Delete buttons</li>
 *   <li>Page 3 – Quest detail viewer (read-only; editing via chat prompts)</li>
 * </ul>
 *
 * Navigation state is stored per player in the static {@code states} map and
 * cleaned up by {@link JobsGUIListener} on inventory close.
 */
public class QuestEditorGUI implements InventoryHolder {

    public enum Page { JOB_SELECT, QUEST_LIST, QUEST_DETAIL }

    public static class EditorState {
        public Page page = Page.JOB_SELECT;
        public String selectedJobId;
        public QuestDefinition selectedQuest;
    }

    private static final int SIZE = 54;
    private static final int CLOSE_SLOT = 49;
    private static final int BACK_SLOT  = 45;
    private static final int ADD_SLOT   = 53;

    private final HorizonUtilitiesPlugin plugin;
    private final Player viewer;
    private final EditorState state;
    private Inventory inventory;

    public QuestEditorGUI(HorizonUtilitiesPlugin plugin, Player viewer, EditorState state) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.state  = state;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public EditorState getState() { return state; }

    public void open() {
        String title = switch (state.page) {
            case JOB_SELECT   -> "Quest Editor - Select Job";
            case QUEST_LIST   -> "Quest Editor - " + (state.selectedJobId != null ? state.selectedJobId : "Quests");
            case QUEST_DETAIL -> "Quest Editor - Quest Detail";
        };
        inventory = Bukkit.createInventory(this, SIZE,
                Component.text(title, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        populate();
        viewer.openInventory(inventory);
    }

    private void populate() {
        ItemStack pane = pane();
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, pane);

        switch (state.page) {
            case JOB_SELECT   -> populateJobSelect();
            case QUEST_LIST   -> populateQuestList();
            case QUEST_DETAIL -> populateQuestDetail();
        }

        inventory.setItem(CLOSE_SLOT, closeButton());
        if (state.page != Page.JOB_SELECT) inventory.setItem(BACK_SLOT, backButton());
    }

    // -------------------------------------------------------------------------
    // Page 1 – Job selector
    // -------------------------------------------------------------------------

    private void populateJobSelect() {
        Collection<Job> jobs = plugin.getJobManager().getAllJobs();
        int slot = 10;
        for (Job job : jobs) {
            if (slot >= CLOSE_SLOT) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }
            inventory.setItem(slot++, buildJobSelectorItem(job));
        }
    }

    private ItemStack buildJobSelectorItem(Job job) {
        ItemStack item = new ItemStack(job.getIcon());
        var meta = item.getItemMeta();
        meta.displayName(Component.text(job.getDisplayName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click to manage quests", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Page 2 – Quest list
    // -------------------------------------------------------------------------

    private void populateQuestList() {
        // Load quest definitions for the selected job directly from storage.
        List<QuestDefinition> definitions = loadDefinitions(state.selectedJobId);

        int slot = 10;
        for (QuestDefinition def : definitions) {
            if (slot >= CLOSE_SLOT) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }
            inventory.setItem(slot++, buildQuestDefItem(def));
        }

        // Add button
        inventory.setItem(ADD_SLOT, addButton());
    }

    private List<QuestDefinition> loadDefinitions(String jobId) {
        // Delegate to quest storage via the quest manager's storage (package-private workaround)
        // We reach it via JobManager -> QuestManager is not directly accessible,
        // so we use the leaderboard/storage path. For a clean approach we expose
        // it in JobManager.
        try {
            // Reflective fallback: works because QuestStorageManager is in the same plugin classloader
            var field = plugin.getJobManager().getQuestManager().getClass()
                    .getDeclaredField("storage");
            field.setAccessible(true);
            var storage = (com.blockforge.horizonutilities.jobs.quests.QuestStorageManager) field.get(
                    plugin.getJobManager().getQuestManager());
            return storage.getAllQuestDefinitions(jobId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private ItemStack buildQuestDefItem(QuestDefinition def) {
        ItemStack item = new ItemStack(def.isEnabled() ? Material.PAPER : Material.GRAY_DYE);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(def.getQuestId(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(def.getDescription(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Action: " + def.getActionType().name(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Amount: " + def.getTargetAmount(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Reward: " + plugin.getVaultHook().format(def.getRewardMoney())
                + " / " + String.format("%.0f XP", def.getRewardXp()), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Left-click: view detail", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right-click: delete", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Page 3 – Quest detail
    // -------------------------------------------------------------------------

    private void populateQuestDetail() {
        if (state.selectedQuest == null) return;
        QuestDefinition def = state.selectedQuest;
        ItemStack item = new ItemStack(Material.BOOK);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(def.getQuestId(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Description: " + def.getDescription(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Job: " + def.getJobId(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Action: " + def.getActionType().name(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Material: " + (def.getTargetMaterial() != null ? def.getTargetMaterial() : "ANY"),
                NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Amount: " + def.getTargetAmount(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Reward Money: " + plugin.getVaultHook().format(def.getRewardMoney()),
                NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Reward XP: " + String.format("%.0f", def.getRewardXp()),
                NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Weight: " + def.getWeight(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Enabled: " + def.isEnabled(), def.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        inventory.setItem(22, item);
    }

    // -------------------------------------------------------------------------
    // Shared buttons
    // -------------------------------------------------------------------------

    private ItemStack addButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Add New Quest", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Back", NamedTextColor.YELLOW)
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
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        var meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
