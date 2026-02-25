package com.blockforge.horizonutilities.auction.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionListing;
import com.blockforge.horizonutilities.auction.AuctionManager;
import com.blockforge.horizonutilities.auction.dialogs.ConfirmCancelDialog;
import com.blockforge.horizonutilities.auction.dialogs.ConfirmPurchaseDialog;
import com.blockforge.horizonutilities.auction.dialogs.PlaceBidDialog;
import com.blockforge.horizonutilities.auction.gui.*;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class AuctionGUIListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    private static final Map<UUID, GUIType> openGUIs = new HashMap<>();

    public enum GUIType { MAIN, LISTING, COLLECTION, HISTORY, PRICE_HISTORY }

    public static void setGUI(UUID uuid, GUIType type) { openGUIs.put(uuid, type); }
    public static void clearGUI(UUID uuid) { openGUIs.remove(uuid); }
    public static GUIType getGUI(UUID uuid) { return openGUIs.get(uuid); }

    public AuctionGUIListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GUIType type = openGUIs.get(player.getUniqueId());
        if (type == null) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();

        switch (type) {
            case MAIN -> handleMainClick(player, slot);
            case LISTING -> handleListingClick(player, slot);
            case COLLECTION -> handleCollectionClick(player, slot);
            case HISTORY -> handleHistoryClick(player, slot);
            case PRICE_HISTORY -> handlePriceHistoryClick(player, slot);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // defer cleanup to allow GUI transitions
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getSize() <= 4) {
                cleanupPlayer(uuid);
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    private void cleanupPlayer(UUID uuid) {
        openGUIs.remove(uuid);
        AuctionMainGUI.removeState(uuid);
        AuctionListingGUI.removeViewedListing(uuid);
        AuctionCollectionGUI.removeState(uuid);
        AuctionHistoryGUI.removeState(uuid);
    }

    private void handleMainClick(Player player, int slot) {
        var state = AuctionMainGUI.getState(player.getUniqueId());
        if (state == null) return;

        // category buttons
        String category = AuctionMainGUI.categoryForSlot(slot);
        if (category != null) {
            openMain(player, category, 0, null, state.sort(), false);
            return;
        }

        switch (slot) {
            case AuctionMainGUI.SLOT_SORT -> {
                var nextSort = state.sort().next();
                openMain(player, state.category(), state.page(), state.searchQuery(), nextSort, state.myListings());
            }
            case AuctionMainGUI.SLOT_SEARCH -> {
                if (state.searchQuery() != null) {
                    // clear search
                    openMain(player, state.category(), 0, null, state.sort(), state.myListings());
                } else {
                    // prompt for search
                    player.closeInventory();
                    plugin.getMessagesManager().send(player, "ah-search-results",
                            Placeholder.unparsed("query", "...type your search in /ah search <query>"));
                }
            }
            case AuctionMainGUI.SLOT_PREV -> {
                if (state.page() > 0) {
                    openMain(player, state.category(), state.page() - 1, state.searchQuery(), state.sort(), state.myListings());
                }
            }
            case AuctionMainGUI.SLOT_NEXT -> {
                openMain(player, state.category(), state.page() + 1, state.searchQuery(), state.sort(), state.myListings());
            }
            case AuctionMainGUI.SLOT_COLLECTION -> {
                setGUI(player.getUniqueId(), GUIType.COLLECTION);
                AuctionCollectionGUI.open(player, 0);
            }
            case AuctionMainGUI.SLOT_HISTORY -> {
                setGUI(player.getUniqueId(), GUIType.HISTORY);
                AuctionHistoryGUI.open(player, 0);
            }
            case AuctionMainGUI.SLOT_MY_LISTINGS -> {
                openMain(player, state.category(), 0, null, state.sort(), !state.myListings());
            }
            default -> {
                // check if it's a listing slot
                int idx = AuctionMainGUI.getListingSlotIndex(slot);
                if (idx >= 0 && idx < state.listings().size()) {
                    AuctionListing listing = state.listings().get(idx);
                    setGUI(player.getUniqueId(), GUIType.LISTING);
                    AuctionListingGUI.open(player, listing.getId());
                }
            }
        }
    }

    private void handleListingClick(Player player, int slot) {
        Integer listingId = AuctionListingGUI.getViewedListing(player.getUniqueId());
        if (listingId == null) return;

        AuctionListing listing = plugin.getAuctionManager().getListing(listingId);
        if (listing == null || !listing.getStatus().equals("ACTIVE")) {
            plugin.getMessagesManager().send(player, "ah-admin-not-found");
            player.closeInventory();
            return;
        }

        boolean isOwner = listing.getSellerUuid().equals(player.getUniqueId().toString());

        switch (slot) {
            case AuctionListingGUI.SLOT_BUY_NOW -> {
                if (!isOwner && listing.hasBuyout()) {
                    player.closeInventory();
                    ConfirmPurchaseDialog.show(player, listing);
                }
            }
            case AuctionListingGUI.SLOT_BID -> {
                if (!isOwner) {
                    player.closeInventory();
                    PlaceBidDialog.show(player, listing);
                }
            }
            case AuctionListingGUI.SLOT_CANCEL -> {
                if (isOwner) {
                    player.closeInventory();
                    ConfirmCancelDialog.show(player, listing);
                }
            }
            case AuctionListingGUI.SLOT_BACK -> {
                setGUI(player.getUniqueId(), GUIType.MAIN);
                AuctionMainGUI.open(player);
            }
        }
    }

    private void handleCollectionClick(Player player, int slot) {
        var state = AuctionCollectionGUI.getState(player.getUniqueId());
        if (state == null) return;

        switch (slot) {
            case AuctionCollectionGUI.SLOT_BACK -> {
                setGUI(player.getUniqueId(), GUIType.MAIN);
                AuctionMainGUI.open(player);
            }
            case AuctionCollectionGUI.SLOT_COLLECT_ALL -> {
                collectAll(player, state);
            }
            case AuctionCollectionGUI.SLOT_PREV -> {
                if (state.page() > 0) {
                    setGUI(player.getUniqueId(), GUIType.COLLECTION);
                    AuctionCollectionGUI.open(player, state.page() - 1);
                }
            }
            case AuctionCollectionGUI.SLOT_NEXT -> {
                setGUI(player.getUniqueId(), GUIType.COLLECTION);
                AuctionCollectionGUI.open(player, state.page() + 1);
            }
            default -> {
                if (slot >= 0 && slot < state.entries().size()) {
                    collectEntry(player, state.entries().get(slot));
                    // refresh
                    setGUI(player.getUniqueId(), GUIType.COLLECTION);
                    AuctionCollectionGUI.open(player, state.page());
                }
            }
        }
    }

    private void handleHistoryClick(Player player, int slot) {
        var state = AuctionHistoryGUI.getState(player.getUniqueId());
        if (state == null) return;

        switch (slot) {
            case AuctionHistoryGUI.SLOT_BACK -> {
                setGUI(player.getUniqueId(), GUIType.MAIN);
                AuctionMainGUI.open(player);
            }
            case AuctionHistoryGUI.SLOT_PREV -> {
                if (state.page() > 0) {
                    setGUI(player.getUniqueId(), GUIType.HISTORY);
                    AuctionHistoryGUI.open(player, state.page() - 1);
                }
            }
            case AuctionHistoryGUI.SLOT_NEXT -> {
                setGUI(player.getUniqueId(), GUIType.HISTORY);
                AuctionHistoryGUI.open(player, state.page() + 1);
            }
        }
    }

    private void handlePriceHistoryClick(Player player, int slot) {
        if (slot == PriceHistoryGUI.SLOT_BACK) {
            setGUI(player.getUniqueId(), GUIType.MAIN);
            AuctionMainGUI.open(player);
        }
    }

    private void openMain(Player player, String category, int page, String search, AuctionMainGUI.SortType sort, boolean myListings) {
        setGUI(player.getUniqueId(), GUIType.MAIN);
        AuctionMainGUI.open(player, category, page, search, sort, myListings);
    }

    private void collectEntry(Player player, AuctionManager.CollectionEntry entry) {
        if (entry.type().equals("ITEM") && entry.item() != null) {
            var leftover = player.getInventory().addItem(entry.item());
            if (!leftover.isEmpty()) {
                plugin.getMessagesManager().send(player, "ah-collection-empty");
                return;
            }
        } else if (entry.type().equals("MONEY")) {
            plugin.getVaultHook().deposit(player, entry.amount());
        }
        plugin.getAuctionManager().removeCollectionEntry(entry.id());
    }

    private void collectAll(Player player, AuctionCollectionGUI.CollectionState state) {
        int itemCount = 0;
        double totalMoney = 0;

        // collect from full collection, not just current page
        List<AuctionManager.CollectionEntry> all = plugin.getAuctionManager().getCollection(player.getUniqueId());
        for (AuctionManager.CollectionEntry entry : all) {
            if (entry.type().equals("ITEM") && entry.item() != null) {
                var leftover = player.getInventory().addItem(entry.item());
                if (leftover.isEmpty()) {
                    plugin.getAuctionManager().removeCollectionEntry(entry.id());
                    itemCount++;
                }
            } else if (entry.type().equals("MONEY")) {
                plugin.getVaultHook().deposit(player, entry.amount());
                plugin.getAuctionManager().removeCollectionEntry(entry.id());
                totalMoney += entry.amount();
                itemCount++;
            }
        }

        if (itemCount > 0) {
            plugin.getMessagesManager().send(player, "ah-collection-collected",
                    Placeholder.unparsed("count", String.valueOf(itemCount)),
                    Placeholder.unparsed("money", plugin.getVaultHook().format(totalMoney)));
        } else {
            plugin.getMessagesManager().send(player, "ah-collection-empty");
        }

        // refresh
        setGUI(player.getUniqueId(), GUIType.COLLECTION);
        AuctionCollectionGUI.open(player, 0);
    }
}
