package com.blockforge.horizonutilities.warps.admin.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.warps.admin.AdminWarpManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /warp, /setwarp, /delwarp, /warps commands.
 */
public class AdminWarpCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final AdminWarpManager manager;

    public AdminWarpCommand(HorizonUtilitiesPlugin plugin, AdminWarpManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }

        switch (label.toLowerCase()) {
            case "warp" -> handleWarp(player, args);
            case "setwarp" -> handleSetWarp(player, args);
            case "delwarp" -> handleDelWarp(player, args);
            case "warps" -> handleList(player);
        }
        return true;
    }

    private void handleWarp(Player player, String[] args) {
        if (!player.hasPermission("horizonutilities.warp.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /warp <name>", NamedTextColor.YELLOW));
            return;
        }
        Location dest = manager.getWarp(args[0]);
        if (dest == null) {
            player.sendMessage(Component.text("Warp '" + args[0] + "' not found.", NamedTextColor.RED));
            return;
        }
        manager.teleport(player, dest, args[0]);
    }

    private void handleSetWarp(Player player, String[] args) {
        if (!player.hasPermission("horizonutilities.warp.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /setwarp <name>", NamedTextColor.YELLOW));
            return;
        }
        manager.setWarp(args[0], player.getLocation());
        player.sendMessage(Component.text("Warp ", NamedTextColor.GREEN)
                .append(Component.text(args[0], NamedTextColor.GOLD))
                .append(Component.text(" set.", NamedTextColor.GREEN)));
    }

    private void handleDelWarp(Player player, String[] args) {
        if (!player.hasPermission("horizonutilities.warp.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /delwarp <name>", NamedTextColor.YELLOW));
            return;
        }
        if (manager.deleteWarp(args[0])) {
            player.sendMessage(Component.text("Warp ", NamedTextColor.RED)
                    .append(Component.text(args[0], NamedTextColor.GOLD))
                    .append(Component.text(" deleted.", NamedTextColor.RED)));
        } else {
            player.sendMessage(Component.text("Warp '" + args[0] + "' not found.", NamedTextColor.RED));
        }
    }

    private void handleList(Player player) {
        if (!player.hasPermission("horizonutilities.warp.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        var names = manager.getWarpNames();
        if (names.isEmpty()) {
            player.sendMessage(Component.text("No warps set.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Warps: ", NamedTextColor.GOLD)
                .append(Component.text(String.join(", ", names), NamedTextColor.WHITE)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("warp") || label.equalsIgnoreCase("delwarp")) {
            if (args.length == 1) {
                String input = args[0].toLowerCase();
                return manager.getWarpNames().stream()
                        .filter(n -> n.startsWith(input))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
