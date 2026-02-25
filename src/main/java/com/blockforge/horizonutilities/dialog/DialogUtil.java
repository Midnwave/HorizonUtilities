package com.blockforge.horizonutilities.dialog;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public class DialogUtil {

    /**
     * Show a centered single-action dialog (notice style) with one button.
     */
    public static void showNotice(Player player, Component title, Component body,
                                   Component buttonText, Runnable onAcknowledge) {
        try {
            var plugin = HorizonUtilitiesPlugin.getInstance();
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(title)
                            .canCloseWithEscape(true)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(List.of(DialogBody.plainMessage(body)))
                            .build())
                    .type(DialogType.notice(
                            ActionButton.builder(buttonText)
                                    .action(DialogAction.customClick(
                                            (response, audience) -> {
                                                if (audience instanceof Player p) {
                                                    plugin.getServer().getScheduler().runTask(plugin, onAcknowledge);
                                                }
                                            },
                                            ClickCallback.Options.builder().uses(1).build()
                                    ))
                                    .build()
                    ))
            );
            player.showDialog(dialog);
        } catch (Exception e) {
            player.sendMessage(title);
            player.sendMessage(body);
            if (onAcknowledge != null) onAcknowledge.run();
        }
    }

    /**
     * Show a confirmation dialog with two centered buttons and text body.
     */
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

    /**
     * Show a confirmation dialog with item display and details.
     */
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

    /**
     * Show a confirmation dialog with item display, details, and input fields.
     * The callback receives the dialog response containing input values.
     *
     * @param player      the player to show the dialog to
     * @param title       dialog title
     * @param item        item to display
     * @param details     details text
     * @param inputs      list of dialog inputs (text fields, number ranges, etc.)
     * @param confirmText confirm button text
     * @param cancelText  cancel button text
     * @param onConfirm   called with the dialog response when the player confirms
     */
    public static void showConfirmationWithItemAndInput(Player player, Component title, ItemStack item,
                                                          Component details, List<DialogInput> inputs,
                                                          Component confirmText, Component cancelText,
                                                          Consumer<DialogResponseView> onConfirm) {
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
                            .inputs(inputs)
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.builder(confirmText)
                                    .action(DialogAction.customClick(
                                            (response, audience) -> {
                                                if (audience instanceof Player p) {
                                                    plugin.getServer().getScheduler().runTask(plugin, () -> onConfirm.accept(response));
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
        }
    }

    /**
     * Show a confirmation dialog with text body and input fields.
     *
     * @param player      the player to show the dialog to
     * @param title       dialog title
     * @param body        body text
     * @param inputs      list of dialog inputs
     * @param confirmText confirm button text
     * @param cancelText  cancel button text
     * @param onConfirm   called with the dialog response when the player confirms
     */
    public static void showConfirmationWithInput(Player player, Component title, Component body,
                                                    List<DialogInput> inputs,
                                                    Component confirmText, Component cancelText,
                                                    Consumer<DialogResponseView> onConfirm) {
        try {
            var plugin = HorizonUtilitiesPlugin.getInstance();
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(title)
                            .canCloseWithEscape(true)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(List.of(DialogBody.plainMessage(body)))
                            .inputs(inputs)
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.builder(confirmText)
                                    .action(DialogAction.customClick(
                                            (response, audience) -> {
                                                if (audience instanceof Player p) {
                                                    plugin.getServer().getScheduler().runTask(plugin, () -> onConfirm.accept(response));
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
        }
    }
}
