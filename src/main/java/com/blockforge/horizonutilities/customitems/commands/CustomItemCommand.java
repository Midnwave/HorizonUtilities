package com.blockforge.horizonutilities.customitems.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.customitems.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /customitem give <id> [player] [amount]
 */
public class CustomItemCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;

    public CustomItemCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("horizonutilities.customitem.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage: /customitem give <id> [player] [amount]", NamedTextColor.YELLOW));
            return true;
        }

        String itemId = args[1];
        CustomItem customItem = plugin.getCustomItemRegistry().get(itemId);
        if (customItem == null) {
            sender.sendMessage(Component.text("Unknown custom item: " + itemId, NamedTextColor.RED));
            sender.sendMessage(Component.text("Available: " +
                    plugin.getCustomItemRegistry().getAll().stream()
                            .map(CustomItem::getId).collect(Collectors.joining(", ")),
                    NamedTextColor.GRAY));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[2] + "' not found.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Specify a player name when using from console.", NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.min(64, Math.max(1, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED));
                return true;
            }
        }

        ItemStack stack = customItem.createItem();
        stack.setAmount(amount);
        target.getInventory().addItem(stack);

        sender.sendMessage(Component.text("Gave ", NamedTextColor.GREEN)
                .append(Component.text(amount + "x " + customItem.getDisplayName(), NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(target.getName(), NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GREEN)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("give");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String input = args[1].toLowerCase();
            return plugin.getCustomItemRegistry().getAll().stream()
                    .map(CustomItem::getId)
                    .filter(id -> id.startsWith(input))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
