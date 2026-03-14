package com.blockforge.horizonutilities.wither.util;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import org.bukkit.Location;

/**
 * Static utility class for checking whether the Wither Sovereign boss fight
 * is allowed to modify blocks at a given location. Checks config settings,
 * max radius from spawn, and soft-dependency protection plugins (GriefPrevention, WorldGuard).
 */
public final class WitherGriefBypass {

    private WitherGriefBypass() {
        // Static utility class — no instantiation
    }

    /**
     * Checks if the boss fight is allowed to modify a block at the given location.
     *
     * @param location the block location to check
     * @param boss     the wither boss entity
     * @return true if the block can be modified, false otherwise
     */
    public static boolean canModifyBlock(Location location, WitherBossEntity boss) {
        // Check max radius from boss spawn location
        Location spawn = boss.getSpawnLocation();
        if (!location.getWorld().equals(spawn.getWorld())) {
            return false;
        }

        double distanceSq = location.distanceSquared(spawn);
        double maxRadius = boss.getConfig().getMaxRadius();
        if (distanceSq > maxRadius * maxRadius) {
            return false;
        }

        // If config says bypass grief protection, allow all modifications within radius
        if (boss.getConfig().isBypassGriefProtection()) {
            return true;
        }

        // Check GriefPrevention claims (soft dependency)
        if (!checkGriefPrevention(location)) {
            return false;
        }

        // Check WorldGuard regions (soft dependency)
        if (!checkWorldGuard(location)) {
            return false;
        }

        return true;
    }

    /**
     * Checks GriefPrevention: if the location is inside a claim, deny modification.
     * Wrapped in try-catch for soft dependency — returns true if GP is not present.
     */
    private static boolean checkGriefPrevention(Location location) {
        try {
            Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");

            me.ryanhamshire.GriefPrevention.GriefPrevention gp =
                    me.ryanhamshire.GriefPrevention.GriefPrevention.instance;
            if (gp == null) {
                return true;
            }

            me.ryanhamshire.GriefPrevention.Claim claim =
                    gp.dataStore.getClaimAt(location, false, null);

            // If there's a claim at this location, deny modification
            return claim == null;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // GriefPrevention not installed — allow
            return true;
        } catch (Exception e) {
            // Unexpected error — fail safe by allowing
            return true;
        }
    }

    /**
     * Checks WorldGuard: queries the BUILD flag at the location.
     * Wrapped in try-catch for soft dependency — returns true if WG is not present.
     */
    private static boolean checkWorldGuard(Location location) {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");

            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();

            com.sk89q.worldguard.protection.regions.RegionContainer container =
                    wg.getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();

            com.sk89q.worldedit.util.Location weLoc = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location);

            // Test BUILD flag — if denied, block modification is not allowed
            boolean allowed = query.testState(
                    weLoc,
                    null, // No player association — check raw region flags
                    com.sk89q.worldguard.protection.flags.Flags.BUILD
            );

            return allowed;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // WorldGuard not installed — allow
            return true;
        } catch (Exception e) {
            // Unexpected error — fail safe by allowing
            return true;
        }
    }
}
