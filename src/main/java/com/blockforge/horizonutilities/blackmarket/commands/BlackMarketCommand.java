package com.blockforge.horizonutilities.blackmarket.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.blackmarket.BlackMarketItem;
import com.blockforge.horizonutilities.blackmarket.BlackMarketManager;
import com.blockforge.horizonutilities.blackmarket.gui.BlackMarketGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BlackMarketCommand implements CommandExecutor {

    private final HorizonUtilitiesPlugin plugin;

    public BlackMarketCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        BlackMarketManager manager = plugin.getBlackMarketManager();

        // /bm  (no args) — open GUI for players
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open the Black Market GUI.", NamedTextColor.RED));
                return true;
            }
            if (!player.hasPermission("horizonutilities.blackmarket.use")) {
                player.sendMessage(Component.text("You don't have permission to use the Black Market.", NamedTextColor.RED));
                return true;
            }
            new BlackMarketGUI(plugin, player, manager).open();
            return true;
        }

        // /bm admin ...
        if (args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("horizonutilities.blackmarket.admin")) {
                sender.sendMessage(Component.text("You don't have permission to use Black Market admin commands.", NamedTextColor.RED));
                return true;
            }
            return handleAdmin(sender, args, manager);
        }

        sender.sendMessage(Component.text("Usage: /bm [admin <subcommand>]", NamedTextColor.YELLOW));
        return true;
    }

    // -------------------------------------------------------------------------
    // Admin subcommands
    // -------------------------------------------------------------------------

    private boolean handleAdmin(CommandSender sender, String[] args, BlackMarketManager manager) {
        if (args.length < 2) {
            sendAdminHelp(sender);
            return true;
        }

        switch (args[1].toLowerCase()) {

            case "additem" -> {
                // /bm admin additem <id> <material> <price> <category>
                if (args.length < 6) {
                    sender.sendMessage(Component.text(
                            "Usage: /bm admin additem <id> <material> <price> <category>", NamedTextColor.YELLOW));
                    return true;
                }
                String id = args[2];
                Material material;
                try {
                    material = Material.valueOf(args[3].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Unknown material: " + args[3], NamedTextColor.RED));
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid price: " + args[4], NamedTextColor.RED));
                    return true;
                }
                String category = args[5];
                BlackMarketItem item = new BlackMarketItem(
                        id, "<white>" + id, material, price, 1, category,
                        java.util.Collections.emptyList(), -1, false,
                        java.util.Collections.emptyList(), 1, 0,
                        java.util.Collections.emptyMap());
                manager.addItem(item);
                sender.sendMessage(Component.text(
                        "Added item '" + id + "' to the Black Market.", NamedTextColor.GREEN));
            }

            case "removeitem" -> {
                // /bm admin removeitem <id>
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /bm admin removeitem <id>", NamedTextColor.YELLOW));
                    return true;
                }
                String id = args[2];
                if (manager.getItemById(id) == null) {
                    sender.sendMessage(Component.text("No item found with id: " + id, NamedTextColor.RED));
                    return true;
                }
                manager.removeItem(id);
                sender.sendMessage(Component.text("Removed item '" + id + "' from the Black Market.", NamedTextColor.GREEN));
            }

            case "setprice" -> {
                // /bm admin setprice <id> <price>
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /bm admin setprice <id> <price>", NamedTextColor.YELLOW));
                    return true;
                }
                String id = args[2];
                if (manager.getItemById(id) == null) {
                    sender.sendMessage(Component.text("No item found with id: " + id, NamedTextColor.RED));
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid price: " + args[3], NamedTextColor.RED));
                    return true;
                }
                manager.setPrice(id, price);
                sender.sendMessage(Component.text(
                        "Updated price of '" + id + "' to " + plugin.getVaultHook().format(price) + ".",
                        NamedTextColor.GREEN));
            }

            case "list" -> {
                List<BlackMarketItem> items = manager.getAllItems();
                if (items.isEmpty()) {
                    sender.sendMessage(Component.text("The Black Market has no items.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("--- Black Market Items ---", NamedTextColor.GOLD));
                for (BlackMarketItem item : items) {
                    sender.sendMessage(Component.text(
                            item.getId() + " [" + item.getCategory() + "] — "
                                    + plugin.getVaultHook().format(item.getPrice()),
                            NamedTextColor.GRAY));
                }
            }

            case "reload" -> {
                manager.reload();
                sender.sendMessage(Component.text("Black Market config reloaded.", NamedTextColor.GREEN));
            }

            default -> sendAdminHelp(sender);
        }

        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- Black Market Admin ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/bm admin additem <id> <material> <price> <category>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bm admin removeitem <id>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bm admin setprice <id> <price>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bm admin list", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/bm admin reload", NamedTextColor.YELLOW));
    }
}
