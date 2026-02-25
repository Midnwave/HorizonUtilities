package com.blockforge.horizonutilities.games.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGamesTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("top", "stats"));
            if (sender.hasPermission("horizonutilities.chatgames.admin")) {
                subs.addAll(Arrays.asList("start", "stop", "reload"));
            }
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "start" -> sender.hasPermission("horizonutilities.chatgames.admin")
                        ? filter(Arrays.asList("unscramble", "retype", "math", "unreverse", "recipe-guess"), args[1])
                        : List.of();
                case "stats" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(s -> s.startsWith(lower)).collect(Collectors.toList());
    }
}
