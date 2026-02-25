package com.blockforge.horizonutilities.lottery;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Periodic task (every 60 seconds on the main thread) that:
 *   1. Ensures every configured tier has an active instance.
 *   2. Broadcasts draw reminders at configured intervals.
 *   3. Triggers draws for any instance whose drawAt time has passed.
 */
public class LotteryDrawTask extends BukkitRunnable {

    private final HorizonUtilitiesPlugin plugin;
    private final LotteryManager manager;

    /** Tracks which reminder intervals have already been broadcast per instance. */
    private final Map<String, Set<Integer>> sentReminders = new HashMap<>();

    public LotteryDrawTask(HorizonUtilitiesPlugin plugin, LotteryManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Schedules this task to run every 60 seconds (1200 ticks), starting after 20 ticks.
     */
    public void start() {
        this.runTaskTimer(plugin, 20L, 1200L);
    }

    @Override
    public void run() {
        // Ensure all tiers have an active instance (creates one if missing)
        manager.ensureAllTiersActive();

        long now = System.currentTimeMillis();
        List<Integer> reminderMinutes = manager.getDrawReminders();

        for (LotteryInstance inst : manager.getActiveInstances().values()) {
            if (!inst.isActive()) continue;

            // Check for overdue draw
            if (now >= inst.getDrawAt()) {
                plugin.getLogger().info("[Lottery] Draw time reached for tier: " + inst.getTierId());
                manager.drawLottery(inst.getTierId());
                sentReminders.remove(inst.getTierId());
                continue;
            }

            // Check for draw reminders
            long minutesUntilDraw = (inst.getDrawAt() - now) / 60_000L;
            Set<Integer> sent = sentReminders.computeIfAbsent(inst.getTierId(), k -> new HashSet<>());

            for (int reminderMin : reminderMinutes) {
                if (minutesUntilDraw <= reminderMin && !sent.contains(reminderMin)) {
                    sent.add(reminderMin);
                    broadcastReminder(inst);
                    break; // only one reminder per tick per tier
                }
            }
        }
    }

    private void broadcastReminder(LotteryInstance inst) {
        LotteryTierConfig config = inst.getTierConfig();
        if (config == null || !config.isBroadcastDraw()) return;

        Bukkit.broadcast(plugin.getMessagesManager().format("lottery-draw-reminder",
                Placeholder.parsed("tier", config.getDisplayName()),
                Placeholder.unparsed("time", inst.getTimeUntilDraw()),
                Placeholder.unparsed("pot", plugin.getVaultHook().format(inst.getCurrentPot())),
                Placeholder.unparsed("tier_id", inst.getTierId())));
    }
}
