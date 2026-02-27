package com.blockforge.horizonutilities.warps.player.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.warps.player.PlayerWarp;
import com.blockforge.horizonutilities.warps.player.PlayerWarpManager;
import com.blockforge.horizonutilities.warps.player.gui.PlayerWarpGUIListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /pwarp [set|delete|list|rate|<ownerName:warpName>]
 */
public class PlayerWarpCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final PlayerWarpManager manager;
    private final PlayerWarpGUIListener guiListener;

    public PlayerWarpCommand(HorizonUtilitiesPlugin plugin, PlayerWarpManager manager,
                             PlayerWarpGUIListener guiListener) {
        this.plugin      = plugin;
        this.manager     = manager;
        this.guiListener = guiListener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("horizonutilities.pwarp.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            guiListener.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /pwarp set <name>", NamedTextColor.YELLOW));
                    return true;
                }
                manager.createWarp(player, args[1]);
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /pwarp delete <name>", NamedTextColor.YELLOW));
                    return true;
                }
                manager.deleteWarp(player, args[1]);
            }
            case "rate" -> {
                // /pwarp rate <ownerName> <warpName> <1-5>
                if (args.length < 4) {
                    player.sendMessage(Component.text("Usage: /pwarp rate <owner> <name> <1-5>", NamedTextColor.YELLOW));
                    return true;
                }
                PlayerWarp warp = manager.getStorage().getByName(args[1], args[2]);
                if (warp == null) {
                    player.sendMessage(Component.text("Warp not found.", NamedTextColor.RED));
                    return true;
                }
                try {
                    int stars = Integer.parseInt(args[3]);
                    manager.rateWarp(player, warp, stars);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Rating must be a number 1-5.", NamedTextColor.RED));
                }
            }
            default -> {
                // /pwarp <ownerName> <warpName>  OR  /pwarp <ownerName:warpName>
                String ownerName;
                String warpName;
                if (args[0].contains(":")) {
                    String[] parts = args[0].split(":", 2);
                    ownerName = parts[0];
                    warpName  = parts[1];
                } else if (args.length >= 2) {
                    ownerName = args[0];
                    warpName  = args[1];
                } else {
                    player.sendMessage(Component.text(
                            "Usage: /pwarp [set|delete|list|rate|<owner> <name>]", NamedTextColor.YELLOW));
                    return true;
                }
                PlayerWarp warp = manager.getStorage().getByName(ownerName, warpName);
                if (warp == null) {
                    player.sendMessage(Component.text("Warp not found.", NamedTextColor.RED));
                    return true;
                }
                manager.teleportToWarp(player, warp);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("set", "delete", "list", "rate").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")
                && sender instanceof Player player) {
            return manager.getPlayerWarps(player.getUniqueId()).stream()
                    .map(PlayerWarp::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
