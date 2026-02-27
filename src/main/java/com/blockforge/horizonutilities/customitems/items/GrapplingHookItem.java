package com.blockforge.horizonutilities.customitems.items;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.customitems.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GrapplingHookItem implements CustomItem {

    private final HorizonUtilitiesPlugin plugin;
    public static final String ID = "grappling_hook";

    public GrapplingHookItem(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return ID; }
    @Override public String getDisplayName() { return "Grappling Hook"; }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Grappling Hook", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Right-click to launch!", NamedTextColor.GRAY),
                Component.text("Pulls you toward the target block.", NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("Durability: " +
                        plugin.getCustomItemRegistry().get(ID) == null
                        ? "50"
                        : "50" /* placeholder */, NamedTextColor.YELLOW)
        ));
        plugin.getCustomItemRegistry().applyTag(meta, ID);
        item.setItemMeta(meta);
        return item;
    }
}
