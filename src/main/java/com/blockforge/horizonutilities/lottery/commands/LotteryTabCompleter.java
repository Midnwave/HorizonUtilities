package com.blockforge.horizonutilities.lottery.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LotteryTabCompleter implements TabCompleter {

    private final HorizonUtilitiesPlugin plugin;

    private static final List<String> TOP_LEVEL  = Arrays.asList("buy", "info", "history", "admin");
    private static final List<String> ADMIN_SUBS = Arrays.asList("draw", "cancel", "setpot", "reload");

    public LotteryTabCompleter(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        // /lottery <arg1>
        if (args.length == 1) {
            return filter(TOP_LEVEL, args[0]);
        }

        String sub = args[0].toLowerCase();

        // /lottery buy <tierId>  or  /lottery info <tierId>
        if ((sub.equals("buy") || sub.equals("info")) && args.length == 2) {
            return filter(tierIds(), args[1]);
        }

        // /lottery buy <tierId> <amount> — nothing useful to suggest for amount
        if (sub.equals("buy") && args.length == 3) {
            return filter(Arrays.asList("1", "2", "5", "10"), args[2]);
        }

        // /lottery admin <subcommand>
        if (sub.equals("admin") && sender.hasPermission("horizonutilities.lottery.admin")) {
            if (args.length == 2) {
                return filter(ADMIN_SUBS, args[1]);
            }
            // /lottery admin draw|cancel|setpot <tierId>
            String adminSub = args[1].toLowerCase();
            if (args.length == 3 && (adminSub.equals("draw")
                    || adminSub.equals("cancel")
                    || adminSub.equals("setpot"))) {
                return filter(tierIds(), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> tierIds() {
        return plugin.getLotteryManager().getTierConfigs().keySet()
                .stream().collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
