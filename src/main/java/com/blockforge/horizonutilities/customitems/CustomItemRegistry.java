package com.blockforge.horizonutilities.customitems;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Central registry for all custom items. Items are identified on ItemStacks by a
 * PDC tag {@code horizonutilities:custom_item_id}.
 */
public class CustomItemRegistry {

    public static final String PDC_KEY_NAME = "custom_item_id";

    private final NamespacedKey pdcKey;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();

    public CustomItemRegistry(HorizonUtilitiesPlugin plugin) {
        this.pdcKey = new NamespacedKey(plugin, PDC_KEY_NAME);
    }

    public void register(CustomItem item) {
        items.put(item.getId().toLowerCase(Locale.ROOT), item);
    }

    /** Returns the custom item ID stored in the ItemStack's PDC, or null if not a custom item. */
    public String getItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
    }

    /** Returns the registered CustomItem for the given ItemStack, or null. */
    public CustomItem getCustomItem(ItemStack stack) {
        String id = getItemId(stack);
        return id == null ? null : items.get(id);
    }

    /** Applies the PDC custom_item_id tag to an ItemMeta. Call before setting meta on the stack. */
    public void applyTag(ItemMeta meta, String itemId) {
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, itemId.toLowerCase(Locale.ROOT));
    }

    public CustomItem get(String id) {
        return items.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomItem> getAll() {
        return Collections.unmodifiableCollection(items.values());
    }

    public NamespacedKey getPdcKey() { return pdcKey; }
}
