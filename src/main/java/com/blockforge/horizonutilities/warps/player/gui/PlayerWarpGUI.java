package com.blockforge.horizonutilities.warps.player.gui;

import com.blockforge.horizonutilities.warps.player.PlayerWarp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a paginated GUI showing player warps. Each warp is represented by a
 * COMPASS with owner, rating, and visits in the lore.
 */
public class PlayerWarpGUI {

    public static final String TITLE_PREFIX = "Player Warps";
    private static final int PAGE_SIZE = 45;

    public static Inventory build(List<PlayerWarp> warps, int page) {
        int totalPages = Math.max(1, (int) Math.ceil((double) warps.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_PREFIX + " » Page " + (page + 1) + "/" + totalPages, NamedTextColor.GOLD));

        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, warps.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildWarpItem(warps.get(i)));
        }

        // Navigation
        if (page > 0) inv.setItem(45, navItem(Material.ARROW, "← Previous"));
        inv.setItem(49, navItem(Material.COMPASS, "All Warps"));
        if (page < totalPages - 1) inv.setItem(53, navItem(Material.ARROW, "Next →"));

        return inv;
    }

    private static ItemStack buildWarpItem(PlayerWarp warp) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(warp.getName(), NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Owner: ", NamedTextColor.GRAY)
                .append(Component.text(warp.getOwnerName(), NamedTextColor.AQUA)));
        lore.add(Component.text("Rating: ", NamedTextColor.GRAY)
                .append(Component.text(warp.getStarDisplay(), NamedTextColor.YELLOW)));
        lore.add(Component.text("Visits: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(warp.getVisits()), NamedTextColor.WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text("Click to teleport", NamedTextColor.GREEN));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navItem(Material mat, String label) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }
}
