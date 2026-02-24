package com.blockforge.horizonutilities.blackmarket;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlackMarketItem {

    private final String id;
    private final String displayName;
    private final Material material;
    private final double price;
    private final int amount;
    private final String category;
    private final List<String> lore;
    private final int customModelData;
    private final boolean isBreaker;
    private final List<String> breakableBlocks;
    private final int maxUses;
    private final int claimBlocks;
    private final Map<String, Integer> enchantments;

    public BlackMarketItem(String id, String displayName, Material material, double price, int amount,
                           String category, List<String> lore, int customModelData, boolean isBreaker,
                           List<String> breakableBlocks, int maxUses, int claimBlocks,
                           Map<String, Integer> enchantments) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.price = price;
        this.amount = amount;
        this.category = category;
        this.lore = lore;
        this.customModelData = customModelData;
        this.isBreaker = isBreaker;
        this.breakableBlocks = breakableBlocks;
        this.maxUses = maxUses;
        this.claimBlocks = claimBlocks;
        this.enchantments = enchantments;
    }

    /**
     * Reads a BlackMarketItem from a YAML ConfigurationSection.
     * Expected keys: material, display-name, category, price, amount,
     * lore, custom-model-data, is-breaker, breakable-blocks, max-uses,
     * claim-blocks, enchantments.
     */
    public static BlackMarketItem fromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        String materialName = section.getString("material", "STONE").toUpperCase();
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
        }

        String displayName = section.getString("display-name", id);
        double price = section.getDouble("price", 0.0);
        int amount = Math.max(1, section.getInt("amount", 1));
        String category = section.getString("category", "Misc");
        List<String> lore = new ArrayList<>(section.getStringList("lore"));
        int customModelData = section.getInt("custom-model-data", -1);
        boolean isBreaker = section.getBoolean("is-breaker", false);
        List<String> breakableBlocks = new ArrayList<>(section.getStringList("breakable-blocks"));
        int maxUses = section.getInt("max-uses", 1);
        int claimBlocks = section.getInt("claim-blocks", 0);

        Map<String, Integer> enchantments = new HashMap<>();
        ConfigurationSection enchSection = section.getConfigurationSection("enchantments");
        if (enchSection != null) {
            for (String key : enchSection.getKeys(false)) {
                enchantments.put(key.toLowerCase(), enchSection.getInt(key, 1));
            }
        }

        return new BlackMarketItem(id, displayName, material, price, amount, category, lore,
                customModelData, isBreaker, breakableBlocks, maxUses, claimBlocks, enchantments);
    }

    // --- Getters ---

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public double getPrice() { return price; }
    public int getAmount() { return amount; }
    public String getCategory() { return category; }
    public List<String> getLore() { return lore; }
    public int getCustomModelData() { return customModelData; }
    public boolean isBreaker() { return isBreaker; }
    public List<String> getBreakableBlocks() { return breakableBlocks; }
    public int getMaxUses() { return maxUses; }
    public int getClaimBlocks() { return claimBlocks; }
    public Map<String, Integer> getEnchantments() { return enchantments; }

    /** Returns a copy of this item with a new price (used by setPrice). */
    public BlackMarketItem withPrice(double newPrice) {
        return new BlackMarketItem(id, displayName, material, newPrice, amount, category, lore,
                customModelData, isBreaker, breakableBlocks, maxUses, claimBlocks, enchantments);
    }

    /** Serialize this item back to the given ConfigurationSection for saving. */
    public void toConfig(ConfigurationSection section) {
        section.set("material", material.name());
        section.set("display-name", displayName);
        section.set("category", category);
        section.set("price", price);
        section.set("amount", amount);
        if (!lore.isEmpty()) section.set("lore", lore);
        if (customModelData > 0) section.set("custom-model-data", customModelData);
        if (isBreaker) {
            section.set("is-breaker", true);
            section.set("breakable-blocks", breakableBlocks);
            section.set("max-uses", maxUses);
        }
        if (claimBlocks > 0) section.set("claim-blocks", claimBlocks);
        if (!enchantments.isEmpty()) {
            enchantments.forEach((k, v) -> section.set("enchantments." + k, v));
        }
    }
}
