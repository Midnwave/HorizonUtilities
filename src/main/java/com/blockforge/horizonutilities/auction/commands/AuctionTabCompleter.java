package com.blockforge.horizonutilities.auction.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionTabCompleter implements TabCompleter {

    private final HorizonUtilitiesPlugin plugin;

    public AuctionTabCompleter(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("sell", "search", "collect", "history", "stats"));
            if (player.hasPermission("horizonutilities.ah.admin")) {
                subs.addAll(List.of("admin", "reload"));
            }
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "sell" -> List.of("<price>");
                case "search" -> List.of("<query>");
                case "stats" -> {
                    String partial = args[1].toUpperCase();
                    yield Arrays.stream(Material.values())
                            .map(Material::name)
                            .filter(n -> n.startsWith(partial))
                            .limit(20)
                            .collect(Collectors.toList());
                }
                case "admin" -> {
                    if (player.hasPermission("horizonutilities.ah.admin")) {
                        yield filter(List.of("remove", "ban", "unban", "transactions"), args[1]);
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "sell" -> List.of("[buyout]");
                case "admin" -> {
                    String adminSub = args[1].toLowerCase();
                    if (adminSub.equals("remove")) yield List.of("<id>");
                    if (adminSub.equals("ban") || adminSub.equals("unban") || adminSub.equals("transactions")) {
                        yield null; // default player completion
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("sell")) {
            return filter(plugin.getAuctionHouseConfig().getDurations().stream()
                    .map(String::valueOf).toList(), args[3]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
