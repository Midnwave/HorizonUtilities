package com.blockforge.horizonutilities.tutorial;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.tutorial.TutorialManager.CategoryData;
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
 * Main tutorial menu — 54 slots with 14 category icons.
 */
public class TutorialGUI implements InventoryHolder {

    static final int SIZE = 54;
    static final int SLOT_WELCOME = 31;
    static final int SLOT_PROGRESS = 40;
    static final int SLOT_CLOSE = 49;

    private final HorizonUtilitiesPlugin plugin;
    private final TutorialManager manager;
    private final Player player;
    private final Inventory inventory;

    // Maps slot → categoryId for click routing
    private final Map<Integer, String> slotToCategory = new HashMap<>();

    public TutorialGUI(HorizonUtilitiesPlugin plugin, Player player, TutorialManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;

        Component title = manager.parseMiniMessage("<gradient:#7B2FF7:#FF5733>Server Guide</gradient>");
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        build();
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void build() {
        // Fill with gray glass
        ItemStack filler = makePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler);

        Set<String> visited = manager.getVisitedCategories(player.getUniqueId());

        // Place category icons
        for (CategoryData cat : manager.getCategories().values()) {
            Material icon = cat.iconMaterial != null ? cat.iconMaterial : Material.BOOK;
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(manager.parseMiniMessage(cat.displayName)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            if (cat.lore != null) {
                for (String line : cat.lore) {
                    lore.add(manager.parseMiniMessage(line).decoration(TextDecoration.ITALIC, false));
                }
            }
            lore.add(Component.empty());
            if (visited.contains(cat.id)) {
                lore.add(Component.text("\u2714 Viewed", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                meta.setEnchantmentGlintOverride(true);
            } else {
                lore.add(Component.text("Click to learn more!", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(cat.slot, item);
            slotToCategory.put(cat.slot, cat.id);
        }

        // Welcome item
        ItemStack welcome = new ItemStack(Material.NETHER_STAR);
        ItemMeta wMeta = welcome.getItemMeta();
        wMeta.displayName(manager.parseMiniMessage("<gradient:#7B2FF7:#FF5733>Welcome to Horizon SMP!</gradient>")
                .decoration(TextDecoration.ITALIC, false));
        wMeta.lore(List.of(
                Component.text("Browse the categories above to", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("learn about all server features.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("View all sections to earn rewards!", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        welcome.setItemMeta(wMeta);
        inventory.setItem(SLOT_WELCOME, welcome);

        // Progress item
        int total = manager.getCategoryCount();
        int viewedCount = visited.size();
        boolean allViewed = viewedCount >= total;
        boolean claimed = manager.hasClaimedReward(player.getUniqueId());

        if (allViewed && !claimed) {
            ItemStack reward = new ItemStack(Material.LIME_DYE);
            ItemMeta rMeta = reward.getItemMeta();
            rMeta.displayName(Component.text("Claim Your Rewards!", NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            rMeta.lore(List.of(
                    Component.text("You've viewed all " + total + " sections!", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Click to claim:", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("  $500 + Starter Tools + Gold Armor", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            rMeta.setEnchantmentGlintOverride(true);
            reward.setItemMeta(rMeta);
            inventory.setItem(SLOT_PROGRESS, reward);
        } else {
            ItemStack progress = new ItemStack(allViewed && claimed ? Material.WRITTEN_BOOK : Material.BOOK);
            ItemMeta pMeta = progress.getItemMeta();
            String status = claimed ? "Rewards Claimed!" : "Progress: " + viewedCount + "/" + total;
            NamedTextColor color = claimed ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            pMeta.displayName(Component.text(status, color)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> pLore = new ArrayList<>();
            pLore.add(Component.text("Sections viewed: " + viewedCount + "/" + total, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (!allViewed) {
                pLore.add(Component.empty());
                pLore.add(Component.text("View all sections to earn rewards!", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }
            pMeta.lore(pLore);
            progress.setItemMeta(pMeta);
            inventory.setItem(SLOT_PROGRESS, progress);
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

        if (slot == SLOT_PROGRESS) {
            boolean allViewed = manager.hasCompletedAll(clicker.getUniqueId());
            boolean claimed = manager.hasClaimedReward(clicker.getUniqueId());
            if (allViewed && !claimed) {
                manager.grantCompletionReward(clicker);
                // Rebuild to show updated state
                new TutorialGUI(plugin, clicker, manager).open();
            }
            return;
        }

        String catId = slotToCategory.get(slot);
        if (catId != null) {
            new TutorialPageGUI(plugin, clicker, manager, catId, 1).open();
        }
    }

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
