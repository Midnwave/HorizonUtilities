package com.blockforge.horizonutilities.customitems.items;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.customitems.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class IceBombItem implements CustomItem {

    private final HorizonUtilitiesPlugin plugin;
    public static final String ID = "ice_bomb";

    public IceBombItem(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return ID; }
    @Override public String getDisplayName() { return "Ice Bomb"; }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Ice Bomb", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Throw to freeze water and nearby mobs!", NamedTextColor.GRAY),
                Component.text("Radius: 5 blocks | Duration: 10s", NamedTextColor.DARK_GRAY)
        ));
        plugin.getCustomItemRegistry().applyTag(meta, ID);
        item.setItemMeta(meta);
        return item;
    }
}
