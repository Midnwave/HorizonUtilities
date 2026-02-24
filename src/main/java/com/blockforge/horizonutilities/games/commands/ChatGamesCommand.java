package com.blockforge.horizonutilities.games.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.games.LeaderboardManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ChatGamesCommand implements CommandExecutor {

    private final HorizonUtilitiesPlugin plugin;

    public ChatGamesCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender);
            case "top" -> handleTop(sender);
            case "stats" -> handleStats(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("horizonutilities.chatgames.admin")) {
            plugin.getMessagesManager().send(sender, "no-permission");
            return;
        }
        if (plugin.getChatGameManager().isGameActive()) {
            plugin.getMessagesManager().send(sender, "game-already-active");
            return;
        }

        boolean started;
        if (args.length > 1) {
            started = plugin.getChatGameManager().startGame(args[1]);
        } else {
            started = plugin.getChatGameManager().startRandomGame();
        }

        if (started) {
            plugin.getMessagesManager().send(sender, "game-started-admin",
                    Placeholder.unparsed("type", args.length > 1 ? args[1] : "random"));
        }
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("horizonutilities.chatgames.admin")) {
            plugin.getMessagesManager().send(sender, "no-permission");
            return;
        }
        if (!plugin.getChatGameManager().isGameActive()) {
            plugin.getMessagesManager().send(sender, "game-none-active");
            return;
        }
        plugin.getChatGameManager().stopCurrentGame();
        plugin.getMessagesManager().send(sender, "game-stopped");
    }

    private void handleTop(CommandSender sender) {
        if (!sender.hasPermission("horizonutilities.chatgames.top")) {
            plugin.getMessagesManager().send(sender, "no-permission");
            return;
        }

        if (sender instanceof Player player) {
            new com.blockforge.horizonutilities.games.gui.LeaderboardGUI(plugin, player).open();
            return;
        }

        List<LeaderboardManager.LeaderboardEntry> top = plugin.getLeaderboardManager().getTop(10);
        if (top.isEmpty()) {
            plugin.getMessagesManager().send(sender, "leaderboard-empty");
            return;
        }

        sender.sendMessage(plugin.getMessagesManager().format("leaderboard-title"));
        for (int i = 0; i < top.size(); i++) {
            var entry = top.get(i);
            plugin.getMessagesManager().send(sender, "leaderboard-entry",
                    Placeholder.unparsed("rank", String.valueOf(i + 1)),
                    Placeholder.unparsed("player", entry.playerName()),
                    Placeholder.unparsed("wins", String.valueOf(entry.wins())),
                    Placeholder.unparsed("streak", String.valueOf(entry.bestStreak())));
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("horizonutilities.chatgames.top")) {
            plugin.getMessagesManager().send(sender, "no-permission");
            return;
        }

        UUID targetUuid;
        String targetName;

        if (args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                targetUuid = target.getUniqueId();
                targetName = target.getName();
            } else {
                @SuppressWarnings("deprecation")
                var offline = Bukkit.getOfflinePlayer(args[1]);
                targetUuid = offline.getUniqueId();
                targetName = offline.getName() != null ? offline.getName() : args[1];
            }
        } else if (sender instanceof Player player) {
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage("Usage: /chatgames stats <player>");
            return;
        }

        var stats = plugin.getLeaderboardManager().getStats(targetUuid);
        if (stats == null) {
            plugin.getMessagesManager().send(sender, "leaderboard-empty");
            return;
        }

        plugin.getMessagesManager().send(sender, "leaderboard-stats",
                Placeholder.unparsed("player", targetName),
                Placeholder.unparsed("wins", String.valueOf(stats.wins())),
                Placeholder.unparsed("streak", String.valueOf(stats.currentStreak())),
                Placeholder.unparsed("best_streak", String.valueOf(stats.bestStreak())),
                Placeholder.unparsed("fastest", String.valueOf(stats.fastestTimeMs())));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("horizonutilities.chatgames.admin")) {
            plugin.getMessagesManager().send(sender, "no-permission");
            return;
        }
        plugin.getChatGamesConfig().load();
        plugin.getMessagesManager().send(sender, "reload-success");
    }

    private void sendHelp(CommandSender sender) {
        var mm = plugin.getMessagesManager().getMiniMessage();
        sender.sendMessage(mm.deserialize(
                "<gradient:#FFD700:#FF6B35><bold>Chat Games</bold></gradient> <gray>Commands:"));
        sender.sendMessage(mm.deserialize(
                "<gold>/chatgames start [type]</gold> <gray>— Start a game"));
        sender.sendMessage(mm.deserialize(
                "<gold>/chatgames stop</gold> <gray>— Stop current game"));
        sender.sendMessage(mm.deserialize(
                "<gold>/chatgames top</gold> <gray>— View leaderboard"));
        sender.sendMessage(mm.deserialize(
                "<gold>/chatgames stats [player]</gold> <gray>— View player stats"));
        sender.sendMessage(mm.deserialize(
                "<gold>/chatgames reload</gold> <gray>— Reload config"));
    }
}
