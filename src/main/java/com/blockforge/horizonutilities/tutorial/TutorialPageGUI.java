package com.blockforge.horizonutilities.tutorial;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.tutorial.TutorialManager.CategoryData;
import com.blockforge.horizonutilities.tutorial.TutorialManager.PageData;
import com.blockforge.horizonutilities.tutorial.TutorialManager.PageItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Sub-page GUI showing the content items for a single category page.
 */
public class TutorialPageGUI implements InventoryHolder {

    static final int SIZE = 54;
    static final int SLOT_BACK = 45;
    static final int SLOT_PREV = 47;
    static final int SLOT_PAGE_INFO = 49;
    static final int SLOT_NEXT = 51;
    static final int SLOT_CLOSE = 53;

    private final HorizonUtilitiesPlugin plugin;
    private final TutorialManager manager;
    private final Player player;
    private final String categoryId;
    private final int page;
    private final Inventory inventory;

    // Maps slot → PageItem for recipe click detection
    private final Map<Integer, PageItem> slotToItem = new HashMap<>();

    public TutorialPageGUI(HorizonUtilitiesPlugin plugin, Player player,
                           TutorialManager manager, String categoryId, int page) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.categoryId = categoryId;
        this.page = page;

        CategoryData cat = manager.getCategory(categoryId);
        int totalPages = cat != null ? cat.pages.size() : 1;
        String titleStr = cat != null && cat.pages.containsKey(page)
                ? cat.pages.get(page).title : categoryId;
        // Append page number to title
        String pageStr = totalPages > 1 ? " <dark_gray>(" + page + "/" + totalPages + ")" : "";
        Component title = manager.parseMiniMessage(titleStr + pageStr);
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        build();
    }

    public void open() {
        // Mark this category as visited
        manager.markCategoryVisited(player.getUniqueId(), categoryId);
        player.openInventory(inventory);
    }

    private void build() {
        CategoryData cat = manager.getCategory(categoryId);
        if (cat == null) return;

        Material glassMat = cat.glassPaneMaterial != null ? cat.glassPaneMaterial : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack catPane = makePane(glassMat);
        ItemStack grayPane = makePane(Material.GRAY_STAINED_GLASS_PANE);

        // Row 1: category-colored border
        for (int i = 0; i < 9; i++) inventory.setItem(i, catPane);
        // Rows 2-5: fill with category glass (content items overwrite)
        for (int i = 9; i < 45; i++) inventory.setItem(i, catPane);
        // Row 6: navigation row
        for (int i = 45; i < 54; i++) inventory.setItem(i, grayPane);

        // Place content items
        PageData pageData = cat.pages.get(page);
        if (pageData != null) {
            for (PageItem pi : pageData.items) {
                Material mat = pi.material != null ? pi.material : Material.PAPER;
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();

                meta.displayName(manager.parseMiniMessage(pi.name)
                        .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                if (pi.lore != null) {
                    for (String line : pi.lore) {
                        lore.add(manager.parseMiniMessage(line)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }
                if (pi.recipe != null) {
                    lore.add(Component.empty());
                    lore.add(Component.text("\u25B6 Click to view recipe!", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
                item.setItemMeta(meta);

                if (pi.slot >= 0 && pi.slot < SIZE) {
                    inventory.setItem(pi.slot, item);
                    slotToItem.put(pi.slot, pi);
                }
            }
        }

        // Back button
        ItemStack back = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back to Menu", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(SLOT_BACK, back);

        // Page info
        int totalPages = cat.pages.size();
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta piMeta = pageInfo.getItemMeta();
        piMeta.displayName(Component.text("Page " + page + "/" + totalPages, NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        pageInfo.setItemMeta(piMeta);
        inventory.setItem(SLOT_PAGE_INFO, pageInfo);

        // Previous page
        if (page > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.displayName(Component.text("Previous Page", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            prev.setItemMeta(prevMeta);
            inventory.setItem(SLOT_PREV, prev);
        }

        // Next page
        if (cat.pages.containsKey(page + 1)) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.displayName(Component.text("Next Page", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            next.setItemMeta(nextMeta);
            inventory.setItem(SLOT_NEXT, next);
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        cMeta.displayName(Component.text("Close", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(cMeta);
        inventory.setItem(SLOT_CLOSE, close);
    }

    public void handleClick(int slot, Player clicker) {
        if (slot == SLOT_CLOSE) {
            clicker.closeInventory();
            return;
        }
        if (slot == SLOT_BACK) {
            new TutorialGUI(plugin, clicker, manager).open();
            return;
        }
        CategoryData cat = manager.getCategory(categoryId);
        if (cat == null) return;

        if (slot == SLOT_PREV && page > 1) {
            new TutorialPageGUI(plugin, clicker, manager, categoryId, page - 1).open();
            return;
        }
        if (slot == SLOT_NEXT && cat.pages.containsKey(page + 1)) {
            new TutorialPageGUI(plugin, clicker, manager, categoryId, page + 1).open();
            return;
        }

        // Check for recipe click
        PageItem pi = slotToItem.get(slot);
        if (pi != null && pi.recipe != null) {
            new TutorialRecipeGUI(plugin, clicker, manager, categoryId, page, pi.recipe).open();
        }
    }

    public String getCategoryId() { return categoryId; }
    public int getPage() { return page; }

    private static ItemStack makePane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
