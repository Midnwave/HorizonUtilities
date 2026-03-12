package com.blockforge.horizonutilities.homes;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final HomeManager homeManager;

    public HomeCommand(HorizonUtilitiesPlugin plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        switch (label.toLowerCase(Locale.ROOT)) {
            case "home"    -> handleHome(player, args);
            case "sethome" -> handleSetHome(player, args);
            case "delhome" -> handleDelHome(player, args);
            case "homes"   -> handleList(player);
            default        -> handleHome(player, args);
        }
        return true;
    }

    private void handleHome(Player player, String[] args) {
        String name = args.length >= 1 ? args[0] : "home";
        homeManager.teleportToHome(player, name);
    }

    private void handleSetHome(Player player, String[] args) {
        String name = args.length >= 1 ? args[0] : "home";
        homeManager.setHome(player, name);
    }

    private void handleDelHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /delhome <name>", NamedTextColor.RED));
            return;
        }
        homeManager.deleteHome(player, args[0]);
    }

    private void handleList(Player player) {
        List<Home> homes = homeManager.getHomes(player.getUniqueId());
        int max = homeManager.getMaxHomes(player);
        String maxStr = max >= Integer.MAX_VALUE ? "unlimited" : String.valueOf(max);

        player.sendMessage(Component.text("Your Homes ", NamedTextColor.GOLD)
                .append(Component.text("(" + homes.size() + "/" + maxStr + ")", NamedTextColor.GRAY)));

        if (homes.isEmpty()) {
            player.sendMessage(Component.text("  No homes set. Use /sethome <name> to create one.", NamedTextColor.GRAY));
            return;
        }

        for (Home home : homes) {
            Component entry = Component.text("  - ", NamedTextColor.GRAY)
                    .append(Component.text(home.getName(), NamedTextColor.YELLOW)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/home " + home.getName())))
                    .append(Component.text(" (" + home.getWorld() + ", " +
                            (int) home.getX() + " " + (int) home.getY() + " " + (int) home.getZ() + ")",
                            NamedTextColor.GRAY));
            player.sendMessage(entry);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return Collections.emptyList();
        }

        String sub = label.toLowerCase(Locale.ROOT);
        if (sub.equals("home") || sub.equals("delhome")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return homeManager.getHomes(player.getUniqueId()).stream()
                    .map(Home::getName)
                    .filter(n -> n.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
