package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Awards job income for killing entities. Applies the configured spawner pay
 * multiplier when the mob was tagged as spawner-sourced.
 */
public class JobKillListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    public JobKillListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        boolean isSpawner = plugin.getJobManager().getSpawnerTracker()
                .isSpawnerMob(event.getEntity());

        // If it is a spawner mob and the job entry has spawnerAllowed=false, processAction
        // will still be called — JobManager.processAction checks the entry's spawnerAllowed
        // flag via the action entry and applies the multiplier from config.
        // We mark it via a thread-local flag so JobManager can read it.
        JobKillContext.setSpawner(isSpawner);
        try {
            plugin.getJobManager().processAction(killer, JobAction.KILL,
                    event.getEntityType().name());
        } finally {
            JobKillContext.clear();
        }
    }

    /**
     * Thread-local carrier so {@link com.blockforge.horizonutilities.jobs.JobManager}
     * can read whether the current KILL action came from a spawner mob without
     * needing a separate method parameter.
     */
    public static final class JobKillContext {
        private static final ThreadLocal<Boolean> SPAWNER = ThreadLocal.withInitial(() -> false);
        public static void setSpawner(boolean v) { SPAWNER.set(v); }
        public static boolean isSpawner()         { return SPAWNER.get(); }
        public static void clear()                { SPAWNER.remove(); }
    }
}
