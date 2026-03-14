package com.blockforge.horizonutilities.story.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;

/**
 * Soft dependency hook for MythicMobs.
 * Uses reflection to avoid compile-time dependency.
 * Falls back to null (vanilla spawning) if MythicMobs is not present.
 */
public class MythicMobsHook {

    private boolean available;
    private Object mythicInst;
    private Method getMobManagerMethod;
    private Method spawnMobMethod;
    private Method getEntityMethod;
    private Method getBukkitEntityMethod;

    public MythicMobsHook() {
        try {
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
                available = false;
                return;
            }

            Class<?> mythicClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method instMethod = mythicClass.getMethod("inst");
            mythicInst = instMethod.invoke(null);

            getMobManagerMethod = mythicInst.getClass().getMethod("getMobManager");
            Object mobManager = getMobManagerMethod.invoke(mythicInst);

            // Find spawnMob(String, Location) method
            for (Method m : mobManager.getClass().getMethods()) {
                if (m.getName().equals("spawnMob") && m.getParameterCount() == 2) {
                    spawnMobMethod = m;
                    break;
                }
            }

            if (spawnMobMethod == null) {
                available = false;
                return;
            }

            // Cache entity accessor methods
            Class<?> activeMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
            getEntityMethod = activeMobClass.getMethod("getEntity");

            Class<?> abstractEntityClass = Class.forName("io.lumine.mythic.api.adapters.AbstractEntity");
            getBukkitEntityMethod = abstractEntityClass.getMethod("getBukkitEntity");

            available = true;
            Bukkit.getLogger().info("[Story] MythicMobs hooked successfully.");
        } catch (Exception e) {
            available = false;
            Bukkit.getLogger().info("[Story] MythicMobs not found or incompatible — using vanilla fallback.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Spawns a MythicMobs mob at the given location.
     * Returns the spawned Bukkit entity, or null if unavailable.
     */
    public Entity spawnMob(String mythicId, Location location) {
        if (!available) return null;
        try {
            Object mobManager = getMobManagerMethod.invoke(mythicInst);
            Object activeMob = spawnMobMethod.invoke(mobManager, mythicId, location);
            if (activeMob == null) return null;

            Object abstractEntity = getEntityMethod.invoke(activeMob);
            if (abstractEntity == null) return null;

            Object bukkitEntity = getBukkitEntityMethod.invoke(abstractEntity);
            return (Entity) bukkitEntity;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Story] Failed to spawn MythicMobs mob '" + mythicId + "': " + e.getMessage());
            return null;
        }
    }
}
