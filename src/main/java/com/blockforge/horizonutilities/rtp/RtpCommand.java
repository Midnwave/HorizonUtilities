package com.blockforge.horizonutilities.rtp;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RtpCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;
    private final RtpManager rtpManager;

    public RtpCommand(HorizonUtilitiesPlugin plugin, RtpManager rtpManager) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessagesManager().send(sender, "player-only");
            return true;
        }

        World world;
        if (args.length >= 1) {
            world = Bukkit.getWorld(args[0]);
            if (world == null) {
                plugin.getMessagesManager().send(player, "rtp-unknown-world",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("world", args[0]));
                return true;
            }
        } else {
            world = player.getWorld();
        }

        rtpManager.teleport(player, world);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> worlds = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().toLowerCase().startsWith(partial) &&
                    rtpManager.getConfig().isWorldEnabled(w.getName())) {
                    worlds.add(w.getName());
                }
            }
            return worlds;
        }
        return List.of();
    }
}
