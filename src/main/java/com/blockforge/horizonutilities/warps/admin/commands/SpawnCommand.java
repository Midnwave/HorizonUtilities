package com.blockforge.horizonutilities.warps.admin.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.warps.admin.AdminWarpManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handles /spawn and /setspawn commands.
 */
public class SpawnCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final AdminWarpManager manager;

    public SpawnCommand(HorizonUtilitiesPlugin plugin, AdminWarpManager manager) {
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

        if (label.equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("horizonutilities.spawn.admin")) {
                player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            manager.setSpawn(player.getLocation());
            player.sendMessage(Component.text("Spawn set to your current location.", NamedTextColor.GREEN));
            return true;
        }

        // /spawn
        if (!player.hasPermission("horizonutilities.spawn.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        manager.teleport(player, manager.getSpawn(), "Spawn");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
