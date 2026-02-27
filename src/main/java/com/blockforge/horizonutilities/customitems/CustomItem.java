package com.blockforge.horizonutilities.customitems;

import org.bukkit.inventory.ItemStack;

/**
 * Base interface for all custom items in HorizonUtilities.
 * Each implementation represents one unique item type.
 */
public interface CustomItem {

    /** Unique lowercase identifier (e.g., "grappling_hook"). */
    String getId();

    /** Human-readable display name shown in item name. */
    String getDisplayName();

    /**
     * Creates a new ItemStack representing this custom item.
     * The returned stack must have the custom_item_id PDC tag set.
     */
    ItemStack createItem();
}
