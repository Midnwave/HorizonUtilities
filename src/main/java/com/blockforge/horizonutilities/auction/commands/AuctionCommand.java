package com.blockforge.horizonutilities.auction.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionListing;
import com.blockforge.horizonutilities.auction.dialogs.CreateListingDialog;
import com.blockforge.horizonutilities.auction.gui.AuctionCollectionGUI;
import com.blockforge.horizonutilities.auction.gui.AuctionHistoryGUI;
import com.blockforge.horizonutilities.auction.gui.AuctionMainGUI;
import com.blockforge.horizonutilities.auction.gui.PriceHistoryGUI;
import com.blockforge.horizonutilities.auction.listeners.AuctionGUIListener;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuctionCommand implements CommandExecutor {

    private final HorizonUtilitiesPlugin plugin;
    private final Map<UUID, Long> lastListingTime = new HashMap<>();

    public AuctionCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var msg = plugin.getMessagesManager();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return true;
        }

        if (plugin.getAuctionManager().isBanned(player.getUniqueId())) {
            msg.send(player, "ah-banned");
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("horizonutilities.ah.use")) {
                msg.send(player, "no-permission");
                return true;
            }
            AuctionGUIListener.setGUI(player.getUniqueId(), AuctionGUIListener.GUIType.MAIN);
            AuctionMainGUI.open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "sell" -> handleSell(player, args);
            case "search" -> handleSearch(player, args);
            case "collect" -> handleCollect(player);
            case "history" -> handleHistory(player);
            case "stats" -> handleStats(player, args);
            case "admin" -> handleAdmin(player, args);
            case "reload" -> handleReload(player);
            default -> {
                AuctionGUIListener.setGUI(player.getUniqueId(), AuctionGUIListener.GUIType.MAIN);
                AuctionMainGUI.open(player);
                yield true;
            }
        };
    }

    private boolean handleSell(Player player, String[] args) {
        var msg = plugin.getMessagesManager();
        var cfg = plugin.getAuctionHouseConfig();
        var vault = plugin.getVaultHook();

        if (!player.hasPermission("horizonutilities.ah.list")) {
            msg.send(player, "no-permission");
            return true;
        }

        if (!vault.isAvailable()) {
            msg.send(player, "no-economy");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMessagesManager().format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Usage: /ah sell <price> [buyout] [duration]")));
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            msg.send(player, "ah-nothing-in-hand");
            return true;
        }

        if (cfg.isBlacklisted(held.getType())) {
            msg.send(player, "ah-blacklisted");
            return true;
        }

        // check listing limit
        int current = plugin.getAuctionManager().countPlayerListings(player.getUniqueId());
        int max = plugin.getAuctionManager().getMaxListings(player);
        if (current >= max) {
            msg.send(player, "ah-max-listings", Placeholder.unparsed("limit", String.valueOf(max)));
            return true;
        }

        // cooldown check
        if (!player.hasPermission("horizonutilities.ah.bypass.cooldown")) {
            Long lastTime = lastListingTime.get(player.getUniqueId());
            if (lastTime != null) {
                long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
                if (elapsed < cfg.getListingCooldownSeconds()) {
                    msg.send(player, "ah-listing-cooldown",
                            Placeholder.unparsed("seconds", String.valueOf(cfg.getListingCooldownSeconds() - elapsed)));
                    return true;
                }
            }
        }

        double startPrice;
        try {
            startPrice = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessagesManager().format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Invalid price.")));
            return true;
        }

        if (startPrice < cfg.getMinPrice()) {
            msg.send(player, "ah-price-too-low", Placeholder.unparsed("min", vault.format(cfg.getMinPrice())));
            return true;
        }
        if (startPrice > cfg.getMaxPrice()) {
            msg.send(player, "ah-price-too-high", Placeholder.unparsed("max", vault.format(cfg.getMaxPrice())));
            return true;
        }

        Double buyoutPrice = null;
        if (args.length >= 3) {
            try {
                buyoutPrice = Double.parseDouble(args[2]);
                if (buyoutPrice <= 0) buyoutPrice = null;
                else if (buyoutPrice < startPrice) buyoutPrice = startPrice;
                else if (buyoutPrice > cfg.getMaxPrice()) {
                    msg.send(player, "ah-price-too-high", Placeholder.unparsed("max", vault.format(cfg.getMaxPrice())));
                    return true;
                }
            } catch (NumberFormatException e) {
                buyoutPrice = null;
            }
        }

        int duration = cfg.getDefaultDuration();
        if (args.length >= 4) {
            try {
                String durationArg = args[3].toLowerCase().replace("h", "");
                int d = Integer.parseInt(durationArg);
                if (cfg.getDurations().contains(d)) duration = d;
            } catch (NumberFormatException ignored) {}
        }

        lastListingTime.put(player.getUniqueId(), System.currentTimeMillis());
        CreateListingDialog.show(player, held, startPrice, buyoutPrice, duration);
        return true;
    }

    private boolean handleSearch(Player player, String[] args) {
        var msg = plugin.getMessagesManager();
        if (!player.hasPermission("horizonutilities.ah.use")) {
            msg.send(player, "no-permission");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMessagesManager().format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Usage: /ah search <query>")));
            return true;
        }

        String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        AuctionGUIListener.setGUI(player.getUniqueId(), AuctionGUIListener.GUIType.MAIN);
        AuctionMainGUI.open(player, "All", 0, query, AuctionMainGUI.SortType.NEWEST, false);
        return true;
    }

    private boolean handleCollect(Player player) {
        if (!player.hasPermission("horizonutilities.ah.use")) {
            plugin.getMessagesManager().send(player, "no-permission");
            return true;
        }
        AuctionGUIListener.setGUI(player.getUniqueId(), AuctionGUIListener.GUIType.COLLECTION);
        AuctionCollectionGUI.open(player, 0);
        return true;
    }

    private boolean handleHistory(Player player) {
        if (!player.hasPermission("horizonutilities.ah.use")) {
            plugin.getMessagesManager().send(player, "no-permission");
            return true;
        }
        AuctionGUIListener.setGUI(player.getUniqueId(), AuctionGUIListener.GUIType.HISTORY);
        AuctionHistoryGUI.open(player, 0);
        return true;
    }

    private boolean handleStats(Player player, String[] args) {
        if (!player.hasPermission("horizonutilities.ah.use")) {
            plugin.getMessagesManager().send(player, "no-permission");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMessagesManager().format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Usage: /ah stats <material>")));
            return true;
        }

        String material = args[1].toUpperCase();
        AuctionGUIListener.setGUI(player.getUniqueId(), AuctionGUIListener.GUIType.PRICE_HISTORY);
        PriceHistoryGUI.open(player, material);
        return true;
    }

    private boolean handleAdmin(Player player, String[] args) {
        var msg = plugin.getMessagesManager();
        if (!player.hasPermission("horizonutilities.ah.admin")) {
            msg.send(player, "no-permission");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(msg.format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Usage: /ah admin <remove|ban|unban|transactions> ...")));
            return true;
        }

        String adminSub = args[1].toLowerCase();
        return switch (adminSub) {
            case "remove" -> adminRemove(player, args);
            case "ban" -> adminBan(player, args);
            case "unban" -> adminUnban(player, args);
            case "transactions" -> adminTransactions(player, args);
            default -> {
                player.sendMessage(msg.format("prefix")
                        .append(net.kyori.adventure.text.Component.text(" Unknown admin command.")));
                yield true;
            }
        };
    }

    private boolean adminRemove(Player player, String[] args) {
        var msg = plugin.getMessagesManager();
        if (args.length < 3) {
            player.sendMessage(msg.format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Usage: /ah admin remove <id>")));
            return true;
        }

        try {
            int id = Integer.parseInt(args[2]);
            AuctionListing listing = plugin.getAuctionManager().getListing(id);
            if (listing == null || !listing.getStatus().equals("ACTIVE")) {
                msg.send(player, "ah-admin-not-found");
                return true;
            }
            plugin.getAuctionManager().cancelListing(id);
            msg.send(player, "ah-admin-removed", Placeholder.unparsed("id", String.valueOf(id)));
        } catch (NumberFormatException e) {
            player.sendMessage(msg.format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Invalid listing ID.")));
        }
        return true;
    }

    private boolean adminBan(Player player, String[] args) {
        var msg = plugin.getMessagesManager();
        if (args.length < 3) {
            player.sendMessage(msg.format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Usage: /ah admin ban <player>")));
            return true;
        }

        var target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            msg.send(player, "ah-admin-player-not-found");
            return true;
        }

        String reason = args.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "No reason";
        plugin.getAuctionManager().ban(target.getUniqueId(), player.getName(), reason);
        msg.send(player, "ah-admin-banned", Placeholder.unparsed("player", args[2]));
        return true;
    }

    private boolean adminUnban(Player player, String[] args) {
        var msg = plugin.getMessagesManager();
        if (args.length < 3) {
            player.sendMessage(msg.format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Usage: /ah admin unban <player>")));
            return true;
        }

        var target = Bukkit.getOfflinePlayer(args[2]);
        plugin.getAuctionManager().unban(target.getUniqueId());
        msg.send(player, "ah-admin-unbanned", Placeholder.unparsed("player", args[2]));
        return true;
    }

    private boolean adminTransactions(Player player, String[] args) {
        var msg = plugin.getMessagesManager();
        if (args.length < 3) {
            player.sendMessage(msg.format("prefix")
                    .append(net.kyori.adventure.text.Component.text(" Usage: /ah admin transactions <player>")));
            return true;
        }

        var target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            msg.send(player, "ah-admin-player-not-found");
            return true;
        }

        // open history GUI for the target player's transactions
        // reuse the history GUI but with the target's UUID
        AuctionHistoryGUI.open(player, 0);
        return true;
    }

    private boolean handleReload(Player player) {
        var msg = plugin.getMessagesManager();
        if (!player.hasPermission("horizonutilities.ah.admin")) {
            msg.send(player, "no-permission");
            return true;
        }
        plugin.reloadAllConfigs();
        msg.send(player, "reload-success");
        return true;
    }
}
