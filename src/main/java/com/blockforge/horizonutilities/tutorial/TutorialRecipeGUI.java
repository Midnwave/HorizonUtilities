package com.blockforge.horizonutilities.tutorial;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.tutorial.TutorialManager.RecipeData;
import com.blockforge.horizonutilities.tutorial.TutorialManager.RecipeIngredient;
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

import java.util.List;

/**
 * Displays a crafting recipe in a 3x3 grid layout.
 *
 * Layout (54 slots):
 * Row 1: gray border
 * Row 2: [gray] [A ] [B ] [C ] [gray] [arrow] [gray] [RESULT] [gray]
 * Row 3: [gray] [D ] [E ] [F ] [gray] [gray]  [gray] [gray]   [gray]
 * Row 4: [gray] [G ] [H ] [I ] [gray] [gray]  [gray] [gray]   [gray]
 * Row 5: gray border
 * Row 6: [gray] ... [BACK] ... [gray]
 */
public class TutorialRecipeGUI implements InventoryHolder {

    static final int SIZE = 54;
    static final int SLOT_BACK = 49;

    // 3x3 crafting grid slots (row 2-4, columns 1-3)
    private static final int[] GRID_SLOTS = {
            10, 11, 12, // row 2
            19, 20, 21, // row 3
            28, 29, 30  // row 4
    };
    private static final int SLOT_ARROW = 14;
    private static final int SLOT_RESULT = 16;

    private final HorizonUtilitiesPlugin plugin;
    private final TutorialManager manager;
    private final Player player;
    private final String categoryId;
    private final int returnPage;
    private final RecipeData recipe;
    private final Inventory inventory;

    public TutorialRecipeGUI(HorizonUtilitiesPlugin plugin, Player player,
                             TutorialManager manager, String categoryId,
                             int returnPage, RecipeData recipe) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.categoryId = categoryId;
        this.returnPage = returnPage;
        this.recipe = recipe;

        String resultName = recipe.resultName != null ? recipe.resultName : "Recipe";
        Component title = manager.parseMiniMessage("<dark_purple>Recipe: " + resultName);
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        build();
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void build() {
        // Fill everything with gray glass
        ItemStack filler = makePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler);

        if ("shaped".equalsIgnoreCase(recipe.type)) {
            buildShaped();
        } else {
            buildShapeless();
        }

        // Arrow
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrow.getItemMeta();
        arrowMeta.displayName(Component.text("\u2192", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(arrowMeta);
        inventory.setItem(SLOT_ARROW, arrow);

        // Result
        if (recipe.resultMaterial != null) {
            ItemStack result = new ItemStack(recipe.resultMaterial);
            ItemMeta rMeta = result.getItemMeta();
            rMeta.displayName(manager.parseMiniMessage(recipe.resultName != null ? recipe.resultName : "Result")
                    .decoration(TextDecoration.ITALIC, false));
            rMeta.lore(List.of(
                    Component.text("Result", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            rMeta.setEnchantmentGlintOverride(true);
            result.setItemMeta(rMeta);
            inventory.setItem(SLOT_RESULT, result);
        }

        // Back button
        ItemStack back = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(SLOT_BACK, back);
    }

    private void buildShaped() {
        if (recipe.pattern == null) return;

        // Parse pattern into a flat 3x3 grid of characters
        char[] grid = new char[9];
        java.util.Arrays.fill(grid, ' ');

        for (int row = 0; row < recipe.pattern.size() && row < 3; row++) {
            String line = recipe.pattern.get(row);
            for (int col = 0; col < line.length() && col < 3; col++) {
                grid[row * 3 + col] = line.charAt(col);
            }
        }

        for (int i = 0; i < 9; i++) {
            char key = grid[i];
            if (key == ' ') continue;
            RecipeIngredient ing = recipe.ingredients.get(key);
            if (ing == null || ing.material == null) continue;

            ItemStack item = new ItemStack(ing.material);
            ItemMeta meta = item.getItemMeta();
            if (ing.name != null && !ing.name.isEmpty()) {
                meta.displayName(manager.parseMiniMessage(ing.name)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(List.of(
                    Component.text("Ingredient", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            inventory.setItem(GRID_SLOTS[i], item);
        }
    }

    private void buildShapeless() {
        // Place ingredients sequentially in the grid
        int idx = 0;
        for (RecipeIngredient ing : recipe.ingredients.values()) {
            if (idx >= 9 || ing == null || ing.material == null) break;
            ItemStack item = new ItemStack(ing.material);
            ItemMeta meta = item.getItemMeta();
            if (ing.name != null && !ing.name.isEmpty()) {
                meta.displayName(manager.parseMiniMessage(ing.name)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(List.of(
                    Component.text("Ingredient (shapeless)", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            inventory.setItem(GRID_SLOTS[idx++], item);
        }
    }

    public void handleClick(int slot, Player clicker) {
        if (slot == SLOT_BACK) {
            new TutorialPageGUI(plugin, clicker, manager, categoryId, returnPage).open();
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
