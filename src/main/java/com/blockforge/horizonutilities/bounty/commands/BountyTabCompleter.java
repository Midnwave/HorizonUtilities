package com.blockforge.horizonutilities.bounty.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab completion for the /bounty command.
 */
public class BountyTabCompleter implements TabCompleter {

    private static final List<String> ROOT_SUBS   = List.of("set", "list", "top", "admin");
    private static final List<String> ADMIN_SUBS  = List.of("remove", "clear", "reload");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("set", "list", "top"));
            if (sender.hasPermission("horizonutilities.bounty.admin")) {
                subs.add("admin");
            }
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "set", "list" -> {
                    return onlinePlayerNames(sender, args[1]);
                }
                case "admin" -> {
                    if (!sender.hasPermission("horizonutilities.bounty.admin")) return List.of();
                    return filter(ADMIN_SUBS, args[1]);
                }
            }
        }

        if (args.length == 3) {
            String sub  = args[0].toLowerCase();
            String sub2 = args[1].toLowerCase();
            if (sub.equals("set")) {
                // suggest amount placeholders
                return filter(List.of("100", "500", "1000", "5000"), args[2]);
            }
            if (sub.equals("admin") && sub2.equals("clear")) {
                return onlinePlayerNames(sender, args[2]);
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            return filter(List.of("anonymous"), args[3]);
        }

        return List.of();
    }

    private List<String> onlinePlayerNames(CommandSender sender, String partial) {
        List<String> names = new ArrayList<>();
        String lp = partial.toLowerCase();
        for (Player p : sender.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(lp)) {
                names.add(p.getName());
            }
        }
        return names;
    }

    private List<String> filter(List<String> options, String partial) {
        String lp = partial.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lp)) result.add(opt);
        }
        return result;
    }
}
