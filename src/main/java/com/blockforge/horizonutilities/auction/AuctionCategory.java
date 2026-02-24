package com.blockforge.horizonutilities.auction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public class AuctionCategory {

    public static String detect(ItemStack item) {
        Material mat = item.getType();

        if (item.getItemMeta() instanceof EnchantmentStorageMeta) return "Enchanted Books";

        if (isWeapon(mat)) return "Weapons";
        if (isArmor(mat)) return "Armor";
        if (isTool(mat)) return "Tools";
        if (isConsumable(mat)) return "Consumables";
        if (mat.isBlock()) return "Blocks";

        return "Misc";
    }

    private static boolean isWeapon(Material mat) {
        String name = mat.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || mat == Material.BOW
                || mat == Material.CROSSBOW || mat == Material.TRIDENT || mat == Material.MACE;
    }

    private static boolean isArmor(Material mat) {
        String name = mat.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || mat == Material.SHIELD || mat == Material.ELYTRA
                || name.endsWith("_HORSE_ARMOR");
    }

    private static boolean isTool(Material mat) {
        String name = mat.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE") || mat == Material.FISHING_ROD
                || mat == Material.SHEARS || mat == Material.FLINT_AND_STEEL
                || mat == Material.BRUSH || mat == Material.SPYGLASS;
    }

    private static boolean isConsumable(Material mat) {
        return mat.isEdible() || mat.name().contains("POTION")
                || mat == Material.GOLDEN_APPLE || mat == Material.ENCHANTED_GOLDEN_APPLE
                || mat == Material.MILK_BUCKET || mat == Material.HONEY_BOTTLE;
    }
}
