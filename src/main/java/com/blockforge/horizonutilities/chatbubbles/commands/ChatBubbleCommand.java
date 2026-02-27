package com.blockforge.horizonutilities.chatbubbles.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.chatbubbles.ChatBubbleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ChatBubbleCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final ChatBubbleManager manager;

    public ChatBubbleCommand(HorizonUtilitiesPlugin plugin, ChatBubbleManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is for players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("horizonutilities.chatbubbles.use")) {
            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            boolean enabled = manager.toggle(player);
            player.sendMessage(Component.text("[Chat Bubbles] ", NamedTextColor.GOLD)
                    .append(Component.text("Chat bubbles are now ", NamedTextColor.GRAY))
                    .append(enabled
                            ? Component.text("enabled", NamedTextColor.GREEN)
                            : Component.text("disabled", NamedTextColor.RED))
                    .append(Component.text(".", NamedTextColor.GRAY)));
            return true;
        }
        player.sendMessage(Component.text("Usage: /chatbubbles toggle", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("toggle");
        return List.of();
    }
}
