package com.blockforge.horizonutilities.wither.display;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Higher-level animation helpers for the Wither Sovereign boss fight.
 * Combines particle effects with display entities to create complex visual effects.
 * Each animation schedules its own cleanup via the DisplayEntityPool.
 */
public class WitherAnimations {

    private final HorizonUtilitiesPlugin plugin;
    private final DisplayEntityPool displayPool;

    public WitherAnimations(HorizonUtilitiesPlugin plugin, DisplayEntityPool displayPool) {
        this.plugin = plugin;
        this.displayPool = displayPool;
    }

    /**
     * Creates a spiraling dark vortex of particles rising from a point.
     * Uses SOUL_FIRE_FLAME and SMOKE particles in an ascending spiral.
     *
     * @param center        the base location of the vortex
     * @param durationTicks how long the animation lasts
     */
    public void darkVortex(Location center, int durationTicks) {
        final int[] tickRef = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (tickRef[0] >= durationTicks) {
                return;
            }

            double progress = (double) tickRef[0] / durationTicks;
            double currentHeight = progress * 6.0; // Rise up to 6 blocks
            double radius = 2.0 * (1.0 - progress * 0.5); // Shrinking radius as it rises

            // Spawn spiral particles at current height
            int particlesPerTick = 8;
            double baseAngle = tickRef[0] * 0.3;

            for (int i = 0; i < particlesPerTick; i++) {
                double angle = baseAngle + (2 * Math.PI / particlesPerTick) * i;
                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
                double y = center.getY() + currentHeight;

                Location point = new Location(center.getWorld(), x, y, z);
                center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0, 0.05, 0, 0.01);
                center.getWorld().spawnParticle(Particle.SMOKE, point, 1, 0.1, 0.1, 0.1, 0.01);
            }

            tickRef[0]++;
        }, 0L, 1L);

        // Schedule task cancellation
        Bukkit.getScheduler().runTaskLater(plugin, task::cancel, durationTicks);
    }

    /**
     * Creates a dark circle warning indicator on the ground using block displays.
     * The circle gradually appears and then fades/is removed after the duration.
     *
     * @param center        center of the circle on the ground
     * @param radius        radius of the warning circle
     * @param durationTicks how long the warning persists
     */
    public void groundCircleWarning(Location center, double radius, int durationTicks) {
        int segments = 24;
        double angleStep = (2 * Math.PI) / segments;
        BlockData blockData = Material.BLACK_CONCRETE.createBlockData();

        for (int i = 0; i < segments; i++) {
            double angle = i * angleStep;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);

            // Find ground level at this position
            Location blockLoc = new Location(center.getWorld(), x, center.getY(), z);
            Location ground = findGround(blockLoc);

            displayPool.spawnBlockDisplay(ground, blockData, durationTicks);
        }

        // Spawn warning particles along the circle
        final int[] tickRef = {0};
        BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (tickRef[0] >= durationTicks) {
                return;
            }

            WitherParticles.ring(center, radius, 16, Particle.DUST_PLUME, null);
            tickRef[0] += 5;
        }, 0L, 5L);

        Bukkit.getScheduler().runTaskLater(plugin, particleTask::cancel, durationTicks);
    }

    /**
     * Creates an item display of a wither skull that rises from one point to another.
     * The skull smoothly interpolates between the two locations over the duration.
     *
     * @param from          starting location
     * @param to            ending location
     * @param durationTicks how long the rise animation takes
     */
    public void floatingSkullRise(Location from, Location to, int durationTicks) {
        ItemStack skullItem = new ItemStack(Material.WITHER_SKELETON_SKULL);
        ItemDisplay skull = displayPool.spawnItemDisplay(from, skullItem, durationTicks + 10);

        // Set initial transformation scale
        skull.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(1.5f, 1.5f, 1.5f),
                new AxisAngle4f(0, 0, 1, 0)
        ));

        final int[] tickRef = {0};
        double dx = (to.getX() - from.getX()) / durationTicks;
        double dy = (to.getY() - from.getY()) / durationTicks;
        double dz = (to.getZ() - from.getZ()) / durationTicks;

        BukkitTask moveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (tickRef[0] >= durationTicks || !skull.isValid()) {
                return;
            }

            double x = from.getX() + dx * tickRef[0];
            double y = from.getY() + dy * tickRef[0];
            double z = from.getZ() + dz * tickRef[0];

            skull.teleport(new Location(from.getWorld(), x, y, z,
                    skull.getLocation().getYaw() + 10, 0));

            // Trail particles
            from.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    skull.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);

            tickRef[0]++;
        }, 0L, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, moveTask::cancel, durationTicks);
    }

    /**
     * Spawns block displays showing cracked/damaged ground in a circular area.
     * Simulates an impact crater effect.
     *
     * @param center center of the crater
     * @param radius radius of the impact area
     */
    public void impactCrater(Location center, double radius) {
        int crackBlocks = (int) (radius * radius * 2);
        BlockData cracked = Material.CRACKED_STONE_BRICKS.createBlockData();
        BlockData deepslate = Material.CRACKED_DEEPSLATE_BRICKS.createBlockData();

        for (int i = 0; i < crackBlocks; i++) {
            // Random point within the circle
            double angle = Math.random() * 2 * Math.PI;
            double dist = Math.random() * radius;
            double x = center.getX() + dist * Math.cos(angle);
            double z = center.getZ() + dist * Math.sin(angle);

            Location loc = new Location(center.getWorld(), x, center.getY(), z);
            Location ground = findGround(loc);

            // Alternate between crack types, prefer deepslate near center
            BlockData data = dist < radius * 0.5 ? deepslate : cracked;
            displayPool.spawnBlockDisplay(ground, data, 200); // 10 second lifetime
        }

        // Impact particles
        WitherParticles.explosion(center, 60, Particle.CAMPFIRE_SIGNAL_SMOKE);
        WitherParticles.ring(center, radius, 24, Particle.LAVA, null);
    }

    /**
     * Creates a particle chain connecting a player to an anchor point.
     * The chain persists for the given duration, updating each tick to follow the player.
     *
     * @param player        the player to chain
     * @param anchor        the fixed anchor location
     * @param durationTicks how long the chain persists
     */
    public void chainBeam(Player player, Location anchor, int durationTicks) {
        final int[] tickRef = {0};

        BukkitTask chainTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (tickRef[0] >= durationTicks || !player.isOnline() || player.isDead()) {
                return;
            }

            Location playerLoc = player.getLocation().add(0, 1, 0); // Chest height
            WitherParticles.beam(anchor, playerLoc, Particle.ENCHANTED_HIT, null, 3.0);

            // Chain link particles at intervals
            if (tickRef[0] % 5 == 0) {
                WitherParticles.beam(anchor, playerLoc, Particle.CRIT, null, 1.5);
            }

            tickRef[0]++;
        }, 0L, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, chainTask::cancel, durationTicks);
    }

    /**
     * Finds the ground level at a location by scanning downward for the first solid block.
     */
    private Location findGround(Location location) {
        Location ground = location.clone();
        for (int y = location.getBlockY(); y > location.getWorld().getMinHeight(); y--) {
            ground.setY(y);
            if (ground.getBlock().getType().isSolid()) {
                ground.setY(y + 1); // Place on top of the solid block
                return ground;
            }
        }
        return location;
    }
}
