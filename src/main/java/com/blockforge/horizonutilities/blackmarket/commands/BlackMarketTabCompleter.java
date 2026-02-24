package com.blockforge.horizonutilities.blackmarket.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.blackmarket.BlackMarketManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BlackMarketTabCompleter implements TabCompleter {

    private final HorizonUtilitiesPlugin plugin;

    private static final List<String> ADMIN_SUBS = Arrays.asList(
            "additem", "removeitem", "setprice", "list", "reload");

    public BlackMarketTabCompleter(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("horizonutilities.blackmarket.admin")) {
            return Collections.emptyList();
        }

        BlackMarketManager manager = plugin.getBlackMarketManager();

        // /bm <arg1>
        if (args.length == 1) {
            return filter(Collections.singletonList("admin"), args[0]);
        }

        // /bm admin <arg2>
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(ADMIN_SUBS, args[1]);
        }

        // /bm admin <sub> <arg3>
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            switch (args[1].toLowerCase()) {
                case "removeitem", "setprice" -> {
                    // Suggest existing item IDs
                    return filter(
                            manager.getAllItems().stream()
                                    .map(i -> i.getId())
                                    .collect(Collectors.toList()),
                            args[2]);
                }
                case "additem" -> {
                    // Suggest a placeholder <id> — nothing useful to complete
                    return Collections.emptyList();
                }
            }
        }

        // /bm admin additem <id> <material> ...
        if (args.length == 4 && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("additem")) {
            // Suggest material names
            List<String> mats = new ArrayList<>();
            for (org.bukkit.Material m : org.bukkit.Material.values()) {
                if (m.isItem()) mats.add(m.name());
            }
            return filter(mats, args[3]);
        }

        // /bm admin additem <id> <material> <price> <category>
        if (args.length == 6 && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("additem")) {
            return filter(manager.getCategories(), args[5]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
