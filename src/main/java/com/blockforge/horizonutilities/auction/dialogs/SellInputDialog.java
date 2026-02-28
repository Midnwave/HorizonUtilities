package com.blockforge.horizonutilities.auction.dialogs;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.util.BedrockUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Dialog-based sell flow for /ah sell (no args).
 * Opens a dialog with input fields for price, buyout price, and duration.
 * Falls back to a chat prompt for Bedrock/ViaBackwards clients.
 */
public class SellInputDialog {

    public static void show(Player player, ItemStack item) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var cfg = plugin.getAuctionHouseConfig();
        var vault = plugin.getVaultHook();

        // If Bedrock or dialog unavailable, fall back to chat prompt
        if (BedrockUtil.isBedrock(player)) {
            showChatPrompt(player);
            return;
        }

        try {
            String defaultPrice = String.format("%.2f", cfg.getMinPrice());
            String defaultDuration = String.valueOf(cfg.getDefaultDuration());

            DialogInput priceInput = DialogInput.text("start_price", Component.text("Start Price"))
                    .width(200).initial(defaultPrice).maxLength(20).build();
            DialogInput buyoutInput = DialogInput.text("buyout_price", Component.text("Buy It Now (0 = none)"))
                    .width(200).initial("0").maxLength(20).build();
            DialogInput durationInput = DialogInput.text("duration_hours", Component.text("Duration (hours)"))
                    .width(200).initial(defaultDuration).maxLength(5).build();

            Component body = Component.text("Item: ", NamedTextColor.GRAY)
                    .append(Component.text(item.getType().name(), NamedTextColor.GOLD))
                    .append(Component.text(" x" + item.getAmount(), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Min Price: " + vault.format(cfg.getMinPrice()) +
                            " | Max: " + vault.format(cfg.getMaxPrice()), NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("Durations: " + cfg.getDurations() + "h", NamedTextColor.GRAY));

            Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Create Auction Listing", NamedTextColor.DARK_PURPLE))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(
                        DialogBody.item(item).build(),
                        DialogBody.plainMessage(body)))
                    .inputs(List.of(priceInput, buyoutInput, durationInput))
                    .build())
                .type(DialogType.confirmation(
                    ActionButton.builder(Component.text("List Item", NamedTextColor.GREEN))
                        .width(150)
                        .action(DialogAction.customClick(
                            (response, audience) -> {
                                if (audience instanceof Player p) {
                                    plugin.getServer().getScheduler().runTask(plugin, () ->
                                        processDialogSell(p, response.getText("start_price"),
                                            response.getText("buyout_price"),
                                            response.getText("duration_hours")));
                                }
                            },
                            ClickCallback.Options.builder().uses(1).build()
                        )).build(),
                    ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                        .width(150).build()
                ))
            );
            player.showDialog(dialog);
        } catch (Exception e) {
            // Dialog API not available — fall back to chat prompt
            showChatPrompt(player);
        }
    }

    private static void processDialogSell(Player player, String priceStr, String buyoutStr, String durationStr) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var msg = plugin.getMessagesManager();
        var cfg = plugin.getAuctionHouseConfig();
        var vault = plugin.getVaultHook();

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            msg.send(player, "ah-nothing-in-hand");
            return;
        }

        // Parse start price
        double startPrice;
        try {
            startPrice = Double.parseDouble(priceStr != null ? priceStr.trim() : "");
        } catch (NumberFormatException e) {
            msg.send(player, "ah-invalid-price");
            return;
        }
        if (startPrice < cfg.getMinPrice()) {
            msg.send(player, "ah-price-too-low", Placeholder.unparsed("min", vault.format(cfg.getMinPrice())));
            return;
        }
        if (startPrice > cfg.getMaxPrice()) {
            msg.send(player, "ah-price-too-high", Placeholder.unparsed("max", vault.format(cfg.getMaxPrice())));
            return;
        }

        // Parse buyout
        Double buyoutPrice = null;
        try {
            double b = Double.parseDouble(buyoutStr != null ? buyoutStr.trim() : "0");
            if (b > 0) {
                if (b < startPrice) b = startPrice;
                if (b > cfg.getMaxPrice()) {
                    msg.send(player, "ah-price-too-high", Placeholder.unparsed("max", vault.format(cfg.getMaxPrice())));
                    return;
                }
                buyoutPrice = b;
            }
        } catch (NumberFormatException ignored) {}

        // Parse duration
        int duration = cfg.getDefaultDuration();
        try {
            String cleaned = (durationStr != null ? durationStr.trim() : "").toLowerCase().replace("h", "");
            int d = Integer.parseInt(cleaned);
            if (cfg.getDurations().contains(d)) duration = d;
        } catch (NumberFormatException ignored) {}

        // Show the confirmation dialog
        CreateListingDialog.show(player, held, startPrice, buyoutPrice, duration);
    }

    private static void showChatPrompt(Player player) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        player.sendMessage(plugin.getMessagesManager().format("prefix")
                .append(Component.text(" Usage: ", NamedTextColor.GRAY))
                .append(Component.text("/ah sell <price> [buyout] [duration]", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  Example: /ah sell 100 200 24h", NamedTextColor.DARK_GRAY));
    }
}
