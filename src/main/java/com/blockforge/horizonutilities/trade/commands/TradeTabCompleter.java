package com.blockforge.horizonutilities.trade.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab completion for the /trade command.
 * Suggests online player names for /trade <player>, and sub-commands otherwise.
 */
public class TradeTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("accept", "decline", "cancel");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> results = new ArrayList<>();

            // Sub-commands
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial)) results.add(sub);
            }

            // Online player names
            for (Player online : player.getServer().getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())
                        && online.getName().toLowerCase().startsWith(partial)) {
                    results.add(online.getName());
                }
            }

            return results;
        }

        return List.of();
    }
}
