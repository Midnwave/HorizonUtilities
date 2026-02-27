package com.blockforge.horizonutilities.tournaments.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.tournaments.Tournament;
import com.blockforge.horizonutilities.tournaments.TournamentManager;
import com.blockforge.horizonutilities.tournaments.TournamentObjectiveType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /tournament [create|start|stop|join|leave|status|list|reload]
 */
public class TournamentCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final TournamentManager manager;

    public TournamentCommand(HorizonUtilitiesPlugin plugin, TournamentManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "status" -> handleStatus(sender, args);
            case "join" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(Component.text("Players only.", NamedTextColor.RED)); return true; }
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /tournament join <id>", NamedTextColor.YELLOW)); return true; }
                manager.join(p, args[1]);
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(Component.text("Players only.", NamedTextColor.RED)); return true; }
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /tournament leave <id>", NamedTextColor.YELLOW)); return true; }
                manager.leave(p, args[1]);
            }
            case "create" -> {
                if (!sender.hasPermission("horizonutilities.tournament.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
                }
                // /tournament create <name> <objective> [targetMaterial] [targetScore|-1] [durationMinutes|-1] [rewardMoney]
                if (args.length < 3) {
                    sender.sendMessage(Component.text(
                            "Usage: /tournament create <name> <OBJECTIVE> [material] [targetScore] [durationMin] [reward$]",
                            NamedTextColor.YELLOW));
                    return true;
                }
                String name = args[1];
                TournamentObjectiveType objective;
                try {
                    objective = TournamentObjectiveType.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid objective. Valid: " +
                            Arrays.stream(TournamentObjectiveType.values()).map(Enum::name)
                                    .collect(Collectors.joining(", ")), NamedTextColor.RED));
                    return true;
                }
                String mat = args.length >= 4 ? args[3] : "";
                int target = args.length >= 5 ? parseInt(args[4], -1) : -1;
                int duration = args.length >= 6 ? parseInt(args[5], 60) * 60 : 3600;
                double money = args.length >= 7 ? parseDouble(args[6], 0) : 0;
                UUID creatorUuid = sender instanceof Player p ? p.getUniqueId() : new UUID(0, 0);
                var t = manager.create(name, creatorUuid, objective, mat, target, duration, money);
                sender.sendMessage(Component.text("Tournament created: " + t.getId(), NamedTextColor.GREEN));
            }
            case "start" -> {
                if (!sender.hasPermission("horizonutilities.tournament.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
                }
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /tournament start <id>", NamedTextColor.YELLOW)); return true; }
                if (manager.start(args[1])) sender.sendMessage(Component.text("Tournament started.", NamedTextColor.GREEN));
                else sender.sendMessage(Component.text("Tournament not found or already started.", NamedTextColor.RED));
            }
            case "stop" -> {
                if (!sender.hasPermission("horizonutilities.tournament.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
                }
                if (args.length < 2) { sender.sendMessage(Component.text("Usage: /tournament stop <id>", NamedTextColor.YELLOW)); return true; }
                if (manager.stop(args[1])) sender.sendMessage(Component.text("Tournament stopped.", NamedTextColor.GREEN));
                else sender.sendMessage(Component.text("Tournament not found.", NamedTextColor.RED));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleList(CommandSender sender) {
        var all = manager.getAll();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No tournaments.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("=== Tournaments ===", NamedTextColor.GOLD));
        for (var t : all) {
            sender.sendMessage(Component.text("  [" + t.getStatus() + "] ", NamedTextColor.GRAY)
                    .append(Component.text(t.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" (" + t.getObjective().name() + ")", NamedTextColor.AQUA))
                    .append(Component.text(" [" + t.getParticipants().size() + " players] id=" + t.getId(), NamedTextColor.DARK_GRAY)));
        }
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /tournament status <id>", NamedTextColor.YELLOW));
            return;
        }
        var t = manager.get(args[1]);
        if (t == null) { sender.sendMessage(Component.text("Tournament not found.", NamedTextColor.RED)); return; }
        sender.sendMessage(Component.text("=== " + t.getName() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Status: " + t.getStatus() + " | Objective: " + t.getObjective().name(), NamedTextColor.GRAY));
        if (t.getStatus() == Tournament.Status.ACTIVE) {
            long rem = t.getRemainingSeconds();
            sender.sendMessage(Component.text("Time remaining: " + (rem < 0 ? "∞" : rem + "s"), NamedTextColor.GRAY));
        }
        var lb = t.getLeaderboard();
        if (!lb.isEmpty()) {
            sender.sendMessage(Component.text("Leaderboard:", NamedTextColor.YELLOW));
            int rank = 1;
            for (var entry : lb.stream().limit(5).toList()) {
                var p = plugin.getServer().getOfflinePlayer(entry.getKey());
                sender.sendMessage(Component.text("  #" + rank++ + " ", NamedTextColor.GRAY)
                        .append(Component.text(p.getName() != null ? p.getName() : "?", NamedTextColor.WHITE))
                        .append(Component.text(" - " + entry.getValue(), NamedTextColor.AQUA)));
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/tournament [create|start|stop|join|leave|status|list]", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("create","start","stop","join","leave","status","list");
        if (args.length == 2 && List.of("start","stop","join","leave","status").contains(args[0].toLowerCase())) {
            return manager.getAll().stream().map(Tournament::getId)
                    .filter(id -> id.startsWith(args[1])).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return Arrays.stream(TournamentObjectiveType.values()).map(Enum::name)
                    .filter(n -> n.startsWith(args[2].toUpperCase())).collect(Collectors.toList());
        }
        return List.of();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
}
