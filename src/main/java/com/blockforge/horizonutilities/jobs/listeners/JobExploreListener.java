package com.blockforge.horizonutilities.jobs.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import com.blockforge.horizonutilities.jobs.config.JobsConfig;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Awards EXPLORE_CHUNK income when a player enters a previously-undiscovered
 * chunk, or EXPLORE_DISTANCE income after accumulating enough travel distance,
 * depending on the configured explore mode.
 *
 * <p>PlayerMoveEvent is throttled: only fires when the player crosses a block
 * boundary to reduce overhead.
 */
public class JobExploreListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    /** DISTANCE mode: accumulated distance per player (blocks, not squared). */
    private final Map<UUID, Double> distanceAccum = new HashMap<>();

    /** CHUNK mode: last chunk coords per player to detect chunk crossings. */
    private final Map<UUID, long[]> lastChunk = new HashMap<>();

    public JobExploreListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        // Throttle: only process when the player moves at least one block
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        JobsConfig cfg = plugin.getJobManager().getConfig();

        if (cfg.getExploreMode() == JobsConfig.ExploreMode.CHUNK_DISCOVERY) {
            handleChunkDiscovery(event.getPlayer(), to);
        } else {
            handleDistance(event.getPlayer(), from, to, cfg);
        }
    }

    // -------------------------------------------------------------------------
    // Chunk discovery mode
    // -------------------------------------------------------------------------

    private void handleChunkDiscovery(Player player, Location to) {
        Chunk chunk = to.getChunk();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        UUID uuid = player.getUniqueId();

        long[] prev = lastChunk.get(uuid);
        if (prev != null && prev[0] == cx && prev[1] == cz) return; // same chunk
        lastChunk.put(uuid, new long[]{cx, cz});

        String world = to.getWorld().getName();

        // Check DB for previously discovered chunk
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (isChunkDiscovered(uuid, world, cx, cz)) return;
            insertDiscoveredChunk(uuid, world, cx, cz);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getJobManager().processAction(player, JobAction.EXPLORE_CHUNK, "CHUNK"));
        });
    }

    private boolean isChunkDiscovered(UUID uuid, String world, int cx, int cz) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT 1 FROM jobs_explored_chunks WHERE player_uuid=? AND world=? AND chunk_x=? AND chunk_z=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, world);
            ps.setInt(3, cx);
            ps.setInt(4, cz);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private void insertDiscoveredChunk(UUID uuid, String world, int cx, int cz) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT OR IGNORE INTO jobs_explored_chunks (player_uuid,world,chunk_x,chunk_z,discovered_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, world);
            ps.setInt(3, cx);
            ps.setInt(4, cz);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // -------------------------------------------------------------------------
    // Distance mode
    // -------------------------------------------------------------------------

    private void handleDistance(Player player, Location from, Location to, JobsConfig cfg) {
        if (!from.getWorld().equals(to.getWorld())) return;
        double dist = from.distance(to);
        UUID uuid = player.getUniqueId();

        double accum = distanceAccum.getOrDefault(uuid, 0.0) + dist;
        int threshold = cfg.getExploreDistanceThreshold();

        if (accum >= threshold) {
            int times = (int) (accum / threshold);
            accum -= times * threshold;
            for (int i = 0; i < times; i++) {
                plugin.getJobManager().processAction(player, JobAction.EXPLORE_DISTANCE, "DISTANCE");
            }
        }
        distanceAccum.put(uuid, accum);
    }

    public void clearPlayer(UUID uuid) {
        distanceAccum.remove(uuid);
        lastChunk.remove(uuid);
    }

    private Connection conn() {
        return plugin.getDatabaseManager().getConnection();
    }
}
