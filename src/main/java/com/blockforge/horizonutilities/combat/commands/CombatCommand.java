package com.blockforge.horizonutilities.combat.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.combat.CombatManager;
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
 * /combat [status|reload]
 */
public class CombatCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final CombatManager manager;

    public CombatCommand(HorizonUtilitiesPlugin plugin, CombatManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console: combat status not available.", NamedTextColor.GRAY));
                return true;
            }
            if (!player.hasPermission("horizonutilities.combat.use")) {
                player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (manager.isInCombat(player.getUniqueId())) {
                int secs = manager.getRemainingSeconds(player.getUniqueId());
                player.sendMessage(Component.text("⚔ You are in combat! ", NamedTextColor.RED)
                        .append(Component.text(secs + "s remaining.", NamedTextColor.YELLOW)));
            } else {
                player.sendMessage(Component.text("You are not in combat.", NamedTextColor.GREEN));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("horizonutilities.combat.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            manager.getConfig().load();
            sender.sendMessage(Component.text("Combat config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /combat [status|reload]", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("status", "reload");
        return List.of();
    }
}
