package com.blockforge.horizonutilities.wither.display;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Static utility class providing particle effect methods for the Wither Sovereign boss fight.
 * All methods spawn particles using the world's spawnParticle API.
 */
public final class WitherParticles {

    private WitherParticles() {
        // Static utility class — no instantiation
    }

    /**
     * Spawns particles in a horizontal ring around the center location.
     *
     * @param center   center of the ring
     * @param radius   radius of the ring
     * @param points   number of particles to spawn along the ring
     * @param particle the particle type
     * @param data     particle data (can be null for particles that don't use data)
     */
    public static void ring(Location center, double radius, int points, Particle particle, Object data) {
        World world = center.getWorld();
        double angleStep = (2 * Math.PI) / points;

        for (int i = 0; i < points; i++) {
            double angle = i * angleStep;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location point = new Location(world, x, center.getY(), z);

            if (data != null) {
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
            } else {
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Spawns an animated expanding ring that grows from startRadius to endRadius over time.
     * Each step spawns a ring, creating an outward-expanding pulse effect.
     *
     * @param center      center of the ring
     * @param startRadius starting radius
     * @param endRadius   ending radius
     * @param steps       number of expansion steps
     * @param intervalTicks ticks between each expansion step
     * @param particle    the particle type
     * @param plugin      plugin instance for scheduling
     */
    public static void expandingRing(Location center, double startRadius, double endRadius,
                                     int steps, int intervalTicks, Particle particle, Plugin plugin) {
        double radiusStep = (endRadius - startRadius) / Math.max(1, steps - 1);
        int pointsPerRing = 32;

        for (int step = 0; step < steps; step++) {
            double radius = startRadius + radiusStep * step;
            long delay = (long) step * intervalTicks;

            final double finalRadius = radius;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (center.getWorld() != null) {
                    ring(center, finalRadius, pointsPerRing, particle, null);
                }
            }, delay);
        }
    }

    /**
     * Spawns a line of particles between two locations (a beam).
     *
     * @param from     start location
     * @param to       end location
     * @param particle the particle type
     * @param data     particle data (can be null)
     * @param density  particles per block of distance
     */
    public static void beam(Location from, Location to, Particle particle, Object data, double density) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        int points = Math.max(1, (int) (distance * density));
        Vector step = direction.multiply(1.0 / points);

        Location current = from.clone();
        for (int i = 0; i <= points; i++) {
            if (data != null) {
                world.spawnParticle(particle, current, 1, 0, 0, 0, 0, data);
            } else {
                world.spawnParticle(particle, current, 1, 0, 0, 0, 0);
            }
            current.add(step);
        }
    }

    /**
     * Spawns particles in a vertical spiral pattern.
     *
     * @param center   base center of the spiral
     * @param radius   radius of the spiral
     * @param height   total height of the spiral
     * @param points   number of particles
     * @param particle the particle type
     * @param data     particle data (can be null)
     */
    public static void spiral(Location center, double radius, double height, int points,
                              Particle particle, Object data) {
        World world = center.getWorld();
        double angleStep = (4 * Math.PI) / points; // Two full rotations
        double heightStep = height / points;

        for (int i = 0; i < points; i++) {
            double angle = i * angleStep;
            double y = center.getY() + i * heightStep;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location point = new Location(world, x, y, z);

            if (data != null) {
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
            } else {
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Spawns particles in a cone shape from the origin in the given direction.
     *
     * @param origin    the tip of the cone
     * @param direction the direction the cone points
     * @param angle     half-angle of the cone in radians
     * @param length    length of the cone
     * @param points    number of particles
     * @param particle  the particle type
     * @param data      particle data (can be null)
     */
    public static void cone(Location origin, Vector direction, double angle, double length,
                            int points, Particle particle, Object data) {
        World world = origin.getWorld();
        Vector dir = direction.clone().normalize();

        // Build orthogonal basis vectors for the cone
        Vector arbitrary = Math.abs(dir.getY()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector ortho1 = dir.clone().crossProduct(arbitrary).normalize();
        Vector ortho2 = dir.clone().crossProduct(ortho1).normalize();

        for (int i = 0; i < points; i++) {
            double t = Math.random() * length;
            double currentRadius = t * Math.tan(angle);
            double theta = Math.random() * 2 * Math.PI;

            double offsetX = currentRadius * Math.cos(theta);
            double offsetZ = currentRadius * Math.sin(theta);

            Vector pointVec = dir.clone().multiply(t)
                    .add(ortho1.clone().multiply(offsetX))
                    .add(ortho2.clone().multiply(offsetZ));

            Location point = origin.clone().add(pointVec);

            if (data != null) {
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
            } else {
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Spawns particles distributed across the surface of a sphere.
     *
     * @param center   center of the sphere
     * @param radius   radius of the sphere
     * @param points   number of particles
     * @param particle the particle type
     * @param data     particle data (can be null)
     */
    public static void sphere(Location center, double radius, int points, Particle particle, Object data) {
        World world = center.getWorld();

        // Use golden spiral for even distribution
        double goldenRatio = (1 + Math.sqrt(5)) / 2;

        for (int i = 0; i < points; i++) {
            double theta = Math.acos(1 - 2.0 * (i + 0.5) / points);
            double phi = 2 * Math.PI * i / goldenRatio;

            double x = center.getX() + radius * Math.sin(theta) * Math.cos(phi);
            double y = center.getY() + radius * Math.cos(theta);
            double z = center.getZ() + radius * Math.sin(theta) * Math.sin(phi);

            Location point = new Location(world, x, y, z);

            if (data != null) {
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
            } else {
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Spawns a burst of particles radiating outward from the center (explosion effect).
     *
     * @param center   center of the explosion
     * @param count    number of particles
     * @param particle the particle type
     */
    public static void explosion(Location center, int count, Particle particle) {
        World world = center.getWorld();
        // Use spread parameters to create an outward burst effect
        world.spawnParticle(particle, center, count, 0.5, 0.5, 0.5, 0.15);
    }
}
