package com.blockforge.horizonutilities.jobs.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Tab-completer for the {@code /jobs} command.
 */
public class JobsTabCompleter implements TabCompleter {

    private static final List<String> TOP_LEVEL = Arrays.asList(
            "join", "leave", "info", "list", "stats", "top", "quests", "prestige", "admin"
    );
    private static final List<String> ADMIN_SUBS = Arrays.asList(
            "setlevel", "addxp", "reset", "forcejoin", "boost", "reload"
    );

    private final HorizonUtilitiesPlugin plugin;

    public JobsTabCompleter(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            TOP_LEVEL.stream()
                    .filter(s -> s.startsWith(partial))
                    .forEach(completions::add);
            return completions;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "join", "leave", "info", "prestige", "top" -> {
                    jobNames().stream()
                            .filter(n -> n.startsWith(partial))
                            .forEach(completions::add);
                }
                case "stats" -> {
                    plugin.getServer().getOnlinePlayers().stream()
                            .map(p -> p.getName())
                            .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                            .forEach(completions::add);
                }
                case "admin" -> {
                    ADMIN_SUBS.stream()
                            .filter(s -> s.startsWith(partial))
                            .forEach(completions::add);
                }
            }
            return completions;
        }

        // /jobs admin <sub> ...
        if ("admin".equals(sub) && args.length >= 3) {
            String adminSub = args[1].toLowerCase(Locale.ROOT);
            String partial  = args[args.length - 1].toLowerCase(Locale.ROOT);

            switch (adminSub) {
                case "setlevel", "addxp", "reset", "forcejoin" -> {
                    if (args.length == 3) {
                        // player name
                        plugin.getServer().getOnlinePlayers().stream()
                                .map(p -> p.getName())
                                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                                .forEach(completions::add);
                    } else if (args.length == 4) {
                        // job id
                        jobNames().stream()
                                .filter(n -> n.startsWith(partial))
                                .forEach(completions::add);
                    }
                }
                case "boost" -> {
                    if (args.length == 3) {
                        // job id or "all"
                        completions.add("all");
                        jobNames().stream()
                                .filter(n -> n.startsWith(partial))
                                .forEach(completions::add);
                    } else if (args.length == 4) {
                        // multiplier examples
                        List.of("1.5", "2.0", "3.0").stream()
                                .filter(n -> n.startsWith(partial))
                                .forEach(completions::add);
                    } else if (args.length == 5) {
                        // duration examples (seconds)
                        List.of("3600", "7200", "86400").stream()
                                .filter(n -> n.startsWith(partial))
                                .forEach(completions::add);
                    }
                }
            }
        }

        return completions;
    }

    private List<String> jobNames() {
        return plugin.getJobManager().getAllJobs().stream()
                .map(j -> j.getId())
                .collect(Collectors.toList());
    }
}
