package com.blockforge.horizonutilities.auction.dialogs;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionListing;
import com.blockforge.horizonutilities.auction.gui.AuctionListingGUI;
import com.blockforge.horizonutilities.dialog.DialogUtil;
import com.blockforge.horizonutilities.util.BedrockUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceBidDialog {

    /** Pending chat-based bid entries for Bedrock players. */
    private static final Map<UUID, Integer> pendingChatBids = new ConcurrentHashMap<>();

    public static void show(Player player, AuctionListing listing) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var vault = plugin.getVaultHook();
        var msg = plugin.getMessagesManager();

        double minBid = AuctionListingGUI.calculateMinBid(plugin, listing);

        // Bedrock players can't use the dialog — use chat prompt
        if (BedrockUtil.isBedrock(player)) {
            showChatBid(player, listing, minBid);
            return;
        }

        Component title = Component.text(msg.getRaw("ah-dialog-bid-title"));
        Component details = Component.text("Current Bid: ", NamedTextColor.GRAY)
                .append(Component.text(listing.hasBids() ? vault.format(listing.getCurrentBid()) : "None", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Minimum Bid: ", NamedTextColor.GRAY))
                .append(Component.text(vault.format(minBid), NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("Seller: ", NamedTextColor.GRAY))
                .append(Component.text(listing.getSellerName(), NamedTextColor.GOLD));

        // Text input for custom bid amount, pre-filled with minimum bid
        DialogInput bidInput = DialogInput.text("bid_amount", Component.text("Bid Amount"))
                .width(200)
                .initial(String.format("%.2f", minBid))
                .maxLength(20)
                .build();

        Component confirmText = Component.text("Place Bid", NamedTextColor.GREEN);
        Component cancelText = Component.text("Cancel", NamedTextColor.RED);

        try {
            DialogUtil.showConfirmationWithItemAndInput(player, title, listing.getItem(), details,
                    List.of(bidInput), confirmText, cancelText, (response) -> {
                        processBid(player, listing, response.getText("bid_amount"));
                    });
        } catch (Exception e) {
            // Dialog API failed — fall back to chat prompt
            showChatBid(player, listing, minBid);
        }
    }

    /** Chat-based bid for Bedrock / ViaBackwards players. */
    private static void showChatBid(Player player, AuctionListing listing, double minBid) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var vault = plugin.getVaultHook();

        pendingChatBids.put(player.getUniqueId(), listing.getId());

        player.sendMessage(plugin.getMessagesManager().format("prefix")
                .append(Component.text(" Enter your bid amount in chat:", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  Minimum bid: " + vault.format(minBid), NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Type 'cancel' to cancel.", NamedTextColor.DARK_GRAY));

        // Register a temporary chat listener for this player's bid
        Listener chatListener = new Listener() {
            @EventHandler
            public void onChat(AsyncChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                if (!pendingChatBids.containsKey(player.getUniqueId())) return;

                event.setCancelled(true);
                pendingChatBids.remove(player.getUniqueId());
                HandlerList.unregisterAll(this);

                String input = PlainTextComponentSerializer.plainText()
                        .serialize(event.message()).trim();
                if (input.equalsIgnoreCase("cancel")) {
                    player.sendMessage(Component.text("Bid cancelled.", NamedTextColor.GRAY));
                    return;
                }

                // Process on main thread
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        processBid(player, listing, input));
            }
        };
        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);

        // Auto-cancel after 30 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingChatBids.remove(player.getUniqueId()) != null) {
                HandlerList.unregisterAll(chatListener);
                player.sendMessage(Component.text("Bid timed out.", NamedTextColor.RED));
            }
        }, 600L); // 30 seconds
    }

    /** Shared bid processing logic for both dialog and chat. */
    private static void processBid(Player player, AuctionListing listing, String bidText) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var vault = plugin.getVaultHook();
        var msg = plugin.getMessagesManager();

        AuctionListing fresh = plugin.getAuctionManager().getListing(listing.getId());
        if (fresh == null || !fresh.getStatus().equals("ACTIVE")) {
            msg.send(player, "ah-admin-not-found");
            return;
        }

        double freshMinBid = AuctionListingGUI.calculateMinBid(plugin, fresh);

        double bidAmount;
        try {
            bidAmount = Double.parseDouble(bidText != null ? bidText.trim() : "");
        } catch (NumberFormatException e) {
            msg.send(player, "ah-invalid-price");
            return;
        }

        if (bidAmount < freshMinBid) {
            msg.send(player, "ah-bid-too-low",
                    Placeholder.unparsed("min", vault.format(freshMinBid)));
            return;
        }

        if (plugin.getAuctionManager().placeBid(player, fresh, bidAmount)) {
            msg.send(player, "ah-bid-placed",
                    Placeholder.unparsed("amount", vault.format(bidAmount)),
                    Placeholder.unparsed("item", fresh.getItemDisplayName()));

            // notify previous bidder if there was one
            if (listing.hasBids() && !listing.getCurrentBidderUuid().equals(player.getUniqueId().toString())) {
                plugin.getNotificationManager().notify(listing.getCurrentBidderUuid(), "ah-bid-outbid",
                        "item", fresh.getItemDisplayName(),
                        "amount", vault.format(bidAmount),
                        "refund", vault.format(listing.getCurrentBid()));
            }
        } else {
            msg.send(player, "ah-not-enough-money");
        }
    }
}
