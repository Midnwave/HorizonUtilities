package com.blockforge.horizonutilities.customitems.items;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.customitems.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class WolfWhistleItem implements CustomItem {

    private final HorizonUtilitiesPlugin plugin;
    public static final String ID = "wolf_whistle";

    public WolfWhistleItem(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return ID; }
    @Override public String getDisplayName() { return "Wolf Whistle"; }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Wolf Whistle", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("Right-click to summon your tamed wolves!", NamedTextColor.GRAY),
                Component.text("Range: 200 blocks | Cooldown: 30s", NamedTextColor.DARK_GRAY)
        ));
        plugin.getCustomItemRegistry().applyTag(meta, ID);
        item.setItemMeta(meta);
        return item;
    }
}
