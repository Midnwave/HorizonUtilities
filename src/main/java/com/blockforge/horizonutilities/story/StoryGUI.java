package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 54-slot chest GUI for the Hollow Chronicle story system.
 *
 * Row 1: Overview star + 7 stage slots + lore collection icon
 * Row 2: Current chapter info + objective summary
 * Row 3-4: Main path objectives (left) + side quests (right)
 * Row 5: Lore book collection
 * Row 6: Filler + close button
 */
public class StoryGUI implements InventoryHolder {

    static final int SIZE = 54;
    private static final int SLOT_CLOSE = 49;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager manager;
    private final Player player;
    private final Inventory inventory;

    public StoryGUI(HorizonUtilitiesPlugin plugin, Player player) {
        this.plugin = plugin;
        this.manager = plugin.getStoryManager();
        this.player = player;

        Component title = MINI.deserialize("<gradient:#6A0DAD:#8B0000>The Hollow Chronicle</gradient>");
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        build();
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void build() {
        // Fill with dark glass
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler);

        StoryManager.StoryPlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data == null) {
            inventory.setItem(22, makeItem(Material.CLOCK, "<yellow>Loading...",
                List.of("<gray>Your story data is still loading.")));
            inventory.setItem(SLOT_CLOSE, makeItem(Material.BARRIER, "<red>Close", List.of()));
            return;
        }

        StoryConfig cfg = manager.getStoryConfig();
        int currentStage = data.getCurrentStage();
        int currentChapter = data.getCurrentChapter();

        // --- Row 1: Overview + 7 Stages ---
        inventory.setItem(0, makeItem(Material.NETHER_STAR, "<dark_purple><bold>The Hollow Chronicle",
            List.of(
                "<gray>An ancient story unfolds across",
                "<gray>seven stages. Walk the path.",
                "",
                "<dark_purple>Stage: <gold>" + currentStage + " <dark_purple>Chapter: <gold>" + currentChapter
            )));

        // 7 stage slots (slots 1-7)
        for (int s = 1; s <= 7; s++) {
            StoryConfig.StageData stageData = cfg.getStage(s);
            String stageName = stageData != null ? stageData.name() : "Stage " + s;
            Material icon;
            List<String> lore = new ArrayList<>();

            if (s < currentStage) {
                // Completed
                icon = Material.LIME_DYE;
                lore.add("<green>Completed");
            } else if (s == currentStage) {
                // In progress
                icon = Material.YELLOW_DYE;
                lore.add("<yellow>In Progress — Chapter " + currentChapter);
                if (stageData != null) {
                    lore.add("<gray>" + stageData.description());
                }
            } else {
                // Locked
                icon = Material.GRAY_DYE;
                lore.add("<dark_gray>Locked");
            }

            ItemStack stageItem = makeItem(icon, "<gold>" + stageName, lore);
            if (s < currentStage) {
                ItemMeta meta = stageItem.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                stageItem.setItemMeta(meta);
            }
            inventory.setItem(s, stageItem);
        }

        // Lore collection icon (slot 8)
        int booksFound = data.getBooksFound().size();
        inventory.setItem(8, makeItem(Material.BOOK, "<light_purple>Lore Collection",
            List.of("<gray>Books discovered: <aqua>" + booksFound)));

        // --- Row 2: Chapter + Objective summary ---
        if (currentStage > 0 && cfg.getStage(currentStage) != null) {
            StoryConfig.StageData stage = cfg.getStage(currentStage);
            StoryConfig.ChapterData chapter = stage.chapters().get(currentChapter);

            // Chapter info (slot 10)
            if (chapter != null) {
                inventory.setItem(10, makeItem(Material.PAPER, "<gold>" + chapter.name(),
                    List.of("<gray>" + chapter.description())));
            }

            // Objectives count (slot 12)
            int objDone = data.getObjectivesCompleted().size();
            inventory.setItem(12, makeItem(Material.COMPASS, "<yellow>Objectives",
                List.of("<gray>Completed: <green>" + objDone)));

            // Items collected (slot 14)
            int items = data.getItemsCollected().size();
            inventory.setItem(14, makeItem(Material.CHEST, "<gold>Items Collected",
                List.of("<gray>Found: <green>" + items)));
        }

