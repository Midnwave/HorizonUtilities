package com.blockforge.horizonutilities.bounty.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.bounty.Bounty;
import com.blockforge.horizonutilities.bounty.BountyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Periodically scans for bounty targets near online players and shows
 * an action-bar alert to the potential bounty hunter.
 *
 * Rate-limited to once every 5 seconds per (viewer, target) pair.
 */
public class BountyProximityListener {

    /** Minimum milliseconds between repeated alerts for the same (viewer, target) pair. */
    private static final long ALERT_COOLDOWN_MS = 5_000L;

    private final HorizonUtilitiesPlugin plugin;
    private final BountyManager bountyManager;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private BukkitTask task;

    /**
     * (viewerUuid -> (targetUuid -> lastAlertTime))
     */
    private final Map<UUID, Map<UUID, Long>> lastAlertTimes = new HashMap<>();

    public BountyProximityListener(HorizonUtilitiesPlugin plugin, BountyManager bountyManager) {
        this.plugin        = plugin;
        this.bountyManager = bountyManager;
        startTask();
    }

    private void startTask() {
        // Run every 2 seconds (40 ticks)
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void tick() {
        double range = bountyManager.getBountyConfig().getProximityAlertRange();
        double rangeSq = range * range;
        long now = System.currentTimeMillis();

        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();

        for (Player hunter : online) {
            Map<UUID, Long> hunterCooldowns =
                    lastAlertTimes.computeIfAbsent(hunter.getUniqueId(), k -> new HashMap<>());

            for (Player nearby : online) {
                if (nearby.getUniqueId().equals(hunter.getUniqueId())) continue;
                if (!nearby.getWorld().equals(hunter.getWorld())) continue;
                if (hunter.getLocation().distanceSquared(nearby.getLocation()) > rangeSq) continue;

                List<Bounty> bounties = bountyManager.getActiveBounties(nearby.getUniqueId());
                bounties.removeIf(b -> !b.isActive());
                if (bounties.isEmpty()) continue;

                double total = Bounty.getTotalValue(bounties);

                // Rate limit check
                long lastAlert = hunterCooldowns.getOrDefault(nearby.getUniqueId(), 0L);
                if (now - lastAlert < ALERT_COOLDOWN_MS) continue;

                hunterCooldowns.put(nearby.getUniqueId(), now);

                // Send action bar
                hunter.sendActionBar(mm.deserialize(
                        "<red>\u2694 " + nearby.getName() + " has a " +
                        plugin.getVaultHook().format(total) + " bounty! \u2694</red>"));
            }
        }

        // Clean up cooldown entries for players who are no longer online
        lastAlertTimes.entrySet().removeIf(e ->
                plugin.getServer().getPlayer(e.getKey()) == null);
    }
}
