package com.blockforge.horizonutilities.tournaments;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all tournament lifecycle: creation, joining, scoring, and ending.
 */
public class TournamentManager {

    private final HorizonUtilitiesPlugin plugin;
    /** id -> Tournament */
    private final Map<String, Tournament> tournaments = new ConcurrentHashMap<>();

    public TournamentManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public Tournament create(String name, UUID creatorUuid, TournamentObjectiveType objective,
                             String targetMaterial, int targetScore, int durationSeconds,
                             double rewardMoney) {
        String id = name.toLowerCase(Locale.ROOT).replace(" ", "_") + "_" + System.currentTimeMillis();
        Tournament t = new Tournament(id, name, creatorUuid, objective, targetMaterial,
                targetScore, durationSeconds, null, rewardMoney);
        tournaments.put(id, t);
        broadcast(Component.text("[Tournament] ", NamedTextColor.GOLD)
                .append(Component.text("\"" + name + "\" ", NamedTextColor.YELLOW))
                .append(Component.text("is now open! Use /tournament join " + id, NamedTextColor.WHITE)));
        return t;
    }

    public boolean start(String id) {
        Tournament t = tournaments.get(id);
        if (t == null || t.getStatus() != Tournament.Status.OPEN) return false;
        t.start();
        broadcast(Component.text("[Tournament] ", NamedTextColor.GOLD)
                .append(Component.text("\"" + t.getName() + "\" ", NamedTextColor.YELLOW))
                .append(Component.text("has started! Objective: ", NamedTextColor.WHITE))
                .append(Component.text(formatObjective(t), NamedTextColor.AQUA)));
        return true;
    }

    public boolean stop(String id) {
        Tournament t = tournaments.get(id);
        if (t == null || t.getStatus() == Tournament.Status.ENDED) return false;
        endTournament(t);
        return true;
    }

    public boolean join(Player player, String id) {
        Tournament t = tournaments.get(id);
        if (t == null) {
            player.sendMessage(Component.text("Tournament not found.", NamedTextColor.RED));
            return false;
        }
        if (t.getStatus() == Tournament.Status.ENDED) {
            player.sendMessage(Component.text("That tournament has ended.", NamedTextColor.RED));
            return false;
        }
        if (t.getParticipants().contains(player.getUniqueId())) {
            player.sendMessage(Component.text("You're already in that tournament.", NamedTextColor.RED));
            return false;
        }
        t.join(player.getUniqueId());
        player.sendMessage(Component.text("Joined tournament: ", NamedTextColor.GREEN)
                .append(Component.text(t.getName(), NamedTextColor.GOLD)));
        return true;
    }

    public boolean leave(Player player, String id) {
        Tournament t = tournaments.get(id);
        if (t == null || !t.getParticipants().contains(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not in that tournament.", NamedTextColor.RED));
            return false;
        }
        t.leave(player.getUniqueId());
        player.sendMessage(Component.text("Left tournament: ", NamedTextColor.YELLOW)
                .append(Component.text(t.getName(), NamedTextColor.GOLD)));
        return true;
    }

    // -------------------------------------------------------------------------
    // Score updates (called from TournamentListener)
    // -------------------------------------------------------------------------

    /** Records a score increment for a player across all matching active tournaments. */
    public void recordProgress(Player player, TournamentObjectiveType type, String material, int amount) {
        for (Tournament t : tournaments.values()) {
            if (t.getStatus() != Tournament.Status.ACTIVE) continue;
            if (!t.getParticipants().contains(player.getUniqueId())) continue;
            if (t.getObjective() != type) continue;
            if (!t.getTargetMaterial().isEmpty() &&
                    !t.getTargetMaterial().equalsIgnoreCase(material)) continue;

            t.addScore(player.getUniqueId(), amount);
            int score = t.getScore(player.getUniqueId());
            player.sendActionBar(Component.text("[" + t.getName() + "] ", NamedTextColor.GOLD)
                    .append(Component.text("Score: " + score, NamedTextColor.YELLOW)));

            // Check first-to-target
            if (t.getTargetScore() > 0 && score >= t.getTargetScore()) {
                endTournament(t);
            }
        }
    }

    // -------------------------------------------------------------------------
    // End / reward
    // -------------------------------------------------------------------------

    private void endTournament(Tournament t) {
        t.end();
        UUID winnerUuid = t.getWinner();
        Player winner = winnerUuid != null ? Bukkit.getPlayer(winnerUuid) : null;

        if (winner != null) {
            broadcast(Component.text("[Tournament] ", NamedTextColor.GOLD)
                    .append(Component.text("\"" + t.getName() + "\" ", NamedTextColor.YELLOW))
                    .append(Component.text("ended! Winner: ", NamedTextColor.WHITE))
                    .append(Component.text(winner.getName(), NamedTextColor.GREEN))
                    .append(Component.text(" with score " + t.getScore(winnerUuid) + "!", NamedTextColor.WHITE)));

            if (t.getRewardMoney() > 0 && plugin.getVaultHook().isAvailable()) {
                plugin.getVaultHook().deposit(winner, t.getRewardMoney());
                winner.sendMessage(Component.text("You won ", NamedTextColor.GOLD)
                        .append(Component.text(plugin.getVaultHook().format(t.getRewardMoney()), NamedTextColor.GREEN))
                        .append(Component.text(" as the tournament prize!", NamedTextColor.GOLD)));
            }
            for (var item : t.getRewards()) {
                winner.getInventory().addItem(item);
            }
        } else {
            broadcast(Component.text("[Tournament] ", NamedTextColor.GOLD)
                    .append(Component.text("\"" + t.getName() + "\" ended with no winner.", NamedTextColor.GRAY)));
        }

        // Remove after a delay so players can check status
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> tournaments.remove(t.getId()), 600L); // remove after 30s
    }

    // -------------------------------------------------------------------------
    // Tick task for timed tournaments
    // -------------------------------------------------------------------------

    private void startTickTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Tournament t : new ArrayList<>(tournaments.values())) {
                if (t.getStatus() == Tournament.Status.ACTIVE && t.isExpired()) {
                    endTournament(t);
                }
            }
        }, 20L, 20L);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public Tournament get(String id) { return tournaments.get(id); }
    public Collection<Tournament> getAll() { return Collections.unmodifiableCollection(tournaments.values()); }

    public List<Tournament> getByStatus(Tournament.Status status) {
        return tournaments.values().stream().filter(t -> t.getStatus() == status).toList();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void broadcast(Component msg) {
        Bukkit.broadcast(msg);
    }

    private String formatObjective(Tournament t) {
        String mat = t.getTargetMaterial().isEmpty() ? "anything" : t.getTargetMaterial();
        return switch (t.getObjective()) {
            case KILL_PLAYERS -> "Kill Players";
            case KILL_MOBS    -> "Kill Mobs (" + mat + ")";
            case MINE_BLOCKS  -> "Mine Blocks (" + mat + ")";
            case CRAFT_ITEMS  -> "Craft Items (" + mat + ")";
            case COLLECT_ITEMS -> "Collect Items (" + mat + ")";
            case SURVIVE_TIME  -> "Survive " + t.getDurationSeconds() + "s";
        };
    }
}
