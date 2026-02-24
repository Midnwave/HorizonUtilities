package com.blockforge.horizonutilities.games;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

public class GameScheduler {

    private final HorizonUtilitiesPlugin plugin;
    private final ChatGameManager manager;
    private BukkitTask task;

    public GameScheduler(HorizonUtilitiesPlugin plugin, ChatGameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        scheduleNext();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void scheduleNext() {
        if (task != null) task.cancel();

        int interval = plugin.getChatGamesConfig().getIntervalSeconds();
        int variance = plugin.getChatGamesConfig().getVarianceSeconds();
        int delay = interval + ThreadLocalRandom.current().nextInt(-variance, variance + 1);
        if (delay < 10) delay = 10;

        task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            manager.startRandomGame();
            // next game scheduled after this one ends (via manager)
        }, delay * 20L);
    }
}
