package com.blockforge.horizonutilities.auction;

import org.bukkit.inventory.ItemStack;

public class ItemSerializer {

    public static byte[] serialize(ItemStack item) {
        return item.serializeAsBytes();
    }

    public static ItemStack deserialize(byte[] data) {
        return ItemStack.deserializeBytes(data);
    }
}
