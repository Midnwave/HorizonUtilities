package com.blockforge.horizonutilities.auction.dialogs;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionListing;
import com.blockforge.horizonutilities.dialog.DialogUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class ConfirmPurchaseDialog {

    public static void show(Player player, AuctionListing listing) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var vault = plugin.getVaultHook();
        var msg = plugin.getMessagesManager();

        Component title = Component.text(msg.getRaw("ah-dialog-confirm-title"));
        Component details = Component.text("Buy It Now: ", NamedTextColor.GRAY)
                .append(Component.text(vault.format(listing.getBuyoutPrice()), NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Seller: ", NamedTextColor.GRAY))
                .append(Component.text(listing.getSellerName(), NamedTextColor.GOLD));
        Component confirmText = Component.text(msg.getRaw("ah-dialog-confirm-yes"), NamedTextColor.GREEN);
        Component cancelText = Component.text(msg.getRaw("ah-dialog-confirm-no"), NamedTextColor.RED);

        DialogUtil.showConfirmationWithItem(player, title, listing.getItem(), details, confirmText, cancelText, () -> {
            // re-fetch to ensure still available
            AuctionListing fresh = plugin.getAuctionManager().getListing(listing.getId());
            if (fresh == null || !fresh.getStatus().equals("ACTIVE")) {
                msg.send(player, "ah-admin-not-found");
                return;
            }
            if (plugin.getAuctionManager().buyNow(player, fresh)) {
                msg.send(player, "ah-item-purchased",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("item", fresh.getItemDisplayName()),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("price", vault.format(fresh.getBuyoutPrice())));
            } else {
                msg.send(player, "ah-not-enough-money");
            }
        });
    }
}
