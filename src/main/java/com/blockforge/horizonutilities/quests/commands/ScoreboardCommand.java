package com.blockforge.horizonutilities.quests.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.config.MessagesManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /sb command — toggle scoreboard on/off and switch between quest/normal modes.
 */
public class ScoreboardCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final MessagesManager msg;

    public ScoreboardCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessagesManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return true;
        }

        if (args.length == 0) {
            if (plugin.getScoreboardManager() != null) {
                boolean current = plugin.getScoreboardManager().isEnabled(player.getUniqueId());
                plugin.getScoreboardManager().setEnabled(player.getUniqueId(), !current);
                msg.send(player, current ? "sb-toggle-off" : "sb-toggle-on");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                if (plugin.getScoreboardManager() != null) {
                    plugin.getScoreboardManager().setEnabled(player.getUniqueId(), true);
                    msg.send(player, "sb-toggle-on");
                }
            }
            case "off" -> {
                if (plugin.getScoreboardManager() != null) {
                    plugin.getScoreboardManager().setEnabled(player.getUniqueId(), false);
                    msg.send(player, "sb-toggle-off");
                }
            }
            case "quests" -> {
                if (plugin.getScoreboardManager() != null) {
                    plugin.getScoreboardManager().setMode(player.getUniqueId(), "QUESTS");
                    msg.send(player, "sb-mode-quests");
                }
            }
            case "normal" -> {
                if (plugin.getScoreboardManager() != null) {
                    plugin.getScoreboardManager().setMode(player.getUniqueId(), "NORMAL");
                    msg.send(player, "sb-mode-normal");
                }
            }
            default -> msg.send(sender, "sb-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("on", "off", "quests", "normal"), args[0]);
        }
        return List.of();
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
