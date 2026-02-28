package com.blockforge.horizonutilities.gems.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.gems.GemsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GemsCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final GemsManager gemsManager;

    public GemsCommand(HorizonUtilitiesPlugin plugin, GemsManager gemsManager) {
        this.plugin = plugin;
        this.gemsManager = gemsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!gemsManager.getConfig().isEnabled()) {
            plugin.getMessagesManager().send(sender, "gems-disabled");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.getMessagesManager().send(sender, "player-only");
                return true;
            }
            showBalance(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "balance", "bal" -> {
                if (args.length >= 2 && sender.hasPermission("horizonutilities.gems.admin")) {
                    showBalanceOther(sender, args[1]);
                } else if (sender instanceof Player player) {
                    showBalance(player);
                } else {
                    plugin.getMessagesManager().send(sender, "player-only");
                }
            }
            case "pay", "send" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessagesManager().send(sender, "player-only");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text("Usage: /gems pay <player> <amount>", NamedTextColor.GRAY)));
                    return true;
                }
                handlePay(player, args[1], args[2]);
            }
            case "exchange", "buy" -> {
                if (!(sender instanceof Player player)) {
                    plugin.getMessagesManager().send(sender, "player-only");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text("Usage: /gems exchange <money-amount>", NamedTextColor.GRAY)));
                    player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text("Rate: ", NamedTextColor.GRAY))
                            .append(Component.text(gemsManager.getConfig().getExchangeRate() + " gems per $1", NamedTextColor.GOLD)));
                    if (gemsManager.getConfig().getExchangeTaxPercent() > 0) {
                        player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                                .append(Component.text("Tax: ", NamedTextColor.GRAY))
                                .append(Component.text(gemsManager.getConfig().getExchangeTaxPercent() + "%", NamedTextColor.RED)));
                    }
                    return true;
                }
                handleExchange(player, args[1]);
            }
            case "top", "leaderboard" -> handleTop(sender);
            case "admin" -> {
                if (!sender.hasPermission("horizonutilities.gems.admin")) {
                    plugin.getMessagesManager().send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    showAdminUsage(sender);
                    return true;
                }
                handleAdmin(sender, args);
            }
            default -> showUsage(sender);
        }
        return true;
    }

    private void showBalance(Player player) {
        double balance = gemsManager.getBalance(player.getUniqueId());
        player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Your balance: ", NamedTextColor.GRAY))
                .append(Component.text(GemsManager.formatGems(balance), NamedTextColor.LIGHT_PURPLE)));
    }

    private void showBalanceOther(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "player-not-found");
            return;
        }
        double balance = gemsManager.getBalance(target.getUniqueId());
        sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(target.getName() + "'s balance: ", NamedTextColor.GRAY))
                .append(Component.text(GemsManager.formatGems(balance), NamedTextColor.LIGHT_PURPLE)));
    }

    private void handlePay(Player player, String targetName, String amountStr) {
        if (!player.hasPermission("horizonutilities.gems.pay")) {
            plugin.getMessagesManager().send(player, "no-permission");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.getMessagesManager().send(player, "player-not-found");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("You can't send gems to yourself.", NamedTextColor.RED)));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Invalid amount.", NamedTextColor.RED)));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Amount must be positive.", NamedTextColor.RED)));
            return;
        }

        amount = Math.floor(amount * 100) / 100; // round to 2 decimals

        if (!gemsManager.transfer(player.getUniqueId(), target.getUniqueId(), amount)) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Transfer failed. Check your balance.", NamedTextColor.RED)));
            return;
        }

        player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Sent ", NamedTextColor.GREEN))
                .append(Component.text(GemsManager.formatGems(amount), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" to " + target.getName() + ".", NamedTextColor.GREEN)));

        target.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Received ", NamedTextColor.GREEN))
                .append(Component.text(GemsManager.formatGems(amount), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" from " + player.getName() + ".", NamedTextColor.GREEN)));
    }

    private void handleExchange(Player player, String amountStr) {
        if (!player.hasPermission("horizonutilities.gems.exchange")) {
            plugin.getMessagesManager().send(player, "no-permission");
            return;
        }

        if (!gemsManager.getConfig().isExchangeEnabled()) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Gem exchange is disabled.", NamedTextColor.RED)));
            return;
        }

        double moneyAmount;
        try {
            moneyAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Invalid amount.", NamedTextColor.RED)));
            return;
        }

        if (moneyAmount < gemsManager.getConfig().getExchangeMinAmount()) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Minimum exchange: ", NamedTextColor.RED))
                    .append(Component.text(plugin.getVaultHook().format(gemsManager.getConfig().getExchangeMinAmount()), NamedTextColor.GOLD)));
            return;
        }

        if (moneyAmount > gemsManager.getConfig().getExchangeMaxAmount()) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Maximum exchange: ", NamedTextColor.RED))
                    .append(Component.text(plugin.getVaultHook().format(gemsManager.getConfig().getExchangeMaxAmount()), NamedTextColor.GOLD)));
            return;
        }

        if (!plugin.getVaultHook().has(player, moneyAmount)) {
            plugin.getMessagesManager().send(player, "ah-not-enough-money");
            return;
        }

        double taxPercent = gemsManager.getConfig().getExchangeTaxPercent();
        double taxAmount = moneyAmount * (taxPercent / 100.0);
        double effectiveAmount = moneyAmount - taxAmount;
        double gemsToGrant = Math.floor(effectiveAmount * gemsManager.getConfig().getExchangeRate() * 100) / 100;

        if (!gemsManager.exchangeMoneyToGems(player, moneyAmount)) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Exchange failed.", NamedTextColor.RED)));
            return;
        }

        player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Exchanged ", NamedTextColor.GREEN))
                .append(Component.text(plugin.getVaultHook().format(moneyAmount), NamedTextColor.GOLD))
                .append(Component.text(" for ", NamedTextColor.GREEN))
                .append(Component.text(GemsManager.formatGems(gemsToGrant), NamedTextColor.LIGHT_PURPLE)));
        if (taxAmount > 0) {
            player.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Tax: ", NamedTextColor.GRAY))
                    .append(Component.text(plugin.getVaultHook().format(taxAmount), NamedTextColor.RED)));
        }
    }

    private void handleTop(CommandSender sender) {
        List<Map.Entry<String, Double>> top = gemsManager.getStorage().getTopBalances(10);
        if (top.isEmpty()) {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("No data yet.", NamedTextColor.GRAY)));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  Gems Leaderboard", NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.empty());

        for (int i = 0; i < top.size(); i++) {
            var entry = top.get(i);
            NamedTextColor rankColor = switch (i) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.GRAY;
                case 2 -> NamedTextColor.DARK_RED;
                default -> NamedTextColor.DARK_GRAY;
            };
            sender.sendMessage(Component.text("  " + (i + 1) + ". ", rankColor)
                    .append(Component.text(entry.getKey(), NamedTextColor.WHITE))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(GemsManager.formatGems(entry.getValue()), NamedTextColor.LIGHT_PURPLE)));
        }
        sender.sendMessage(Component.empty());
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showAdminUsage(sender);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "give" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text("Usage: /gems admin give <player> <amount>", NamedTextColor.GRAY)));
                    return;
                }
                adminGive(sender, args[2], args[3]);
            }
            case "take" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text("Usage: /gems admin take <player> <amount>", NamedTextColor.GRAY)));
                    return;
                }
                adminTake(sender, args[2], args[3]);
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text("Usage: /gems admin set <player> <amount>", NamedTextColor.GRAY)));
                    return;
                }
                adminSet(sender, args[2], args[3]);
            }
            case "reload" -> {
                gemsManager.getConfig().load();
                sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("Configuration reloaded.", NamedTextColor.GREEN)));
            }
            default -> showAdminUsage(sender);
        }
    }

    private void adminGive(CommandSender sender, String targetName, String amountStr) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "player-not-found");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Invalid amount.", NamedTextColor.RED)));
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Amount must be positive.", NamedTextColor.RED)));
            return;
        }

        String reason = "admin give by " + sender.getName();
        if (gemsManager.deposit(target.getUniqueId(), amount, reason)) {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Gave ", NamedTextColor.GREEN))
                    .append(Component.text(GemsManager.formatGems(amount), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" to " + target.getName() + ".", NamedTextColor.GREEN)));
            target.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("You received ", NamedTextColor.GREEN))
                    .append(Component.text(GemsManager.formatGems(amount), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" from an admin.", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Failed to give gems.", NamedTextColor.RED)));
        }
    }

    private void adminTake(CommandSender sender, String targetName, String amountStr) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "player-not-found");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Invalid amount.", NamedTextColor.RED)));
            return;
        }

        String reason = "admin take by " + sender.getName();
        if (gemsManager.withdraw(target.getUniqueId(), amount, reason)) {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Took ", NamedTextColor.GREEN))
                    .append(Component.text(GemsManager.formatGems(amount), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" from " + target.getName() + ".", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Failed — player may not have enough gems.", NamedTextColor.RED)));
        }
    }

    private void adminSet(CommandSender sender, String targetName, String amountStr) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.getMessagesManager().send(sender, "player-not-found");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Invalid amount.", NamedTextColor.RED)));
            return;
        }

        String reason = "admin set by " + sender.getName();
        if (gemsManager.setBalance(target.getUniqueId(), amount, reason)) {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Set " + target.getName() + "'s gems to ", NamedTextColor.GREEN))
                    .append(Component.text(GemsManager.formatGems(amount), NamedTextColor.LIGHT_PURPLE)));
        } else {
            sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Failed to set balance.", NamedTextColor.RED)));
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Commands:", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /gems", NamedTextColor.GOLD)
                .append(Component.text(" — View your balance", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /gems pay <player> <amount>", NamedTextColor.GOLD)
                .append(Component.text(" — Send gems", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /gems exchange <money>", NamedTextColor.GOLD)
                .append(Component.text(" — Buy gems with money", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /gems top", NamedTextColor.GOLD)
                .append(Component.text(" — Leaderboard", NamedTextColor.GRAY)));
        if (sender.hasPermission("horizonutilities.gems.admin")) {
            sender.sendMessage(Component.text("  /gems admin", NamedTextColor.GOLD)
                    .append(Component.text(" — Admin commands", NamedTextColor.GRAY)));
        }
    }

    private void showAdminUsage(CommandSender sender) {
        sender.sendMessage(Component.text("[Gems] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Admin Commands:", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /gems admin give <player> <amount>", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /gems admin take <player> <amount>", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /gems admin set <player> <amount>", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /gems admin reload", NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("balance", "pay", "exchange", "top"));
            if (sender.hasPermission("horizonutilities.gems.admin")) {
                completions.add("admin");
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "pay", "send", "balance", "bal" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                }
                case "admin" -> {
                    if (sender.hasPermission("horizonutilities.gems.admin")) {
                        completions.addAll(List.of("give", "take", "set", "reload"));
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && sender.hasPermission("horizonutilities.gems.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")
                && sender.hasPermission("horizonutilities.gems.admin")) {
            completions.addAll(List.of("10", "50", "100", "500", "1000"));
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream().filter(c -> c.toLowerCase().startsWith(lastArg)).toList();
    }
}
