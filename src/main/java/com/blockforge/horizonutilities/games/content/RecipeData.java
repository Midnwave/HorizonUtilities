package com.blockforge.horizonutilities.games.content;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RecipeData {

    public record RecipeEntry(ItemStack result, ItemStack[] grid) {}

    private static final List<RecipeEntry> recipes = new ArrayList<>();

    public static void loadRecipes() {
        recipes.clear();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (!(recipe instanceof ShapedRecipe shaped)) continue;

            ItemStack result = shaped.getResult();
            if (result.getType() == Material.AIR) continue;

            // build 3x3 grid
            String[] shape = shaped.getShape();
            var ingredientMap = shaped.getChoiceMap();
            ItemStack[] grid = new ItemStack[9];
            for (int row = 0; row < shape.length && row < 3; row++) {
                for (int col = 0; col < shape[row].length() && col < 3; col++) {
                    char c = shape[row].charAt(col);
                    if (c == ' ') {
                        grid[row * 3 + col] = new ItemStack(Material.AIR);
                    } else {
                        var choice = ingredientMap.get(c);
                        grid[row * 3 + col] = resolveChoice(choice);
                    }
                }
            }
            // fill remaining slots
            for (int i = 0; i < 9; i++) {
                if (grid[i] == null) grid[i] = new ItemStack(Material.AIR);
            }

            recipes.add(new RecipeEntry(result, grid));
        }
    }

    private static ItemStack resolveChoice(RecipeChoice choice) {
        if (choice == null) return new ItemStack(Material.AIR);
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            List<Material> materials = mc.getChoices();
            return materials.isEmpty() ? new ItemStack(Material.AIR) : new ItemStack(materials.getFirst());
        }
        if (choice instanceof RecipeChoice.ExactChoice ec) {
            List<ItemStack> stacks = ec.getChoices();
            return stacks.isEmpty() ? new ItemStack(Material.AIR) : stacks.getFirst().clone();
        }
        // fallback for any other RecipeChoice type
        @SuppressWarnings("deprecation")
        ItemStack stack = choice.getItemStack();
        return stack != null ? stack : new ItemStack(Material.AIR);
    }

    public static RecipeEntry randomRecipe() {
        if (recipes.isEmpty()) return null;
        return recipes.get(ThreadLocalRandom.current().nextInt(recipes.size()));
    }

    public static Component buildGridMessage(RecipeEntry entry) {
        Component msg = Component.text("What does this craft?", NamedTextColor.GOLD)
                .appendNewline();

        for (int row = 0; row < 3; row++) {
            Component rowComponent = Component.text("  ", NamedTextColor.DARK_GRAY);
            for (int col = 0; col < 3; col++) {
                ItemStack item = entry.grid()[row * 3 + col];
                if (item.getType() == Material.AIR) {
                    rowComponent = rowComponent.append(
                            Component.text("[ ]", NamedTextColor.DARK_GRAY));
                } else {
                    String name = item.getType().name().toLowerCase().replace('_', ' ');
                    rowComponent = rowComponent.append(
                            Component.text("[" + name + "]", NamedTextColor.YELLOW)
                                    .hoverEvent(item.asHoverEvent()));
                }
                if (col < 2) rowComponent = rowComponent.append(Component.text(" "));
            }
            msg = msg.append(rowComponent);
            if (row < 2) msg = msg.appendNewline();
        }

        return msg;
    }

    public static int getRecipeCount() { return recipes.size(); }
}
