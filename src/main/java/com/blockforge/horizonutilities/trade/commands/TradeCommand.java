package com.blockforge.horizonutilities.trade.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.trade.TradeManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /trade command handler.
 *
 * Sub-commands:
 *   /trade <player>   — send a trade request
 *   /trade accept     — accept incoming request
 *   /trade decline    — decline incoming request
 *   /trade cancel     — cancel active trade
 */
public class TradeCommand implements CommandExecutor {

    private final HorizonUtilitiesPlugin plugin;
    private final TradeManager tradeManager;

    public TradeCommand(HorizonUtilitiesPlugin plugin, TradeManager tradeManager) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            plugin.getMessagesManager().send(player, "trade-usage");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "accept" -> {
                tradeManager.acceptRequest(player);
            }
            case "decline" -> {
                tradeManager.declineRequest(player);
            }
            case "cancel" -> {
                if (!tradeManager.hasActiveSession(player.getUniqueId())) {
                    plugin.getMessagesManager().send(player, "trade-not-active");
                } else {
                    tradeManager.cancelSession(player.getUniqueId());
                }
            }
            default -> {
                // /trade <player>
                if (!player.hasPermission("horizonutilities.trade.use")) {
                    plugin.getMessagesManager().send(player, "no-permission");
                    return true;
                }
                String targetName = args[0];
                Player target = plugin.getServer().getPlayer(targetName);
                if (target == null || !target.isOnline()) {
                    plugin.getMessagesManager().send(player, "player-not-found",
                            Placeholder.unparsed("player", targetName));
                    return true;
                }
                tradeManager.sendRequest(player, target);
            }
        }

        return true;
    }
}
