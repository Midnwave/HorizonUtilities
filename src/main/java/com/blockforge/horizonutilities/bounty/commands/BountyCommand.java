package com.blockforge.horizonutilities.bounty.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.bounty.Bounty;
import com.blockforge.horizonutilities.bounty.BountyManager;
import com.blockforge.horizonutilities.bounty.BountyStorageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /bounty command handler.
 *
 * Sub-commands:
 *   /bounty                          — list top 10 active bounties
 *   /bounty set <player> <amount> [anonymous]
 *   /bounty list [player]            — list bounties on player (or all)
 *   /bounty top                      — show top bounty targets
 *   /bounty admin remove <id>        — remove a bounty by DB id
 *   /bounty admin clear <player>     — clear all bounties on a player
 *   /bounty admin reload             — reload bounty config
 */
public class BountyCommand implements CommandExecutor {

    private final HorizonUtilitiesPlugin plugin;
    private final BountyManager bountyManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BountyCommand(HorizonUtilitiesPlugin plugin, BountyManager bountyManager) {
        this.plugin        = plugin;
        this.bountyManager = bountyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showTopBounties(sender, 10);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set"   -> handleSet(sender, args);
            case "list"  -> handleList(sender, args);
            case "top"   -> showTopBounties(sender, 10);
            case "admin" -> handleAdmin(sender, args);
            default -> plugin.getMessagesManager().send(sender, "bounty-usage");
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // /bounty set <player> <amount> [anonymous]
    // -------------------------------------------------------------------------

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return;
        }
        if (args.length < 3) {
            plugin.getMessagesManager().send(sender, "bounty-set-usage");
            return;
        }

        String targetName = args[1];
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessagesManager().send(sender, "bounty-invalid-number");
            return;
        }

        boolean anonymous = args.length >= 4 && args[3].equalsIgnoreCase("anonymous");

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getMessagesManager().send(sender, "player-not-found",
                    Placeholder.unparsed("player", targetName));
            return;
        }

        bountyManager.placeBounty(player, target, amount, anonymous);
    }

    // -------------------------------------------------------------------------
    // /bounty list [player]
    // -------------------------------------------------------------------------

    private void handleList(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            // List bounties on a specific player
            String targetName = args[1];
            OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
            List<Bounty> bounties = bountyManager.getActiveBounties(target.getUniqueId());
            bounties.removeIf(b -> !b.isActive());

            if (bounties.isEmpty()) {
                sender.sendMessage(mm.deserialize("<yellow>No active bounties on <red>" + targetName + "</red>."));
                return;
            }
            sender.sendMessage(mm.deserialize("<gold><bold>Bounties on " + targetName + ":</bold></gold>"));
            for (Bounty b : bounties) {
                sender.sendMessage(mm.deserialize(
                        "<gray>#" + b.getId() + " <yellow>" + plugin.getVaultHook().format(b.getAmount()) +
                        " <gray>set by <white>" + b.getDisplaySetterName()));
            }
            sender.sendMessage(mm.deserialize(
                    "<gold>Total: <yellow>" + plugin.getVaultHook().format(Bounty.getTotalValue(bounties))));
        } else {
            // List all active bounties
            showTopBounties(sender, 20);
        }
    }

    // -------------------------------------------------------------------------
    // /bounty top
    // -------------------------------------------------------------------------

    private void showTopBounties(CommandSender sender, int limit) {
        List<BountyStorageManager.BountyTarget> top =
                bountyManager.getStorage().getTopBountyTargets(limit);

        if (top.isEmpty()) {
            sender.sendMessage(mm.deserialize("<yellow>There are no active bounties."));
            return;
        }

        sender.sendMessage(mm.deserialize("<gold><bold>=== Top Bounties ===</bold></gold>"));
        int rank = 1;
        for (BountyStorageManager.BountyTarget entry : top) {
            sender.sendMessage(mm.deserialize(
                    "<gray>" + rank + ". <red>" + entry.name() +
                    " <yellow>" + plugin.getVaultHook().format(entry.totalAmount())));
            rank++;
        }
    }

    // -------------------------------------------------------------------------
    // /bounty admin ...
    // -------------------------------------------------------------------------

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("horizonutilities.bounty.admin")) {
            plugin.getMessagesManager().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Usage: /bounty admin <remove|clear|reload>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(mm.deserialize("<red>Usage: /bounty admin remove <id>"));
                    return;
                }
                int id;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(mm.deserialize("<red>Invalid ID."));
                    return;
                }
                bountyManager.getStorage().removeBounty(id);
                // Reload cache
                bountyManager.loadCache();
                sender.sendMessage(mm.deserialize("<green>Bounty #" + id + " removed."));
            }
            case "clear" -> {
                if (args.length < 3) {
                    sender.sendMessage(mm.deserialize("<red>Usage: /bounty admin clear <player>"));
                    return;
                }
                String targetName = args[2];
                OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
                bountyManager.getStorage().clearBountiesOnTarget(target.getUniqueId());
                bountyManager.loadCache();
                sender.sendMessage(mm.deserialize("<green>All bounties on <red>" + targetName + "</red> cleared."));
            }
            case "reload" -> {
                bountyManager.getBountyConfig().load();
                sender.sendMessage(mm.deserialize("<green>Bounty config reloaded."));
            }
            default -> sender.sendMessage(mm.deserialize("<red>Unknown admin sub-command."));
        }
    }
}
