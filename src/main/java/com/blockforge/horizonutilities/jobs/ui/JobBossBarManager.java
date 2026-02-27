package com.blockforge.horizonutilities.jobs.ui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.JobLevelCalculator;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Displays a per-player boss bar showing XP progress toward the next job level.
 * The bar auto-hides after a configurable duration of inactivity.
 */
public class JobBossBarManager {

    private final HorizonUtilitiesPlugin plugin;

    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> hideTasks = new ConcurrentHashMap<>();

    public JobBossBarManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Updates (or creates) the player's job boss bar and schedules auto-hide.
     */
    public void showProgress(Player player, JobPlayer jp, Job job) {
        if (!plugin.getJobManager().getConfig().isBossBarEnabled()) return;

        double xpCurrent = jp.getXp();
        int level = jp.getLevel();
        double xpBase = plugin.getJobManager().getConfig().getXpBase();
        double xpExp  = plugin.getJobManager().getConfig().getXpExponent();

        double xpForThisLevel = JobLevelCalculator.getXpRequired(level, xpBase, xpExp);
        double xpForNextLevel = JobLevelCalculator.getXpRequired(level + 1, xpBase, xpExp);
        double span = xpForNextLevel - xpForThisLevel;
        float progress = span <= 0 ? 1.0f : (float) Math.min(1.0, (xpCurrent - xpForThisLevel) / span);

        int maxLevel = job.getMaxLevel() > 0 ? job.getMaxLevel() : plugin.getJobManager().getConfig().getMaxLevel();
        boolean isMaxed = level >= maxLevel;
        if (isMaxed) progress = 1.0f;
        final float finalProgress = progress;

        Component title = Component.text(job.getDisplayName() + " ", NamedTextColor.GOLD)
                .append(Component.text("Lv." + jp.getLevel(), NamedTextColor.AQUA))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.0f", xpCurrent) + " / " +
                        String.format("%.0f", xpForNextLevel) + " XP", NamedTextColor.YELLOW));

        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), k ->
                BossBar.bossBar(title, finalProgress,
                        plugin.getJobManager().getConfig().getBossBarColor(),
                        plugin.getJobManager().getConfig().getBossBarOverlay()));

        bar.name(title);
        bar.progress(progress);
        player.showBossBar(bar);

        // Schedule auto-hide
        BukkitTask old = hideTasks.remove(player.getUniqueId());
        if (old != null) old.cancel();

        long hideTicks = plugin.getJobManager().getConfig().getBossBarDurationSeconds() * 20L;
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            BossBar b = bossBars.remove(player.getUniqueId());
            if (b != null) player.hideBossBar(b);
            hideTasks.remove(player.getUniqueId());
        }, hideTicks);
        hideTasks.put(player.getUniqueId(), task);
    }

    /** Called on player quit to clean up any lingering bars and tasks. */
    public void cleanup(Player player) {
        BukkitTask task = hideTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }
}
