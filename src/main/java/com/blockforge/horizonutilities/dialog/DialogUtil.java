package com.blockforge.horizonutilities.dialog;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class DialogUtil {

    public static void showConfirmation(Player player, Component title, Component body,
                                         Component confirmText, Component cancelText,
                                         Runnable onConfirm) {
        try {
            var plugin = HorizonUtilitiesPlugin.getInstance();
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(title)
                            .canCloseWithEscape(true)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(List.of(DialogBody.plainMessage(body)))
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.builder(confirmText)
                                    .action(DialogAction.customClick(
                                            (response, audience) -> {
                                                if (audience instanceof Player p) {
                                                    plugin.getServer().getScheduler().runTask(plugin, onConfirm);
                                                }
                                            },
                                            ClickCallback.Options.builder().uses(1).build()
                                    ))
                                    .build(),
                            ActionButton.builder(cancelText).build()
                    ))
            );
            player.showDialog(dialog);
        } catch (Exception e) {
            player.sendMessage(title);
            player.sendMessage(body);
            onConfirm.run();
        }
    }

    public static void showConfirmationWithItem(Player player, Component title, ItemStack item,
                                                  Component details, Component confirmText,
                                                  Component cancelText, Runnable onConfirm) {
        try {
            var plugin = HorizonUtilitiesPlugin.getInstance();
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(title)
                            .canCloseWithEscape(true)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(List.of(
                                    DialogBody.item(item).build(),
                                    DialogBody.plainMessage(details)
                            ))
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.builder(confirmText)
                                    .action(DialogAction.customClick(
                                            (response, audience) -> {
                                                if (audience instanceof Player p) {
                                                    plugin.getServer().getScheduler().runTask(plugin, onConfirm);
                                                }
                                            },
                                            ClickCallback.Options.builder().uses(1).build()
                                    ))
                                    .build(),
                            ActionButton.builder(cancelText).build()
                    ))
            );
            player.showDialog(dialog);
        } catch (Exception e) {
            player.sendMessage(title);
            onConfirm.run();
        }
    }
}
