package com.blockforge.horizonutilities.auction.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionListing;
import com.blockforge.horizonutilities.config.MessagesManager;
import com.blockforge.horizonutilities.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class AuctionMainGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 32;

    private static final int[] LISTING_SLOTS = {
            1, 2, 3, 4, 5, 6, 7, 8,
            10, 11, 12, 13, 14, 15, 16, 17,
            19, 20, 21, 22, 23, 24, 25, 26,
            28, 29, 30, 31, 32, 33, 34, 35
    };

    public static final int SLOT_CAT_ALL = 0;
    public static final int SLOT_CAT_WEAPONS = 9;
    public static final int SLOT_CAT_ARMOR = 18;
    public static final int SLOT_CAT_TOOLS = 27;
    public static final int SLOT_CAT_BLOCKS = 36;
    public static final int SLOT_CAT_CONSUMABLES = 45;
    public static final int SLOT_CAT_BOOKS = 37;
    public static final int SLOT_CAT_MISC = 38;
    public static final int SLOT_SORT = 40;
    public static final int SLOT_SEARCH = 41;
    public static final int SLOT_PREV = 43;
    public static final int SLOT_NEXT = 44;
    public static final int SLOT_PAGE_INFO = 49;
    public static final int SLOT_COLLECTION = 50;
    public static final int SLOT_HISTORY = 51;
    public static final int SLOT_MY_LISTINGS = 52;

    private static final Map<UUID, BrowseState> states = new HashMap<>();

    public static BrowseState getState(UUID uuid) { return states.get(uuid); }
    public static void removeState(UUID uuid) { states.remove(uuid); }

    public static void open(Player player) {
        open(player, "All", 0, null, SortType.NEWEST, false);
    }

    public static void open(Player player, String category, int page, String searchQuery, SortType sort, boolean myListings) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var msg = plugin.getMessagesManager();
        var am = plugin.getAuctionManager();
        var vault = plugin.getVaultHook();

        Component title = msg.format("ah-gui-title");
        Inventory inv = Bukkit.createInventory(null, SIZE, title);

        // category buttons
        setCategoryButton(inv, msg, SLOT_CAT_ALL, "ah-gui-category-all", Material.NETHER_STAR, "All", category);
        setCategoryButton(inv, msg, SLOT_CAT_WEAPONS, "ah-gui-category-weapons", Material.DIAMOND_SWORD, "Weapons", category);
        setCategoryButton(inv, msg, SLOT_CAT_ARMOR, "ah-gui-category-armor", Material.DIAMOND_CHESTPLATE, "Armor", category);
        setCategoryButton(inv, msg, SLOT_CAT_TOOLS, "ah-gui-category-tools", Material.DIAMOND_PICKAXE, "Tools", category);
        setCategoryButton(inv, msg, SLOT_CAT_BLOCKS, "ah-gui-category-blocks", Material.GRASS_BLOCK, "Blocks", category);
        setCategoryButton(inv, msg, SLOT_CAT_CONSUMABLES, "ah-gui-category-consumables", Material.GOLDEN_APPLE, "Consumables", category);
        setCategoryButton(inv, msg, SLOT_CAT_BOOKS, "ah-gui-category-books", Material.ENCHANTED_BOOK, "Enchanted Books", category);
        setCategoryButton(inv, msg, SLOT_CAT_MISC, "ah-gui-category-misc", Material.CHEST, "Misc", category);

        // sort button
        ItemStack sortBtn = makeButton(Material.HOPPER, msg.format("ah-gui-sort"),
                Component.text("Current: " + sort.displayName(), NamedTextColor.GRAY),
                Component.text("Click to change", NamedTextColor.DARK_GRAY));
        inv.setItem(SLOT_SORT, sortBtn);

        // search button
        List<Component> searchLore = new ArrayList<>();
        if (searchQuery != null) {
            searchLore.add(Component.text("Query: " + searchQuery, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            searchLore.add(Component.text("Click to clear", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            searchLore.add(Component.text("Click to search", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        ItemStack searchBtn = makeButton(Material.COMPASS, msg.format("ah-gui-search"), searchLore);
        inv.setItem(SLOT_SEARCH, searchBtn);

        // fetch listings
        List<AuctionListing> listings;
        int totalCount;

        if (myListings) {
            listings = am.getPlayerActiveListings(player.getUniqueId(), page, PER_PAGE);
            totalCount = am.countPlayerListings(player.getUniqueId());
        } else if (searchQuery != null && !searchQuery.isEmpty()) {
            listings = am.searchListings(searchQuery, page, PER_PAGE);
            totalCount = am.countSearchListings(searchQuery);
        } else {
            listings = am.getActiveListings(category, page, PER_PAGE);
            totalCount = am.countActiveListings(category);
        }

        sortListings(listings, sort);

        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PER_PAGE));

        // place listing items
        for (int i = 0; i < LISTING_SLOTS.length && i < listings.size(); i++) {
            AuctionListing listing = listings.get(i);
            inv.setItem(LISTING_SLOTS[i], buildListingItem(plugin, listing));
        }

        // prev/next page
        if (page > 0) {
            inv.setItem(SLOT_PREV, makeButton(Material.ARROW, msg.format("ah-gui-prev-page")));
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeButton(Material.ARROW, msg.format("ah-gui-next-page")));
        }

        // page info
        inv.setItem(SLOT_PAGE_INFO, makeButton(Material.PAPER, msg.format("ah-gui-page-info",
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("total", String.valueOf(totalPages)))));

        // collection box
        inv.setItem(SLOT_COLLECTION, makeButton(Material.ENDER_CHEST, msg.format("ah-gui-collection")));

        // history
        inv.setItem(SLOT_HISTORY, makeButton(Material.BOOK, msg.format("ah-gui-history")));

        // my listings (player head)
        ItemStack myListingsItem = new ItemStack(Material.PLAYER_HEAD);
        var headMeta = (SkullMeta) myListingsItem.getItemMeta();
        headMeta.setOwningPlayer(player);
        headMeta.displayName(msg.format("ah-gui-my-listings").decoration(TextDecoration.ITALIC, false));
        if (myListings) headMeta.setEnchantmentGlintOverride(true);
        myListingsItem.setItemMeta(headMeta);
        inv.setItem(SLOT_MY_LISTINGS, myListingsItem);

        states.put(player.getUniqueId(), new BrowseState(category, page, searchQuery, sort, myListings, listings));
        player.openInventory(inv);
    }

    public static int getListingSlotIndex(int rawSlot) {
        for (int i = 0; i < LISTING_SLOTS.length; i++) {
            if (LISTING_SLOTS[i] == rawSlot) return i;
        }
        return -1;
    }

    public static String categoryForSlot(int slot) {
        return switch (slot) {
            case SLOT_CAT_ALL -> "All";
            case SLOT_CAT_WEAPONS -> "Weapons";
            case SLOT_CAT_ARMOR -> "Armor";
            case SLOT_CAT_TOOLS -> "Tools";
            case SLOT_CAT_BLOCKS -> "Blocks";
            case SLOT_CAT_CONSUMABLES -> "Consumables";
            case SLOT_CAT_BOOKS -> "Enchanted Books";
            case SLOT_CAT_MISC -> "Misc";
            default -> null;
        };
    }

    private static ItemStack buildListingItem(HorizonUtilitiesPlugin plugin, AuctionListing listing) {
        ItemStack display = listing.getItem().clone();
        var meta = display.getItemMeta();
        var vault = plugin.getVaultHook();

        String buyoutStr = listing.hasBuyout() ? vault.format(listing.getBuyoutPrice()) : "None";
        String currentBidStr = listing.hasBids() ? vault.format(listing.getCurrentBid()) : "None";
        String timeLeft = TimeUtil.formatDuration(listing.getTimeLeftSeconds());

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.lore());
        lore.add(Component.empty());
        lore.add(infoLine("Seller: ", listing.getSellerName()));
        lore.add(infoLine("Starting Bid: ", vault.format(listing.getStartPrice())));
        lore.add(infoLine("Buy It Now: ", buyoutStr));
        lore.add(infoLine("Current Bid: ", currentBidStr));
        lore.add(infoLine("Time Left: ", timeLeft));
        lore.add(Component.empty());
        lore.add(Component.text("Left-click to view", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static Component infoLine(String label, String value) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static void setCategoryButton(Inventory inv, MessagesManager msg, int slot, String key, Material mat, String name, String active) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(msg.format(key).decoration(TextDecoration.ITALIC, false));
        if (name.equals(active)) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private static ItemStack makeButton(Material mat, Component name, Component... loreLines) {
        return makeButton(mat, name, Arrays.asList(loreLines));
    }

    private static ItemStack makeButton(Material mat, Component name, List<Component> loreLines) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (!loreLines.isEmpty()) {
            meta.lore(loreLines.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private static void sortListings(List<AuctionListing> listings, SortType sort) {
        switch (sort) {
            case NEWEST -> listings.sort(Comparator.comparingLong(AuctionListing::getListedAt).reversed());
            case OLDEST -> listings.sort(Comparator.comparingLong(AuctionListing::getListedAt));
            case PRICE_LOW -> listings.sort(Comparator.comparingDouble(l -> l.hasBuyout() ? l.getBuyoutPrice() : l.getStartPrice()));
            case PRICE_HIGH -> listings.sort(Comparator.comparingDouble((AuctionListing l) -> l.hasBuyout() ? l.getBuyoutPrice() : l.getStartPrice()).reversed());
        }
    }

    public enum SortType {
        NEWEST("Newest First"),
        OLDEST("Oldest First"),
        PRICE_LOW("Price: Low to High"),
        PRICE_HIGH("Price: High to Low");

        private final String displayName;
        SortType(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        public SortType next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public record BrowseState(String category, int page, String searchQuery, SortType sort, boolean myListings, List<AuctionListing> listings) {}
}
