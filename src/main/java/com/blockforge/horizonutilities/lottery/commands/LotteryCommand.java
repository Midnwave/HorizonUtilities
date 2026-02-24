package com.blockforge.horizonutilities.lottery.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.lottery.LotteryInstance;
import com.blockforge.horizonutilities.lottery.LotteryManager;
import com.blockforge.horizonutilities.lottery.LotteryStorageManager;
import com.blockforge.horizonutilities.lottery.LotteryTierConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class LotteryCommand implements CommandExecutor {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final HorizonUtilitiesPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LotteryCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LotteryManager manager = plugin.getLotteryManager();

        // /lottery — list all active lotteries
        if (args.length == 0) {
            showAllLotteries(sender, manager);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // /lottery buy <tierId> [amount]
            case "buy" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can buy lottery tickets.", NamedTextColor.RED));
                    return true;
                }
                if (!player.hasPermission("horizonutilities.lottery.use")) {
                    player.sendMessage(Component.text("You don't have permission to use the lottery.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /lottery buy <tierId> [amount]", NamedTextColor.YELLOW));
                    return true;
                }
                String tierId = args[1];
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                        if (amount < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("Amount must be a positive integer.", NamedTextColor.RED));
                        return true;
                    }
                }
                manager.buyTickets(player, tierId, amount);
            }

            // /lottery info <tierId>
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /lottery info <tierId>", NamedTextColor.YELLOW));
                    return true;
                }
                String tierId = args[1];
                LotteryTierConfig cfg = manager.getTierConfigs().get(tierId);
                if (cfg == null) {
                    sender.sendMessage(Component.text("Unknown lottery tier: " + tierId, NamedTextColor.RED));
                    return true;
                }
                LotteryInstance inst = manager.getActiveInstance(tierId);
                if (inst == null) {
                    sender.sendMessage(miniMessage.deserialize("<red>No active lottery for tier: <white>" + tierId));
                    return true;
                }
                int totalTickets = manager.getStorage().getTotalTickets(inst.getId());
                int players = manager.getStorage().getDistinctPlayerCount(inst.getId());
                sender.sendMessage(miniMessage.deserialize("<gold>--- " + cfg.getDisplayName() + " <gold>---"));
                sender.sendMessage(miniMessage.deserialize("<gray>Pot: <yellow>" + plugin.getVaultHook().format(inst.getCurrentPot())));
                sender.sendMessage(miniMessage.deserialize("<gray>Ticket price: <yellow>" + plugin.getVaultHook().format(cfg.getTicketPrice())));
                sender.sendMessage(miniMessage.deserialize("<gray>Max tickets per player: <white>" + cfg.getMaxTicketsPerPlayer()));
                sender.sendMessage(miniMessage.deserialize("<gray>Min players to draw: <white>" + cfg.getMinPlayers()));
                sender.sendMessage(miniMessage.deserialize("<gray>Players entered: <white>" + players));
                sender.sendMessage(miniMessage.deserialize("<gray>Total tickets sold: <white>" + totalTickets));
                sender.sendMessage(miniMessage.deserialize("<gray>Draw in: <white>" + inst.getTimeUntilDraw()));
                if (sender instanceof Player player) {
                    int myTickets = manager.getStorage().getTicketCount(inst.getId(), player.getUniqueId());
                    sender.sendMessage(miniMessage.deserialize("<gray>Your tickets: <aqua>" + myTickets));
                }
            }

            // /lottery history
            case "history" -> {
                List<LotteryStorageManager.WinnerRecord> history = manager.getRecentHistory(10);
                if (history.isEmpty()) {
                    sender.sendMessage(Component.text("No lottery winners recorded yet.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("--- Recent Lottery Winners ---", NamedTextColor.GOLD));
                for (LotteryStorageManager.WinnerRecord record : history) {
                    String date = DATE_FMT.format(Instant.ofEpochMilli(record.drawnAt()));
                    sender.sendMessage(miniMessage.deserialize(
                            "<gray>" + date + " | " +
                            miniMessage.stripTags(record.tierDisplayName()) + " | <green>" +
                            record.winnerName() + "</green> won <yellow>" +
                            plugin.getVaultHook().format(record.pot())));
                }
            }

            // /lottery admin ...
            case "admin" -> {
                if (!sender.hasPermission("horizonutilities.lottery.admin")) {
                    sender.sendMessage(Component.text("You don't have permission for lottery admin commands.", NamedTextColor.RED));
                    return true;
                }
                handleAdmin(sender, args, manager);
            }

            default -> {
                sender.sendMessage(Component.text(
                        "Usage: /lottery [buy|info|history|admin]", NamedTextColor.YELLOW));
            }
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Admin subcommands
    // -------------------------------------------------------------------------

    private void handleAdmin(CommandSender sender, String[] args, LotteryManager manager) {
        if (args.length < 2) {
            sendAdminHelp(sender);
            return;
        }

        switch (args[1].toLowerCase()) {

            // /lottery admin draw <tierId>
            case "draw" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /lottery admin draw <tierId>", NamedTextColor.YELLOW));
                    return;
                }
                String tierId = args[2];
                if (manager.getActiveInstance(tierId) == null) {
                    sender.sendMessage(Component.text("No active lottery for tier: " + tierId, NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text("Forcing draw for tier: " + tierId, NamedTextColor.GREEN));
                manager.drawLottery(tierId);
            }

            // /lottery admin cancel <tierId>
            case "cancel" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /lottery admin cancel <tierId>", NamedTextColor.YELLOW));
                    return;
                }
                String tierId = args[2];
                if (manager.getActiveInstance(tierId) == null) {
                    sender.sendMessage(Component.text("No active lottery for tier: " + tierId, NamedTextColor.RED));
                    return;
                }
                manager.cancelLottery(tierId);
                sender.sendMessage(Component.text("Cancelled lottery for tier: " + tierId, NamedTextColor.GREEN));
            }

            // /lottery admin setpot <tierId> <amount>
            case "setpot" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /lottery admin setpot <tierId> <amount>", NamedTextColor.YELLOW));
                    return;
                }
                String tierId = args[2];
                double amount;
                try {
                    amount = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid amount: " + args[3], NamedTextColor.RED));
                    return;
                }
                if (manager.getActiveInstance(tierId) == null) {
                    sender.sendMessage(Component.text("No active lottery for tier: " + tierId, NamedTextColor.RED));
                    return;
                }
                manager.setPot(tierId, amount);
                sender.sendMessage(miniMessage.deserialize(
                        "<green>Set pot for <white>" + tierId + "</white> to <yellow>" +
                        plugin.getVaultHook().format(amount) + "</yellow>."));
            }

            // /lottery admin reload
            case "reload" -> {
                manager.reload();
                sender.sendMessage(Component.text("Lottery config reloaded.", NamedTextColor.GREEN));
            }

            default -> sendAdminHelp(sender);
        }
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- Lottery Admin ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/lottery admin draw <tierId>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/lottery admin cancel <tierId>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/lottery admin setpot <tierId> <amount>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/lottery admin reload", NamedTextColor.YELLOW));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showAllLotteries(CommandSender sender, LotteryManager manager) {
        Map<String, LotteryInstance> instances = manager.getActiveInstances();
        if (instances.isEmpty()) {
            sender.sendMessage(Component.text("There are no active lotteries right now.", NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text("--- Active Lotteries ---", NamedTextColor.GOLD));
        for (LotteryInstance inst : instances.values()) {
            LotteryTierConfig cfg = inst.getTierConfig();
            sender.sendMessage(miniMessage.deserialize(
                    cfg.getDisplayName() +
                    " <gray>| Pot: <yellow>" + plugin.getVaultHook().format(inst.getCurrentPot()) +
                    " <gray>| Ticket: <yellow>" + plugin.getVaultHook().format(cfg.getTicketPrice()) +
                    " <gray>| Draw in: <white>" + inst.getTimeUntilDraw() +
                    " <gray>| /lottery buy " + inst.getTierId()));
        }
    }
}
