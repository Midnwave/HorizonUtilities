package com.blockforge.horizonutilities.auction.dialogs;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionListing;
import com.blockforge.horizonutilities.auction.gui.AuctionListingGUI;
import com.blockforge.horizonutilities.dialog.DialogUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public class PlaceBidDialog {

    public static void show(Player player, AuctionListing listing) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var vault = plugin.getVaultHook();
        var msg = plugin.getMessagesManager();

        double minBid = AuctionListingGUI.calculateMinBid(plugin, listing);

        Component title = Component.text(msg.getRaw("ah-dialog-bid-title"));
        Component details = Component.text("Current Bid: ", NamedTextColor.GRAY)
                .append(Component.text(listing.hasBids() ? vault.format(listing.getCurrentBid()) : "None", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Your Bid: ", NamedTextColor.GRAY))
                .append(Component.text(vault.format(minBid), NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("Seller: ", NamedTextColor.GRAY))
                .append(Component.text(listing.getSellerName(), NamedTextColor.GOLD));

        Component confirmText = Component.text("Place Bid", NamedTextColor.GREEN);
        Component cancelText = Component.text("Cancel", NamedTextColor.RED);

        DialogUtil.showConfirmationWithItem(player, title, listing.getItem(), details, confirmText, cancelText, () -> {
            AuctionListing fresh = plugin.getAuctionManager().getListing(listing.getId());
            if (fresh == null || !fresh.getStatus().equals("ACTIVE")) {
                msg.send(player, "ah-admin-not-found");
                return;
            }

            double freshMinBid = AuctionListingGUI.calculateMinBid(plugin, fresh);
            if (plugin.getAuctionManager().placeBid(player, fresh, freshMinBid)) {
                msg.send(player, "ah-bid-placed",
                        Placeholder.unparsed("amount", vault.format(freshMinBid)),
                        Placeholder.unparsed("item", fresh.getItemDisplayName()));

                // notify previous bidder if there was one
                if (listing.hasBids() && !listing.getCurrentBidderUuid().equals(player.getUniqueId().toString())) {
                    plugin.getNotificationManager().notify(listing.getCurrentBidderUuid(), "ah-bid-outbid",
                            "item", fresh.getItemDisplayName(),
                            "amount", vault.format(freshMinBid),
                            "refund", vault.format(listing.getCurrentBid()));
                }
            } else {
                msg.send(player, "ah-not-enough-money");
            }
        });
    }
}
