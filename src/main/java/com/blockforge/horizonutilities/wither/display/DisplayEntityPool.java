package com.blockforge.horizonutilities.wither.display;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and manages display entities (ItemDisplay, BlockDisplay, ArmorStand)
 * spawned during the Wither Sovereign boss fight. Provides lifecycle management
 * with automatic cleanup and timed removal.
 */
public class DisplayEntityPool {

    private final HorizonUtilitiesPlugin plugin;
    private final Set<UUID> trackedEntities = ConcurrentHashMap.newKeySet();

    public DisplayEntityPool(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Adds an entity to the tracked set.
     */
    public void track(Entity entity) {
        trackedEntities.add(entity.getUniqueId());
    }

    /**
     * Removes an entity from the tracked set and removes it from the world.
     */
    public void remove(Entity entity) {
        trackedEntities.remove(entity.getUniqueId());
        if (entity.isValid()) {
            entity.remove();
        }
    }

    /**
     * Removes ALL tracked entities from the world and clears the tracking set.
     */
    public void cleanup() {
        Iterator<UUID> it = trackedEntities.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            it.remove();
        }
    }

    /**
     * Checks all tracked entities and removes any that are no longer valid
     * (dead, despawned, or otherwise invalidated).
     */
    public void removeExpired() {
        Iterator<UUID> it = trackedEntities.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || !entity.isValid()) {
                it.remove();
            }
        }
    }

    /**
     * Spawns an ItemDisplay entity at the given location with the specified item.
     * The entity is automatically tracked and scheduled for removal after the lifetime expires.
     *
     * @param location      where to spawn the display
     * @param itemStack     the item to display
     * @param lifetimeTicks how many ticks before the entity is removed
     * @return the spawned ItemDisplay entity
     */
    public ItemDisplay spawnItemDisplay(Location location, ItemStack itemStack, int lifetimeTicks) {
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(itemStack);
            entity.setPersistent(false);
        });

        track(display);

        // Schedule removal after lifetime
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (trackedEntities.contains(display.getUniqueId())) {
                remove(display);
            }
        }, lifetimeTicks);

        return display;
    }

    /**
     * Spawns a BlockDisplay entity at the given location with the specified block data.
     * The entity is automatically tracked and scheduled for removal after the lifetime expires.
     *
     * @param location      where to spawn the display
     * @param blockData     the block data to display
     * @param lifetimeTicks how many ticks before the entity is removed
     * @return the spawned BlockDisplay entity
     */
    public BlockDisplay spawnBlockDisplay(Location location, BlockData blockData, int lifetimeTicks) {
        BlockDisplay display = location.getWorld().spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(blockData);
            entity.setPersistent(false);
        });

        track(display);

        // Schedule removal after lifetime
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (trackedEntities.contains(display.getUniqueId())) {
                remove(display);
            }
        }, lifetimeTicks);

        return display;
    }

    /**
     * Returns the number of currently tracked entities.
     */
    public int getTrackedCount() {
        return trackedEntities.size();
    }
}
