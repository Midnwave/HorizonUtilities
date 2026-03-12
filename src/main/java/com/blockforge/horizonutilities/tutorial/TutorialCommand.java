package com.blockforge.horizonutilities.tutorial;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Collections;
import java.util.List;

/**
 * Handles /help (/guide, /tutorial) command and first-join auto-open.
 */
public class TutorialCommand implements CommandExecutor, TabCompleter, Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final TutorialManager manager;

    public TutorialCommand(HorizonUtilitiesPlugin plugin, TutorialManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("horizonutilities.tutorial.admin")) {
                player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            manager.reload();
            player.sendMessage(Component.text("Tutorial config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        new TutorialGUI(plugin, player, manager).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("horizonutilities.tutorial.admin")) {
            String prefix = args[0].toLowerCase();
            if ("reload".startsWith(prefix)) return List.of("reload");
        }
        return Collections.emptyList();
    }

    // ─── First-join auto-open ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!manager.isAutoOpenFirstJoin()) return;
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;

        int delay = manager.getFirstJoinDelayTicks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                new TutorialGUI(plugin, player, manager).open();
            }
        }, delay);
    }
}
