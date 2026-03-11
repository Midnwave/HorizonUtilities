package com.blockforge.horizonutilities.quests;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages boss bars that show quest progress to players.
 * Bars appear on progress updates and auto-hide after a configurable duration.
 */
public class QuestBossBarManager {

    private final HorizonUtilitiesPlugin plugin;
    private final QuestConfig config;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /** player UUID -> (quest dbId -> active bar entry) */
    private final Map<UUID, Map<Integer, BarEntry>> activeBars = new ConcurrentHashMap<>();

    public QuestBossBarManager(HorizonUtilitiesPlugin plugin, QuestConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Show or update a boss bar for quest progress.
     */
    public void showProgress(Player player, ActiveQuest quest) {
        if (!config.isBossBarEnabled()) return;

        ActiveQuest.ActiveStep step = quest.getCurrentStep();
        if (step == null) return;

        UUID uuid = player.getUniqueId();
        Map<Integer, BarEntry> bars = activeBars.computeIfAbsent(uuid, k -> new LinkedHashMap<>());

        // Enforce max bars — remove oldest if at limit
        int maxBars = config.getBossBarMaxBars();
        while (bars.size() >= maxBars && !bars.containsKey(quest.getDbId())) {
            Iterator<Map.Entry<Integer, BarEntry>> it = bars.entrySet().iterator();
            if (it.hasNext()) {
                BarEntry oldest = it.next().getValue();
                hideBar(player, oldest);
                it.remove();
            }
        }

        float progress = Math.min(1.0f, (float) step.getCurrentProgress() / step.getTargetAmount());
        BossBar.Color color = getCategoryColor(quest.getCategory());
        BossBar.Overlay overlay = config.getBossBarOverlay();

        // Build title: "⛏ Quest Name: Mine 5/10 Stone"
        String icon = quest.getCategory().getIcon();
        String title = config.getBossBarFormat()
            .replace("<icon>", icon)
            .replace("<quest>", quest.getQuestName())
            .replace("<description>", step.getDescription())
            .replace("<progress>", String.valueOf(step.getCurrentProgress()))
            .replace("<total>", String.valueOf(step.getTargetAmount()));

        Component titleComponent = mm.deserialize(title);

        BarEntry existing = bars.get(quest.getDbId());
        if (existing != null) {
            // Update existing bar
            existing.bar.name(titleComponent);
            existing.bar.progress(progress);
            existing.bar.color(color);
            resetHideTimer(player, quest.getDbId(), existing);
        } else {
            // Create new bar
            BossBar bar = BossBar.bossBar(titleComponent, progress, color, overlay);
            BarEntry entry = new BarEntry(bar, -1);
            bars.put(quest.getDbId(), entry);
            player.showBossBar(bar);
            resetHideTimer(player, quest.getDbId(), entry);
        }
    }

    /**
     * Show a completion boss bar that auto-hides.
     */
    public void showCompleted(Player player, ActiveQuest quest) {
        if (!config.isBossBarEnabled() || !config.isBossBarShowOnComplete()) return;

        UUID uuid = player.getUniqueId();
        Map<Integer, BarEntry> bars = activeBars.computeIfAbsent(uuid, k -> new LinkedHashMap<>());

        // Remove any existing bar for this quest
        BarEntry existing = bars.remove(quest.getDbId());
        if (existing != null) {
            hideBar(player, existing);
        }

        String icon = quest.getCategory().getIcon();
        String title = config.getBossBarCompleteFormat()
            .replace("<icon>", icon)
            .replace("<quest>", quest.getQuestName());

        Component titleComponent = mm.deserialize(title);
        BossBar bar = BossBar.bossBar(titleComponent, 1.0f, BossBar.Color.GREEN, config.getBossBarOverlay());

        int completeKey = quest.getDbId() + 100000; // offset to avoid collision
        BarEntry entry = new BarEntry(bar, -1);
        bars.put(completeKey, entry);
        player.showBossBar(bar);

        // Auto-hide after complete duration
        int completeTicks = config.getBossBarCompleteDurationSeconds() * 20;
        entry.taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.hideBossBar(bar);
            Map<Integer, BarEntry> b = activeBars.get(uuid);
            if (b != null) b.remove(completeKey);
        }, completeTicks).getTaskId();
    }

    /**
     * Remove all boss bars for a player (on quit or shutdown).
     */
    public void removeAll(Player player) {
        Map<Integer, BarEntry> bars = activeBars.remove(player.getUniqueId());
        if (bars == null) return;

        for (BarEntry entry : bars.values()) {
            hideBar(player, entry);
        }
    }

    /**
     * Shut down — clean up all bars for all players.
     */
    public void shutdown() {
        for (Map.Entry<UUID, Map<Integer, BarEntry>> playerEntry : activeBars.entrySet()) {
            Player player = Bukkit.getPlayer(playerEntry.getKey());
            if (player != null) {
                for (BarEntry entry : playerEntry.getValue().values()) {
                    hideBar(player, entry);
                }
            }
        }
        activeBars.clear();
    }

    // ===== Internal =====

    private void resetHideTimer(Player player, int questId, BarEntry entry) {
        // Cancel previous hide task
        if (entry.taskId != -1) {
            Bukkit.getScheduler().cancelTask(entry.taskId);
        }

        int durationSeconds = config.getBossBarDurationSeconds();
        if (durationSeconds <= 0) return; // 0 = permanent

        UUID uuid = player.getUniqueId();
        entry.taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.hideBossBar(entry.bar);
            Map<Integer, BarEntry> bars = activeBars.get(uuid);
            if (bars != null) bars.remove(questId);
        }, durationSeconds * 20L).getTaskId();
    }

    private void hideBar(Player player, BarEntry entry) {
        if (entry.taskId != -1) {
            Bukkit.getScheduler().cancelTask(entry.taskId);
        }
        player.hideBossBar(entry.bar);
    }

    private BossBar.Color getCategoryColor(QuestCategory category) {
        BossBar.Color color = config.getBossBarCategoryColors().get(category);
        return color != null ? color : BossBar.Color.WHITE;
    }

    private static class BarEntry {
        final BossBar bar;
        int taskId;

        BarEntry(BossBar bar, int taskId) {
            this.bar = bar;
            this.taskId = taskId;
        }
    }
}
