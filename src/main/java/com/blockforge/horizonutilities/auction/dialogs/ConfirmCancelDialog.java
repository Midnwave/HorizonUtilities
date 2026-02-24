package com.blockforge.horizonutilities.auction.dialogs;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionListing;
import com.blockforge.horizonutilities.dialog.DialogUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class ConfirmCancelDialog {

    public static void show(Player player, AuctionListing listing) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var msg = plugin.getMessagesManager();

        Component title = Component.text(msg.getRaw("ah-dialog-cancel-title"));
        Component body = Component.text(msg.getRaw("ah-dialog-cancel-body"), NamedTextColor.GRAY);

        if (listing.hasBids()) {
            body = body.append(Component.newline())
                    .append(Component.text("Warning: This listing has bids!", NamedTextColor.RED));
        }

        Component confirmText = Component.text("Cancel Listing", NamedTextColor.RED);
        Component cancelText = Component.text("Keep Listing", NamedTextColor.GREEN);

        DialogUtil.showConfirmationWithItem(player, title, listing.getItem(), body, confirmText, cancelText, () -> {
            AuctionListing fresh = plugin.getAuctionManager().getListing(listing.getId());
            if (fresh == null || !fresh.getStatus().equals("ACTIVE")) {
                msg.send(player, "ah-admin-not-found");
                return;
            }
            plugin.getAuctionManager().cancelListing(fresh.getId());
            msg.send(player, "ah-listing-cancelled");
        });
    }
}
