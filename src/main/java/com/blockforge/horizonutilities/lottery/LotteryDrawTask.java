package com.blockforge.horizonutilities.lottery;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodic task (every 60 seconds on the main thread) that:
 *   1. Ensures every configured tier has an active instance.
 *   2. Triggers draws for any instance whose drawAt time has passed.
 */
public class LotteryDrawTask extends BukkitRunnable {

    private final HorizonUtilitiesPlugin plugin;
    private final LotteryManager manager;

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

        // Check each active instance for an overdue draw
        for (LotteryInstance inst : manager.getActiveInstances().values()) {
            if (inst.isActive() && now >= inst.getDrawAt()) {
                plugin.getLogger().info("[Lottery] Draw time reached for tier: " + inst.getTierId());
                manager.drawLottery(inst.getTierId());
            }
        }
    }
}
