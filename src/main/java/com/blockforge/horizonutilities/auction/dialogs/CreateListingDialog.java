package com.blockforge.horizonutilities.auction.dialogs;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.dialog.DialogUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CreateListingDialog {

    public static void show(Player player, ItemStack item, double startPrice, Double buyoutPrice, int durationHours) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var vault = plugin.getVaultHook();
        var msg = plugin.getMessagesManager();
        var cfg = plugin.getAuctionHouseConfig();

        double fee = startPrice * (cfg.getListingFeePercent() / 100.0);
        if (player.hasPermission("horizonutilities.ah.bypass.fee")) fee = 0;

        String buyoutStr = (buyoutPrice != null && buyoutPrice > 0) ? vault.format(buyoutPrice) : "None";
        double finalFee = fee;

        Component title = Component.text("Create Listing", NamedTextColor.DARK_PURPLE);
        Component details = Component.text("Start Price: ", NamedTextColor.GRAY)
                .append(Component.text(vault.format(startPrice), NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Buy It Now: ", NamedTextColor.GRAY))
                .append(Component.text(buyoutStr, NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Duration: ", NamedTextColor.GRAY))
                .append(Component.text(durationHours + "h", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Listing Fee: ", NamedTextColor.GRAY))
                .append(Component.text(vault.format(finalFee), NamedTextColor.RED));

        Component confirmText = Component.text("Confirm Listing", NamedTextColor.GREEN);
        Component cancelText = Component.text("Cancel", NamedTextColor.RED);

        DialogUtil.showConfirmationWithItem(player, title, item, details, confirmText, cancelText, () -> {
            // remove item from hand
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                msg.send(player, "ah-nothing-in-hand");
                return;
            }

            int id = plugin.getAuctionManager().createListing(player, held, startPrice, buyoutPrice, durationHours);
            if (id > 0) {
                player.getInventory().setItemInMainHand(null);
                msg.send(player, "ah-listing-created",
                        Placeholder.unparsed("price", vault.format(startPrice)),
                        Placeholder.unparsed("fee", vault.format(finalFee)));
            } else {
                msg.send(player, "ah-not-enough-money");
            }
        });
    }
}
