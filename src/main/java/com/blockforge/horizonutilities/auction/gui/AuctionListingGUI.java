package com.blockforge.horizonutilities.auction.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionListing;
import com.blockforge.horizonutilities.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class AuctionListingGUI {

    private static final int SIZE = 45;

    public static final int SLOT_ITEM = 13;
    public static final int SLOT_SELLER = 20;
    public static final int SLOT_PRICE_INFO = 22;
    public static final int SLOT_TIME_INFO = 24;
    public static final int SLOT_BUY_NOW = 29;
    public static final int SLOT_BID = 31;
    public static final int SLOT_CANCEL = 33;
    public static final int SLOT_BACK = 36;

    private static final Map<UUID, Integer> viewedListings = new HashMap<>();

    public static Integer getViewedListing(UUID uuid) { return viewedListings.get(uuid); }
    public static void removeViewedListing(UUID uuid) { viewedListings.remove(uuid); }

    public static void open(Player player, int listingId) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var msg = plugin.getMessagesManager();
        var vault = plugin.getVaultHook();
        AuctionListing listing = plugin.getAuctionManager().getListing(listingId);

        if (listing == null) {
            msg.send(player, "ah-admin-not-found");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, SIZE,
                Component.text("Listing #" + listingId, NamedTextColor.DARK_PURPLE));

        // fill borders with glass
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        var glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.empty());
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 36; i < SIZE; i++) inv.setItem(i, glass);

        // the item
        inv.setItem(SLOT_ITEM, listing.getItem().clone());

        // seller head
        ItemStack sellerHead = new ItemStack(Material.PLAYER_HEAD);
        var headMeta = (SkullMeta) sellerHead.getItemMeta();
        headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(listing.getSellerUuid())));
        headMeta.displayName(Component.text("Seller", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        headMeta.lore(List.of(Component.text(listing.getSellerName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        sellerHead.setItemMeta(headMeta);
        inv.setItem(SLOT_SELLER, sellerHead);

        // price info
        ItemStack priceItem = new ItemStack(Material.EMERALD);
        var priceMeta = priceItem.getItemMeta();
        priceMeta.displayName(Component.text("Price Info", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> priceLore = new ArrayList<>();
        priceLore.add(infoLine("Start Price: ", vault.format(listing.getStartPrice())));
        if (listing.hasBuyout()) {
            priceLore.add(infoLine("Buy It Now: ", vault.format(listing.getBuyoutPrice())));
        }
        if (listing.hasBids()) {
            priceLore.add(infoLine("Current Bid: ", vault.format(listing.getCurrentBid())));
            priceLore.add(infoLine("Bidder: ", listing.getCurrentBidderName()));
        } else {
            priceLore.add(Component.text("No bids yet", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        priceMeta.lore(priceLore);
        priceItem.setItemMeta(priceMeta);
        inv.setItem(SLOT_PRICE_INFO, priceItem);

        // time info
        ItemStack timeItem = new ItemStack(Material.CLOCK);
        var timeMeta = timeItem.getItemMeta();
        timeMeta.displayName(Component.text("Time Remaining", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        timeMeta.lore(List.of(
                Component.text(TimeUtil.formatDuration(listing.getTimeLeftSeconds()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
        ));
        timeItem.setItemMeta(timeMeta);
        inv.setItem(SLOT_TIME_INFO, timeItem);

        boolean isOwner = listing.getSellerUuid().equals(player.getUniqueId().toString());

        // buy now button
        if (listing.hasBuyout() && !isOwner) {
            ItemStack buyBtn = new ItemStack(Material.EMERALD_BLOCK);
            var buyMeta = buyBtn.getItemMeta();
            buyMeta.displayName(Component.text("Buy Now", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            buyMeta.lore(List.of(
                    infoLine("Price: ", vault.format(listing.getBuyoutPrice())),
                    Component.text("Click to purchase", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            buyBtn.setItemMeta(buyMeta);
            inv.setItem(SLOT_BUY_NOW, buyBtn);
        }

        // bid button
        if (!isOwner) {
            double minBid = calculateMinBid(plugin, listing);
            ItemStack bidBtn = new ItemStack(Material.GOLD_BLOCK);
            var bidMeta = bidBtn.getItemMeta();
            bidMeta.displayName(Component.text("Place Bid", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            bidMeta.lore(List.of(
                    infoLine("Minimum Bid: ", vault.format(minBid)),
                    Component.text("Click to bid", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            bidBtn.setItemMeta(bidMeta);
            inv.setItem(SLOT_BID, bidBtn);
        }

        // cancel button (owner only)
        if (isOwner) {
            ItemStack cancelBtn = new ItemStack(Material.RED_WOOL);
            var cancelMeta = cancelBtn.getItemMeta();
            cancelMeta.displayName(Component.text("Cancel Listing", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            cancelMeta.lore(List.of(
                    Component.text("Click to cancel", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            cancelBtn.setItemMeta(cancelMeta);
            inv.setItem(SLOT_CANCEL, cancelBtn);
        }

        // back button
        ItemStack backBtn = new ItemStack(Material.ARROW);
        var backMeta = backBtn.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        backBtn.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, backBtn);

        viewedListings.put(player.getUniqueId(), listingId);
        player.openInventory(inv);
    }

    public static double calculateMinBid(HorizonUtilitiesPlugin plugin, AuctionListing listing) {
        double increment = plugin.getAuctionHouseConfig().getMinBidIncrementPercent() / 100.0;
        if (listing.hasBids()) {
            return listing.getCurrentBid() * (1 + increment);
        }
        return listing.getStartPrice();
    }

    private static Component infoLine(String label, String value) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false);
    }
}
