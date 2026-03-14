package com.blockforge.horizonutilities.wither;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin command for managing the Wither Sovereign boss fight.
 * Usage: /wither [spawn|kill|reload|stats]
 * Permission: horizonutilities.wither.admin
 */
public class WitherCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "horizonutilities.wither.admin";
    private static final List<String> SUBCOMMANDS = List.of("spawn", "kill", "reload", "stats");

    private final HorizonUtilitiesPlugin plugin;
    private final WitherBossManager manager;

    public WitherCommand(HorizonUtilitiesPlugin plugin, WitherBossManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "spawn" -> handleSpawn(sender);
            case "kill" -> handleKill(sender);
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    /**
     * Force-spawns a custom Wither Sovereign at the sender's location.
     * Must be executed by a player (not console).
     */
    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command must be run by a player.", NamedTextColor.RED));
            return true;
        }

        // Count nearby players for HP scaling
        int nearbyPlayers = player.getLocation().getNearbyPlayers(64).size();

        WitherBossEntity boss = manager.spawnBoss(player.getLocation(), nearbyPlayers);
        if (boss != null) {
            sender.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Boss spawned at your location with ", NamedTextColor.GREEN))
                    .append(Component.text(nearbyPlayers + " player(s)", NamedTextColor.GOLD))
                    .append(Component.text(" nearby. HP: ", NamedTextColor.GREEN))
                    .append(Component.text(String.format("%.0f", boss.getMaxHealth()), NamedTextColor.GOLD)));
        } else {
            sender.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Failed to spawn boss. Is the world valid?", NamedTextColor.RED)));
        }
        return true;
    }

    /**
     * Kills all currently active custom Wither Sovereign bosses.
     */
    private boolean handleKill(CommandSender sender) {
        Collection<WitherBossEntity> bosses = manager.getActiveBosses();
        if (bosses.isEmpty()) {
            sender.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("No active Wither Sovereign fights.", NamedTextColor.YELLOW)));
            return true;
        }

        int count = bosses.size();
        // Copy the collection to avoid ConcurrentModificationException
        List<WitherBossEntity> bossList = new ArrayList<>(bosses);
        for (WitherBossEntity boss : bossList) {
            // Kill the wither entity
            if (boss.getWither().isValid()) {
                boss.getWither().setHealth(0);
            }
            manager.removeBoss(boss);
        }

        sender.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("Killed " + count + " active Wither Sovereign(s).", NamedTextColor.GREEN)));
        return true;
    }

    /**
     * Reloads the Wither Sovereign configuration from disk.
     */
    private boolean handleReload(CommandSender sender) {
        manager.reload();
        sender.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("Configuration reloaded.", NamedTextColor.GREEN)));
        return true;
    }

    /**
     * Displays detailed statistics about all active Wither Sovereign fights.
     */
    private boolean handleStats(CommandSender sender) {
        Collection<WitherBossEntity> bosses = manager.getActiveBosses();
        if (bosses.isEmpty()) {
            sender.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("No active Wither Sovereign fights.", NamedTextColor.YELLOW)));
            return true;
        }

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Wither Sovereign Status ===", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        sender.sendMessage(Component.text("Active fights: " + bosses.size(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(""));

        int index = 1;
        for (WitherBossEntity boss : bosses) {
            double currentHp = boss.getWither().isValid() ? boss.getWither().getHealth() : 0;
            double maxHp = boss.getMaxHealth();
            double healthPercent = maxHp > 0 ? (currentHp / maxHp) * 100.0 : 0;

            // Color HP based on percentage
            NamedTextColor hpColor;
            if (healthPercent > 50) hpColor = NamedTextColor.GREEN;
            else if (healthPercent > 25) hpColor = NamedTextColor.YELLOW;
            else hpColor = NamedTextColor.RED;

            String phaseName = manager.getConfig().getPhaseName(boss.getCurrentPhase());

            sender.sendMessage(Component.text("Fight #" + index, NamedTextColor.GOLD, TextDecoration.BOLD));

            sender.sendMessage(Component.text("  HP: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.0f / %.0f (%.1f%%)", currentHp, maxHp, healthPercent), hpColor)));

            sender.sendMessage(Component.text("  Phase: ", NamedTextColor.GRAY)
                    .append(Component.text(boss.getCurrentPhase() + " — " + phaseName, NamedTextColor.LIGHT_PURPLE)));

            sender.sendMessage(Component.text("  Duration: ", NamedTextColor.GRAY)
                    .append(Component.text(formatDuration(boss.getFightDurationSeconds()), NamedTextColor.WHITE)));

            sender.sendMessage(Component.text("  Participants: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(boss.getParticipants().size()), NamedTextColor.WHITE)));

            sender.sendMessage(Component.text("  Active Minions: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(boss.getActiveMinions().size()), NamedTextColor.WHITE)));

            sender.sendMessage(Component.text("  Enraged: ", NamedTextColor.GRAY)
                    .append(boss.isEnraged()
                            ? Component.text("YES", NamedTextColor.RED, TextDecoration.BOLD)
                            : Component.text("No", NamedTextColor.GREEN)));

            if (boss.getWither().isValid()) {
                var loc = boss.getWither().getLocation();
                sender.sendMessage(Component.text("  Location: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%s (%.0f, %.0f, %.0f)",
                                        loc.getWorld() != null ? loc.getWorld().getName() : "?",
                                        loc.getX(), loc.getY(), loc.getZ()),
                                NamedTextColor.WHITE)));
            }

            sender.sendMessage(Component.text(""));
            index++;
        }

        return true;
    }

    /**
     * Sends the usage message to the sender.
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("Usage: /wither <spawn|kill|reload|stats>", NamedTextColor.GRAY)));
    }

    /**
     * Formats a duration in seconds to a human-readable string (e.g., "2m 30s").
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + secs + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m " + secs + "s";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(partial))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
