package com.blockforge.horizonutilities.auction.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class AuctionCollectionGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    public static final int SLOT_COLLECT_ALL = 49;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_NEXT = 50;

    private static final Map<UUID, CollectionState> states = new HashMap<>();

    public static CollectionState getState(UUID uuid) { return states.get(uuid); }
    public static void removeState(UUID uuid) { states.remove(uuid); }

    public static void open(Player player, int page) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var msg = plugin.getMessagesManager();

        Component title = msg.format("ah-gui-collection-title");
        Inventory inv = Bukkit.createInventory(null, SIZE, title);

        List<AuctionManager.CollectionEntry> allEntries = plugin.getAuctionManager().getCollection(player.getUniqueId());
        int totalPages = Math.max(1, (int) Math.ceil(allEntries.size() / (double) PER_PAGE));
        page = Math.min(page, totalPages - 1);

        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, allEntries.size());
        List<AuctionManager.CollectionEntry> pageEntries = allEntries.subList(start, end);

        for (int i = 0; i < pageEntries.size() && i < PER_PAGE; i++) {
            AuctionManager.CollectionEntry entry = pageEntries.get(i);
            inv.setItem(i, buildEntryItem(plugin, entry));
        }

        // bottom row controls
        ItemStack back = new ItemStack(Material.ARROW);
        var backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, back);

        if (!allEntries.isEmpty()) {
            ItemStack collectAll = new ItemStack(Material.HOPPER);
            var collectMeta = collectAll.getItemMeta();
            collectMeta.displayName(Component.text("Collect All", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            collectMeta.lore(List.of(Component.text(allEntries.size() + " item(s)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            collectAll.setItemMeta(collectMeta);
            inv.setItem(SLOT_COLLECT_ALL, collectAll);
        }

        if (page > 0) {
            inv.setItem(SLOT_PREV, makeArrow(msg.format("ah-gui-prev-page")));
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeArrow(msg.format("ah-gui-next-page")));
        }

        states.put(player.getUniqueId(), new CollectionState(page, pageEntries));
        player.openInventory(inv);
    }

    private static ItemStack buildEntryItem(HorizonUtilitiesPlugin plugin, AuctionManager.CollectionEntry entry) {
        if (entry.type().equals("ITEM") && entry.item() != null) {
            ItemStack display = entry.item().clone();
            var meta = display.getItemMeta();
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore()) lore.addAll(meta.lore());
            lore.add(Component.empty());
            lore.add(Component.text(entry.reason(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to collect", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            display.setItemMeta(meta);
            return display;
        } else {
            // money entry
            ItemStack gold = new ItemStack(Material.GOLD_INGOT);
            var meta = gold.getItemMeta();
            meta.displayName(Component.text(plugin.getVaultHook().format(entry.amount()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(entry.reason(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Click to collect", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
            ));
            gold.setItemMeta(meta);
            return gold;
        }
    }

    private static ItemStack makeArrow(Component name) {
        ItemStack item = new ItemStack(Material.ARROW);
        var meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public record CollectionState(int page, List<AuctionManager.CollectionEntry> entries) {}
}