        // --- Row 3-4: Main objectives + Side quests ---
        if (currentStage > 0 && cfg.getStage(currentStage) != null) {
            StoryConfig.StageData stage = cfg.getStage(currentStage);
            StoryConfig.ChapterData chapter = stage.chapters().get(currentChapter);

            if (chapter != null) {
                List<StoryConfig.ObjectiveData> objectives = chapter.objectives();
                for (int i = 0; i < Math.min(objectives.size(), 6); i++) {
                    StoryConfig.ObjectiveData obj = objectives.get(i);
                    boolean done = data.getObjectivesCompleted().contains(obj.id());
                    Material objMat = done ? Material.LIME_TERRACOTTA : Material.GREEN_TERRACOTTA;
                    String status = done ? "<green>Completed" : "<yellow>In Progress";
                    int slot = 18 + i; // Row 3
                    inventory.setItem(slot, makeItem(objMat, "<green>" + obj.description(),
                        List.of(status)));
                    if (done) {
                        ItemStack item = inventory.getItem(slot);
                        if (item != null) {
                            ItemMeta meta = item.getItemMeta();
                            meta.setEnchantmentGlintOverride(true);
                            item.setItemMeta(meta);
                        }
                    }
                }
            }

            // Side quests (right side of row 3-4)
            List<StoryConfig.SideQuestData> sideQuests = stage.sideQuests();
            for (int i = 0; i < Math.min(sideQuests.size(), 4); i++) {
                StoryConfig.SideQuestData sq = sideQuests.get(i);
                boolean done = data.getSideQuestsCompleted().contains(sq.id());
                Material sqMat = done ? Material.CYAN_TERRACOTTA : Material.LIGHT_BLUE_TERRACOTTA;
                String status = done ? "<green>Completed" : "<aqua>Side Quest";
                int slot = 22 + i; // Row 3, right side
                inventory.setItem(slot, makeItem(sqMat, "<aqua>" + sq.name(),
                    List.of("<gray>" + sq.description(), "", status)));
                if (done) {
                    ItemStack item = inventory.getItem(slot);
                    if (item != null) {
                        ItemMeta meta = item.getItemMeta();
                        meta.setEnchantmentGlintOverride(true);
                        item.setItemMeta(meta);
                    }
                }
            }
        }

        // --- Row 5: Lore books ---
        String[] bookIds = {"book1", "book2", "book3", "book4", "book5", "book6"};
        String[] bookNames = {
            "The Horizon Creed", "The Last Departure", "Day 1,447",
            "What Came Back", "The Last Vigil", "To the Next Walker"
        };
        for (int i = 0; i < bookIds.length; i++) {
            boolean found = data.getBooksFound().contains(bookIds[i]);
            int slot = 37 + i; // Row 5, slots 37-42
            if (found) {
                inventory.setItem(slot, makeItem(Material.ENCHANTED_BOOK,
                    "<light_purple>" + bookNames[i],
                    List.of("<green>Discovered")));
            } else {
                inventory.setItem(slot, makeItem(Material.GRAY_DYE,
                    "<dark_gray>???",
                    List.of("<dark_gray>Not yet discovered")));
            }
        }

        // --- Row 6: Close button ---
        inventory.setItem(SLOT_CLOSE, makeItem(Material.BARRIER, "<red>Close", List.of()));
    }

    public void handleClick(int slot, Player clicker) {
        if (slot == SLOT_CLOSE) {
            clicker.closeInventory();
        }
        // Stage slots and other interactions can be expanded later
    }

    // --- Item helpers ---

    private ItemStack pane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String miniName, List<String> miniLore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI.deserialize(miniName).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : miniLore) {
            lore.add(MINI.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
