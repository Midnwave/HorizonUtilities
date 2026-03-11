package com.blockforge.horizonutilities.quests.commands;

import com.blockforge.horizonutilities.quests.QuestCategory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class QuestTabCompleter implements TabCompleter {

    private static final List<String> ROOT_SUBS = List.of("daily", "jobs", "weekly", "challenges", "history", "admin");
    private static final List<String> ADMIN_SUBS = List.of("reload", "reset", "settier", "generate", "import");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("daily", "jobs", "weekly", "challenges", "history"));
            if (sender.hasPermission("horizonutilities.quests.admin")) {
                subs.add("admin");
            }
            return filter(subs, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("horizonutilities.quests.admin")) return List.of();
            return filter(ADMIN_SUBS, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("reset") || sub.equals("settier") || sub.equals("generate") || sub.equals("import")) {
                return onlinePlayerNames(args[2]);
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("settier")) {
                return filter(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), args[3]);
            }
            if (sub.equals("generate")) {
                List<String> categories = new ArrayList<>();
                for (QuestCategory cat : QuestCategory.values()) {
                    categories.add(cat.name());
                }
                return filter(categories, args[3]);
            }
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("generate")) {
            return filter(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), args[4]);
        }

        return List.of();
    }

    private List<String> onlinePlayerNames(String partial) {
        String lp = partial.toLowerCase();
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
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
