package com.blockforge.horizonutilities.quests.scoreboard;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.QuestStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages scoreboard lifecycle, mode switching, and preferences.
 * Supports two modes: NORMAL (AnimatedScoreboard) and QUESTS (custom quest display).
 */
public class ScoreboardManager {

    private final HorizonUtilitiesPlugin plugin;
    private final QuestManager questManager;
    private final QuestStorage questStorage;
    private final ScoreboardConfig config;
    private final QuestScoreboard questScoreboard;

    /** Player preferences: enabled state */
    private final Map<UUID, Boolean> enabledState = new ConcurrentHashMap<>();
    /** Player preferences: current mode (NORMAL or QUESTS) */
    private final Map<UUID, String> modeState = new ConcurrentHashMap<>();

    private int updateTaskId = -1;

    public ScoreboardManager(HorizonUtilitiesPlugin plugin, QuestManager questManager,
                              QuestStorage questStorage, ScoreboardConfig config) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.questStorage = questStorage;
        this.config = config;
        this.questScoreboard = new QuestScoreboard(questManager, config);

        startUpdateTask();
    }

    /**
     * Load player preferences on join.
     */
    public void onPlayerJoin(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String[] prefs = questStorage.loadScoreboardPrefs(uuid);
            if (prefs != null) {
                enabledState.put(uuid, Boolean.parseBoolean(prefs[0]));
                modeState.put(uuid, prefs[1]);
            } else {
                enabledState.put(uuid, true);
                modeState.put(uuid, "NORMAL");
            }
        });
    }

    /**
     * Cleanup on quit.
     */
    public void onPlayerQuit(UUID uuid) {
        enabledState.remove(uuid);
        modeState.remove(uuid);
    }

    /**
     * Check if scoreboard is enabled for a player.
     */
    public boolean isEnabled(UUID uuid) {
        return enabledState.getOrDefault(uuid, true);
    }

    /**
     * Set scoreboard enabled/disabled.
     */
    public void setEnabled(UUID uuid, boolean enabled) {
        enabledState.put(uuid, enabled);
        savePrefs(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        if (enabled) {
            updatePlayer(player);
        } else {
            // Reset to main scoreboard so AnimatedScoreboard can take over
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * Get current mode for a player.
     */
    public String getMode(UUID uuid) {
        return modeState.getOrDefault(uuid, "NORMAL");
    }

    /**
     * Switch scoreboard mode.
     */
    public void setMode(UUID uuid, String mode) {
        modeState.put(uuid, mode);
        savePrefs(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !isEnabled(uuid)) return;

        if (mode.equals("QUESTS")) {
            questScoreboard.render(player);
        } else {
            // Reset to main scoreboard so AnimatedScoreboard re-applies
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * Auto-switch to quest mode when quests are assigned (if config allows).
     */
    public void onQuestsAssigned(UUID uuid) {
        if (!config.isAutoSwitchOnAssign()) return;
        if (!isEnabled(uuid)) return;

        modeState.put(uuid, "QUESTS");
        savePrefs(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            questScoreboard.render(player);
        }
    }

    /**
     * Update a single player's scoreboard if they're in quest mode.
     */
    private void updatePlayer(Player player) {
        if (!isEnabled(player.getUniqueId())) return;
        String mode = getMode(player.getUniqueId());

        if (mode.equals("QUESTS")) {
            questScoreboard.render(player);
        }
        // NORMAL mode: don't touch, let AnimatedScoreboard handle it
    }

    /**
     * Start the periodic update task.
     */
    private void startUpdateTask() {
        int interval = config.getUpdateIntervalTicks();
        updateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (getMode(player.getUniqueId()).equals("QUESTS") && isEnabled(player.getUniqueId())) {
                    questScoreboard.render(player);
                }
            }
        }, interval, interval).getTaskId();
    }

    private void savePrefs(UUID uuid) {
        boolean enabled = isEnabled(uuid);
        String mode = getMode(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            questStorage.saveScoreboardPrefs(uuid, enabled, mode));
    }

    public void shutdown() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
        }
        enabledState.clear();
        modeState.clear();
    }

    public void reload() {
        config.load();
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
        }
        startUpdateTask();
    }
}
