package com.blockforge.horizonutilities.blackmarket;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BreakerItemFactory {

    public static final NamespacedKey USES_KEY;
    public static final NamespacedKey MAX_USES_KEY;
    public static final NamespacedKey BREAKABLE_KEY;

    static {
        HorizonUtilitiesPlugin plugin = HorizonUtilitiesPlugin.getInstance();
        USES_KEY = new NamespacedKey(plugin, "breaker_uses");
        MAX_USES_KEY = new NamespacedKey(plugin, "breaker_max_uses");
        BREAKABLE_KEY = new NamespacedKey(plugin, "breaker_targets");
    }

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private BreakerItemFactory() {}

    /**
     * Creates a fully configured breaker pickaxe ItemStack from a BlackMarketItem config.
     */
    public static ItemStack createBreakerItem(BlackMarketItem config) {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Display name (MiniMessage, italic disabled)
        Component name = MINI.deserialize(config.getDisplayName()).decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);

        // Lore – replace <uses> placeholder with actual max uses
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            String replaced = line.replace("<uses>", String.valueOf(config.getMaxUses()));
            lore.add(MINI.deserialize(replaced).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        // Custom model data
        if (config.getCustomModelData() > 0) {
            meta.setCustomModelData(config.getCustomModelData());
        }

        // Enchantments
        config.getEnchantments().forEach((key, level) -> {
            Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(key));
            if (ench != null) meta.addEnchant(ench, level, true);
        });

        // Make unbreakable
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        // PDC
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(USES_KEY, PersistentDataType.INTEGER, config.getMaxUses());
        pdc.set(MAX_USES_KEY, PersistentDataType.INTEGER, config.getMaxUses());
        pdc.set(BREAKABLE_KEY, PersistentDataType.STRING, String.join(",", config.getBreakableBlocks()));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns the remaining uses stored on the item, or -1 if it is not a breaker tool.
     */
    public static int getRemainingUses(ItemStack item) {
        if (item == null) return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(USES_KEY, PersistentDataType.INTEGER)) return -1;
        Integer uses = pdc.get(USES_KEY, PersistentDataType.INTEGER);
        return uses != null ? uses : -1;
    }

    /**
     * Decrements the remaining uses by 1. Returns the new remaining uses, or -1 if the item is
     * not a breaker. Returns 0 if uses have been depleted.
     */
    public static int decrementUses(ItemStack item) {
        if (item == null) return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(USES_KEY, PersistentDataType.INTEGER)) return -1;

        Integer current = pdc.get(USES_KEY, PersistentDataType.INTEGER);
        if (current == null) return -1;

        int newUses = Math.max(0, current - 1);
        pdc.set(USES_KEY, PersistentDataType.INTEGER, newUses);

        // Update lore to reflect new use count
        Integer maxUses = pdc.get(MAX_USES_KEY, PersistentDataType.INTEGER);
        if (maxUses == null) maxUses = newUses;

        List<Component> existingLore = meta.lore();
        if (existingLore != null) {
            List<Component> newLore = new ArrayList<>();
            for (Component line : existingLore) {
                // Rebuild any line that contains "Uses remaining:"
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                if (plain.contains("Uses remaining:")) {
                    newLore.add(MINI.deserialize("<yellow>Uses remaining: <white>" + newUses + "/" + maxUses)
                            .decoration(TextDecoration.ITALIC, false));
                } else {
                    newLore.add(line);
                }
            }
            meta.lore(newLore);
        }

        item.setItemMeta(meta);
        return newUses;
    }

    /**
     * Returns the list of breakable block material names for this breaker, or an empty list
     * if it is not a breaker.
     */
    public static List<String> getBreakableBlocks(ItemStack item) {
        if (item == null) return Collections.emptyList();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Collections.emptyList();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String raw = pdc.get(BREAKABLE_KEY, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        return Arrays.asList(raw.split(","));
    }

    /**
     * Returns true if the given ItemStack is a breaker tool (has the USES_KEY in its PDC).
     */
    public static boolean isBreakerTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(USES_KEY, PersistentDataType.INTEGER);
    }
}
