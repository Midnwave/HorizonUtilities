package com.blockforge.horizonutilities.wither;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.wither.attacks.AttackRegistry;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wither;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all active custom Wither Sovereign boss fights.
 * Tracks spawned bosses, provides lookup, and coordinates subsystems.
 */
public class WitherBossManager {

    private final HorizonUtilitiesPlugin plugin;
    private WitherBossConfig config;
    private final WitherMusicManager musicManager;
    private final WitherLootManager lootManager;

    // Active boss fights keyed by the Wither entity UUID
    private final Map<UUID, WitherBossEntity> activeBosses = new ConcurrentHashMap<>();

    public WitherBossManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.config = new WitherBossConfig(plugin);
        this.config.load();
        this.musicManager = new WitherMusicManager(plugin, config);
        this.lootManager = new WitherLootManager(plugin, config);
    }

    /**
     * Spawns a custom Wither Sovereign boss at the given location.
     * The wither is spawned, wrapped in a WitherBossEntity, initialized with scaled health,
     * all attacks are registered, and music starts.
     *
     * @param location      spawn location
     * @param nearbyPlayers number of nearby players (for HP scaling)
     * @return the created WitherBossEntity, or null if spawning failed
     */
    public WitherBossEntity spawnBoss(Location location, int nearbyPlayers) {
        if (location.getWorld() == null) return null;

        Wither wither = (Wither) location.getWorld().spawnEntity(location, EntityType.WITHER);
        WitherBossEntity boss = new WitherBossEntity(plugin, config, wither, location);
        boss.initialize(nearbyPlayers);

        // Register all attacks with the scheduler
        AttackRegistry.registerAll(boss.getAttackScheduler());

        activeBosses.put(wither.getUniqueId(), boss);
        musicManager.startMusic(boss);

        plugin.getLogger().info("Wither Sovereign spawned at " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ()
                + " with " + nearbyPlayers + " players nearby.");
        return boss;
    }

    /**
     * Removes a boss from tracking and cleans up its resources.
     *
     * @param boss the boss entity to remove
     */
    public void removeBoss(WitherBossEntity boss) {
        activeBosses.remove(boss.getWither().getUniqueId());
        musicManager.stopMusic(boss);
        boss.cleanup();
    }

    /**
     * Removes a boss fight by the wither entity's UUID.
     * Calls cleanup on the boss entity and removes it from tracking.
     *
     * @param witherUuid the UUID of the wither entity
     */
    public void removeBoss(UUID witherUuid) {
        WitherBossEntity boss = activeBosses.get(witherUuid);
        if (boss != null) {
            removeBoss(boss);
        }
    }

    /**
     * Checks whether the given entity UUID belongs to a tracked custom Wither.
     */
    public boolean isCustomWither(UUID entityUuid) {
        return activeBosses.containsKey(entityUuid);
    }

    /**
     * Gets the WitherBossEntity for a tracked custom Wither.
     */
    public WitherBossEntity getBoss(UUID entityUuid) {
        return activeBosses.get(entityUuid);
    }

    /**
     * Returns all currently active boss fights.
     */
    public Collection<WitherBossEntity> getActiveBosses() {
        return Collections.unmodifiableCollection(activeBosses.values());
    }

    /**
     * Checks whether any active boss has this UUID in its activeMinions set.
     * Returns the owning boss, or null.
     */
    public WitherBossEntity findBossOwningMinion(UUID minionUuid) {
        for (WitherBossEntity boss : activeBosses.values()) {
            if (boss.getActiveMinions().contains(minionUuid)) {
                return boss;
            }
        }
        return null;
    }

    /**
     * Reloads the wither boss configuration from disk.
     * Does not affect currently active fights — they keep their existing config.
     */
    public void reload() {
        this.config = new WitherBossConfig(plugin);
        this.config.load();
        plugin.getLogger().info("Wither Sovereign configuration reloaded.");
    }

    /**
     * Shuts down all active boss fights. Called on plugin disable.
     * Cleans up all boss entities and removes withers from the world.
     */
    public void shutdown() {
        for (WitherBossEntity boss : new ArrayList<>(activeBosses.values())) {
            removeBoss(boss);
            if (boss.getWither().isValid()) {
                boss.getWither().remove();
            }
        }
        activeBosses.clear();
        plugin.getLogger().info("All Wither Sovereign fights cleaned up.");
    }

    public HorizonUtilitiesPlugin getPlugin() { return plugin; }
    public WitherBossConfig getConfig() { return config; }
    public WitherMusicManager getMusicManager() { return musicManager; }
    public WitherLootManager getLootManager() { return lootManager; }
}
