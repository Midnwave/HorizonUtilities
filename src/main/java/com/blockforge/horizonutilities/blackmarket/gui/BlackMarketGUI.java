package com.blockforge.horizonutilities.blackmarket.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.blackmarket.BlackMarketItem;
import com.blockforge.horizonutilities.blackmarket.BlackMarketManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 6-row (54-slot) chest GUI for the Black Market.
 *
 * Layout:
 *   Row 1 (0-8)  : Category tabs — slot 0 = "All", then one per category
 *   Rows 2-5 (9-44) : Item display grid (36 slots)
 *   Row 6 (45-53): Navigation — 45=prev, 49=page info, 51=close, 53=next
 */
public class BlackMarketGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = 36;

    // Row 6 control slots
    private static final int SLOT_PREV  = 45;
    private static final int SLOT_INFO  = 49;
    private static final int SLOT_CLOSE = 51;
    private static final int SLOT_NEXT  = 53;

    private final HorizonUtilitiesPlugin plugin;
    private final Player player;
    private final BlackMarketManager manager;
    private final MiniMessage miniMessage;

    private String currentCategory = "All";
    private int currentPage = 0;
    private Inventory inventory;

    // Tracks itemId per display slot so clicks can be resolved
    private final String[] slotItemIds = new String[SIZE];

    public BlackMarketGUI(HorizonUtilitiesPlugin plugin, Player player, BlackMarketManager manager) {
        this.plugin = plugin;
        this.player = player;
        this.manager = manager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // -------------------------------------------------------------------------
    // Open / Build
    // -------------------------------------------------------------------------

    public void open() {
        Component title = miniMessage.deserialize(
                plugin.getDatabaseManager() != null
                        ? getConfigTitle()
                        : "<gradient:#7B2FF7:#FF5733>Black Market</gradient>");
        inventory = Bukkit.createInventory(this, SIZE, title);
        populate();
        player.openInventory(inventory);
    }

    private String getConfigTitle() {
        try {
            org.bukkit.configuration.file.YamlConfiguration cfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.File(plugin.getDataFolder(), "black-market.yml"));
            String t = cfg.getString("gui-title");
            return t != null ? t : "<gradient:#7B2FF7:#FF5733>Black Market</gradient>";
        } catch (Exception e) {
            return "<gradient:#7B2FF7:#FF5733>Black Market</gradient>";
        }
    }

    private void populate() {
        inventory.clear();
        java.util.Arrays.fill(slotItemIds, null);

        buildCategoryTabs();
        buildItemGrid();
        buildNavigation();
    }

    // -------------------------------------------------------------------------
    // Category tabs (Row 1, slots 0-8)
    // -------------------------------------------------------------------------

    private void buildCategoryTabs() {
        // Slot 0 = "All"
        inventory.setItem(0, makeCategoryTab("All", Material.NETHER_STAR));

        List<String> categories = manager.getCategories();
        int slot = 1;
        for (String cat : categories) {
            if (slot > 8) break;
            Material icon = getCategoryIcon(cat);
            inventory.setItem(slot, makeCategoryTab(cat, icon));
            slot++;
        }

        // Fill remaining tab slots with glass panes
        for (int s = slot; s <= 8; s++) {
            inventory.setItem(s, makeFillerPane(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private ItemStack makeCategoryTab(String category, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        boolean active = category.equalsIgnoreCase(currentCategory);
        Component name = Component.text(category, active ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, active);

        meta.displayName(name);
        if (active) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private Material getCategoryIcon(String category) {
        // Try to read from config first
        try {
            org.bukkit.configuration.file.YamlConfiguration cfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.File(plugin.getDataFolder(), "black-market.yml"));
            String iconName = cfg.getString("categories." + category + ".icon");
            if (iconName != null) {
                try { return Material.valueOf(iconName.toUpperCase()); } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}

        // Fallbacks by common name
        return switch (category.toLowerCase()) {
            case "blocks"       -> Material.GRASS_BLOCK;
            case "tools"        -> Material.DIAMOND_PICKAXE;
            case "weapons"      -> Material.DIAMOND_SWORD;
            case "armor"        -> Material.DIAMOND_CHESTPLATE;
            case "special"      -> Material.NETHER_STAR;
            case "claim blocks" -> Material.GOLD_BLOCK;
            case "consumables"  -> Material.GOLDEN_APPLE;
            default             -> Material.CHEST;
        };
    }

    // -------------------------------------------------------------------------
    // Item grid (Rows 2-5, slots 9-44)
    // -------------------------------------------------------------------------

    private void buildItemGrid() {
        List<BlackMarketItem> displayed = manager.getItemsByCategory(currentCategory);
        int totalItems = displayed.size();
        int start = currentPage * ITEMS_PER_PAGE;

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int invSlot = 9 + i;
            int itemIndex = start + i;
            if (itemIndex < totalItems) {
                BlackMarketItem bmItem = displayed.get(itemIndex);
                inventory.setItem(invSlot, buildDisplayItem(bmItem));
                slotItemIds[invSlot] = bmItem.getId();
            }
            // Empty slots are left null (air)
        }
    }

    private ItemStack buildDisplayItem(BlackMarketItem bmItem) {
        ItemStack item = new ItemStack(bmItem.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Display name
        meta.displayName(miniMessage.deserialize(bmItem.getDisplayName())
                .decoration(TextDecoration.ITALIC, false));

        // Build lore
        List<Component> lore = new ArrayList<>();

        // Item description lines from config lore (skip <uses> placeholder lines for display)
        for (String line : bmItem.getLore()) {
            if (!line.contains("<uses>")) {
                lore.add(miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
        }

        if (!bmItem.getLore().isEmpty()) lore.add(Component.empty());

        // Price
        lore.add(miniMessage.deserialize(
                "<yellow>Price: <green>" + plugin.getVaultHook().format(bmItem.getPrice()))
                .decoration(TextDecoration.ITALIC, false));

        // Special annotations
        if (bmItem.isBreaker()) {
            lore.add(miniMessage.deserialize(
                    "<gray>Uses: <white>" + bmItem.getMaxUses())
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (bmItem.getClaimBlocks() > 0) {
            lore.add(miniMessage.deserialize(
                    "<gray>Grants: <white>" + bmItem.getClaimBlocks() + " claim blocks")
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(miniMessage.deserialize("<yellow>Click to purchase")
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        if (bmItem.getCustomModelData() > 0) meta.setCustomModelData(bmItem.getCustomModelData());

        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Navigation row (Row 6, slots 45-53)
    // -------------------------------------------------------------------------

    private void buildNavigation() {
        List<BlackMarketItem> displayed = manager.getItemsByCategory(currentCategory);
        int totalPages = Math.max(1, (int) Math.ceil(displayed.size() / (double) ITEMS_PER_PAGE));

        // Prev arrow
        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, makeButton(Material.ARROW,
                    Component.text("Previous Page", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        } else {
            inventory.setItem(SLOT_PREV, makeFillerPane(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Page info
        ItemStack info = makeButton(Material.PAPER,
                Component.text("Page " + (currentPage + 1) + " / " + totalPages, NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
        inventory.setItem(SLOT_INFO, info);

        // Close
        inventory.setItem(SLOT_CLOSE, makeButton(Material.BARRIER,
                Component.text("Close", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));

        // Next arrow
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, makeButton(Material.ARROW,
                    Component.text("Next Page", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        } else {
            inventory.setItem(SLOT_NEXT, makeFillerPane(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    public void handleClick(int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        // Row 1: category tabs (slots 0-8)
        if (slot <= 8) {
            if (slot == 0) {
                setCategory("All");
            } else {
                List<String> cats = manager.getCategories();
                int catIndex = slot - 1;
                if (catIndex < cats.size()) {
                    setCategory(cats.get(catIndex));
                }
            }
            return;
        }

        // Item grid (slots 9-44)
        if (slot >= 9 && slot <= 44) {
            String itemId = slotItemIds[slot];
            if (itemId != null) {
                manager.purchaseItem(player, itemId);
                // Refresh GUI after purchase to reflect any inventory changes
                populate();
            }
            return;
        }

        // Navigation row
        switch (slot) {
            case SLOT_PREV  -> prevPage();
            case SLOT_NEXT  -> nextPage();
            case SLOT_CLOSE -> player.closeInventory();
            default -> {}
        }
    }

    public void setCategory(String category) {
        this.currentCategory = category;
        this.currentPage = 0;
        populate();
    }

    public void nextPage() {
        List<BlackMarketItem> displayed = manager.getItemsByCategory(currentCategory);
        int totalPages = Math.max(1, (int) Math.ceil(displayed.size() / (double) ITEMS_PER_PAGE));
        if (currentPage < totalPages - 1) {
            currentPage++;
            populate();
        }
    }

    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            populate();
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private ItemStack makeButton(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeFillerPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getCurrentCategory() { return currentCategory; }
    public int getCurrentPage() { return currentPage; }
    public Player getPlayer() { return player; }
}
