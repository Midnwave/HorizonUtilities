package com.blockforge.horizonutilities.maintenance;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class MaintenanceCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;

    public MaintenanceCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Authorization: console always allowed, players must be in the hardcoded list
        if (sender instanceof Player player) {
            if (!plugin.getMaintenanceManager().isAuthorized(player.getUniqueId())) {
                sender.sendMessage(Component.text("You do not have access to this command.", NamedTextColor.RED));
                return true;
            }
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        var manager = plugin.getMaintenanceManager();

        switch (args[0].toLowerCase()) {
            case "on" -> {
                if (manager.isEnabled()) {
                    sender.sendMessage(Component.text("Maintenance mode is already enabled.", NamedTextColor.YELLOW));
                    return true;
                }
                manager.setEnabled(true);
                sender.sendMessage(Component.text("Maintenance mode ", NamedTextColor.GRAY)
                        .append(Component.text("enabled", NamedTextColor.RED))
                        .append(Component.text(". All non-authorized players have been kicked.", NamedTextColor.GRAY)));
            }
            case "off" -> {
                if (!manager.isEnabled()) {
                    sender.sendMessage(Component.text("Maintenance mode is already disabled.", NamedTextColor.YELLOW));
                    return true;
                }
                manager.setEnabled(false);
                sender.sendMessage(Component.text("Maintenance mode ", NamedTextColor.GRAY)
                        .append(Component.text("disabled", NamedTextColor.GREEN))
                        .append(Component.text(". Players can now join.", NamedTextColor.GRAY)));
            }
            case "status" -> {
                if (manager.isEnabled()) {
                    sender.sendMessage(Component.text("Maintenance mode is currently ", NamedTextColor.GRAY)
                            .append(Component.text("ENABLED", NamedTextColor.RED)));
                } else {
                    sender.sendMessage(Component.text("Maintenance mode is currently ", NamedTextColor.GRAY)
                            .append(Component.text("DISABLED", NamedTextColor.GREEN)));
                }
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /maintenance <on|off|status>", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (!plugin.getMaintenanceManager().isAuthorized(player.getUniqueId())) {
                return List.of();
            }
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return List.of("on", "off", "status").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
