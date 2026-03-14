package com.blockforge.horizonutilities.wither;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the ritual altar detection and throw-to-summon mechanic
 * for the Wither Sovereign boss fight.
 *
 * Players throw a "Soul of the Damned" item onto a correctly built altar
 * to trigger a cinematic summoning sequence.
 */
public class WitherSummonListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final WitherBossManager manager;

    public WitherSummonListener(HorizonUtilitiesPlugin plugin, WitherBossManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Detects when a player drops a "Soul of the Damned" item and schedules
     * an altar check after the item has landed.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!manager.getConfig().isSummoningEnabled()) return;

        Item droppedItem = event.getItemDrop();
        ItemStack stack = droppedItem.getItemStack();

        if (!isSoulOfTheDamned(stack)) return;

        Player summoner = event.getPlayer();

        // Schedule a check 10 ticks later so the item has time to land
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Verify the item entity still exists
            if (!droppedItem.isValid() || droppedItem.isDead()) return;

            Location itemLoc = droppedItem.getLocation();

            // Scan the area below the item for the altar pattern
            // Check at the item's block location and 1 block below
            Location checkCenter = itemLoc.clone();
            checkCenter.setY(Math.floor(checkCenter.getY()));

            // Try a few vertical offsets in case item is hovering
            boolean altarFound = false;
            Location altarCenter = null;
            for (int yOff = 0; yOff >= -2; yOff--) {
                Location test = checkCenter.clone().add(0, yOff, 0);
                if (checkAltar(test)) {
                    altarFound = true;
                    altarCenter = test;
                    break;
                }
            }

            if (!altarFound) return;

            // Remove the dropped item
            droppedItem.remove();

            // Count nearby players for HP scaling
            int nearbyPlayers = altarCenter.getNearbyPlayers(64).size();

            startSummoningCinematic(altarCenter, summoner, nearbyPlayers);

        }, 10L);
    }

    /**
     * Checks if an ItemStack is the "Soul of the Damned" ritual item.
     * Detection is based on display name containing the expected text,
     * or custom model data if present.
     */
    private boolean isSoulOfTheDamned(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;

        var meta = stack.getItemMeta();

        // Check display name via Adventure Component
        Component displayName = meta.displayName();
        if (displayName != null) {
            // Serialize to plain text and check for the ritual item name
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(displayName);
            if (plain.contains("Soul of the Damned")) {
                return true;
            }
        }

        // Fallback: check custom model data (convention: 66600 for wither ritual items)
        if (meta.hasCustomModelData() && meta.getCustomModelData() == 66600) {
            return true;
        }

        return false;
    }

    /**
     * Checks for the ritual altar pattern centered at the given location.
     *
     * Altar structure (top-down, center is the given location):
     * - Base layer (y=0): 3x3 soul soil platform
     * - Pillars (y=1..3): 4 blackstone pillars at corners (-1,-1), (-1,1), (1,-1), (1,1)
     * - Skulls (y=4): wither skeleton skulls on top of each pillar
     * - Decorations on platform: wither roses and soul lanterns (optional aesthetics, but
     *   we check for at least 1 wither rose and 1 soul lantern on the platform)
     *
     * @param center the center block of the platform (y = platform level)
     * @return true if the altar pattern is valid
     */
    public boolean checkAltar(Location center) {
        World world = center.getWorld();
        if (world == null) return false;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Check 3x3 soul soil platform at base level
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(cx + dx, cy, cz + dz);
                if (block.getType() != Material.SOUL_SOIL) {
                    return false;
                }
            }
        }

        // Corner offsets for the 4 pillars
        int[][] corners = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

        // Check 4 blackstone pillars (3 blocks high) at corners, starting at y+1
        for (int[] corner : corners) {
            for (int dy = 1; dy <= 3; dy++) {
                Block pillar = world.getBlockAt(cx + corner[0], cy + dy, cz + corner[1]);
                if (pillar.getType() != Material.POLISHED_BLACKSTONE_BRICKS
                        && pillar.getType() != Material.BLACKSTONE
                        && pillar.getType() != Material.POLISHED_BLACKSTONE) {
                    return false;
                }
            }
        }

        // Check 4 wither skeleton skulls on top of pillars (y+4)
        for (int[] corner : corners) {
            Block skullBlock = world.getBlockAt(cx + corner[0], cy + 4, cz + corner[1]);
            Material type = skullBlock.getType();
            if (type != Material.WITHER_SKELETON_SKULL
                    && type != Material.WITHER_SKELETON_WALL_SKULL) {
                return false;
            }
        }

        // Check for at least 1 wither rose and 1 soul lantern on the platform (y+1 level, non-corner)
        boolean hasWitherRose = false;
        boolean hasSoulLantern = false;

        // Check the 5 non-corner spots on the platform at y+1
        int[][] platformSpots = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] spot : platformSpots) {
            Block above = world.getBlockAt(cx + spot[0], cy + 1, cz + spot[1]);
            if (above.getType() == Material.WITHER_ROSE) {
                hasWitherRose = true;
            }
            if (above.getType() == Material.SOUL_LANTERN) {
                hasSoulLantern = true;
            }
        }

        return hasWitherRose && hasSoulLantern;
    }

    /**
     * Starts the cinematic summoning sequence at the altar.
     * Runs for 100 ticks (5 seconds) with progressive visual effects,
     * then spawns the Wither Sovereign boss.
     *
     * @param altarCenter   center of the altar platform
     * @param summoner      the player who initiated the ritual
     * @param nearbyPlayers count of nearby players for HP scaling
     */
    public void startSummoningCinematic(Location altarCenter, Player summoner, int nearbyPlayers) {
        World world = altarCenter.getWorld();
        if (world == null) return;

        int cx = altarCenter.getBlockX();
        int cy = altarCenter.getBlockY();
        int cz = altarCenter.getBlockZ();

        // Notify nearby players
        Location centerExact = altarCenter.clone().add(0.5, 0.5, 0.5);
        for (Player p : centerExact.getNearbyPlayers(64)) {
            p.showTitle(Title.title(
                    Component.text("The Wither Sovereign", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                    Component.text("has been summoned...", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
        }

        // Track spawned armor stands for cleanup
        List<ArmorStand> floatingSkullStands = new ArrayList<>();

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (world == null || !world.isChunkLoaded(cx >> 4, cz >> 4)) {
                    cleanup();
                    cancel();
                    return;
                }

                Location center = altarCenter.clone().add(0.5, 1.0, 0.5);

                // Phase 1 (ticks 0-20): Soul particle vortex spiraling upward
                if (tick <= 20) {
                    double progress = tick / 20.0;
                    for (int i = 0; i < 8; i++) {
                        double angle = (tick * 18.0 + i * 45.0) * Math.PI / 180.0;
                        double radius = 2.0 * (1.0 - progress * 0.3);
                        double height = progress * 5.0;
                        double x = center.getX() + Math.cos(angle) * radius;
                        double z = center.getZ() + Math.sin(angle) * radius;
                        Location particleLoc = new Location(world, x, center.getY() + height, z);
                        world.spawnParticle(Particle.SOUL, particleLoc, 2, 0.05, 0.05, 0.05, 0.01);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0.02, 0.02, 0.02, 0.005);
                    }

                    // Ambient sound
                    if (tick % 5 == 0) {
                        world.playSound(center, Sound.BLOCK_SOUL_SAND_BREAK, 2.0f, 0.5f);
                    }
                }

                // Phase 2 (ticks 20-40): Skull blocks float upward (armor stands with skull)
                if (tick == 20) {
                    int[][] corners = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
                    for (int[] corner : corners) {
                        Location skullLoc = new Location(world,
                                cx + corner[0] + 0.5, cy + 4.0, cz + corner[1] + 0.5);

                        // Remove the skull block
                        world.getBlockAt(cx + corner[0], cy + 4, cz + corner[1]).setType(Material.AIR);

                        // Spawn an invisible armor stand holding a wither skeleton skull
                        ArmorStand stand = world.spawn(skullLoc, ArmorStand.class, as -> {
                            as.setVisible(false);
                            as.setGravity(false);
                            as.setMarker(true);
                            as.setSmall(false);
                            as.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
                            as.setCustomNameVisible(false);
                        });
                        floatingSkullStands.add(stand);
                    }
                }

                if (tick > 20 && tick <= 40) {
                    // Float skull armor stands upward
                    double liftProgress = (tick - 20) / 20.0;
                    for (ArmorStand stand : floatingSkullStands) {
                        if (stand.isValid()) {
                            Location current = stand.getLocation();
                            current.setY(cy + 4.0 + liftProgress * 2.0);
                            stand.teleport(current);

                            // Trail particles
                            world.spawnParticle(Particle.SMOKE, current, 3, 0.1, 0.1, 0.1, 0.01);
                        }
                    }
                }

                // Phase 3 (ticks 40-60): Skulls converge to center point 5 blocks above altar
                if (tick > 40 && tick <= 60) {
                    double convergeProgress = (tick - 40) / 20.0;
                    Location convergencePoint = center.clone().add(0, 5, 0);

                    for (ArmorStand stand : floatingSkullStands) {
                        if (stand.isValid()) {
                            Location origin = stand.getLocation();
                            double newX = origin.getX() + (convergencePoint.getX() - origin.getX()) * convergeProgress * 0.15;
                            double newY = origin.getY() + (convergencePoint.getY() - origin.getY()) * convergeProgress * 0.15;
                            double newZ = origin.getZ() + (convergencePoint.getZ() - origin.getZ()) * convergeProgress * 0.15;
                            stand.teleport(new Location(world, newX, newY, newZ));

                            world.spawnParticle(Particle.SOUL_FIRE_FLAME, stand.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
                        }
                    }

                    // Growing dark particles at convergence
                    double particleRadius = convergeProgress * 1.5;
                    world.spawnParticle(Particle.LARGE_SMOKE, convergencePoint, (int) (5 + convergeProgress * 15),
                            particleRadius * 0.3, particleRadius * 0.3, particleRadius * 0.3, 0.02);

                    if (tick % 4 == 0) {
                        world.playSound(convergencePoint, Sound.ENTITY_WITHER_AMBIENT, 1.5f,
                                0.5f + (float) convergeProgress * 0.5f);
                    }
                }

                // Phase 4 (ticks 60-80): Growing dark orb at convergence point
                if (tick > 60 && tick <= 80) {
                    Location convergencePoint = center.clone().add(0, 5, 0);
                    double orbProgress = (tick - 60) / 20.0;
                    double orbSize = 0.5 + orbProgress * 2.0;

                    // Dense dark particle sphere
                    for (int i = 0; i < (int) (10 + orbProgress * 30); i++) {
                        double theta = Math.random() * Math.PI * 2;
                        double phi = Math.random() * Math.PI;
                        double r = orbSize * Math.random();
                        double px = Math.sin(phi) * Math.cos(theta) * r;
                        double py = Math.cos(phi) * r;
                        double pz = Math.sin(phi) * Math.sin(theta) * r;
                        world.spawnParticle(Particle.SMOKE, convergencePoint.clone().add(px, py, pz),
                                1, 0, 0, 0, 0);
                    }

                    // Intense soul particles
                    world.spawnParticle(Particle.SOUL, convergencePoint, 5,
                            orbSize * 0.2, orbSize * 0.2, orbSize * 0.2, 0.05);

                    // Rumble sound
                    if (tick % 3 == 0) {
                        world.playSound(convergencePoint, Sound.ENTITY_WITHER_SPAWN, 0.3f + (float) orbProgress * 0.7f,
                                0.3f + (float) orbProgress * 0.7f);
                    }
                }

                // Phase 5 (ticks 80-100): Massive explosion particles, remove altar, spawn boss
                if (tick == 80) {
                    // Remove floating skull armor stands
                    for (ArmorStand stand : floatingSkullStands) {
                        if (stand.isValid()) stand.remove();
                    }
                    floatingSkullStands.clear();
                }

                if (tick > 80 && tick < 100) {
                    Location convergencePoint = center.clone().add(0, 5, 0);
                    double explosionProgress = (tick - 80) / 20.0;

                    // Expanding explosion particles
                    double radius = explosionProgress * 6.0;
                    world.spawnParticle(Particle.EXPLOSION, convergencePoint, 3,
                            radius * 0.3, radius * 0.3, radius * 0.3, 0.1);
                    world.spawnParticle(Particle.LARGE_SMOKE, convergencePoint, 10,
                            radius * 0.4, radius * 0.4, radius * 0.4, 0.05);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, convergencePoint, 8,
                            radius * 0.3, radius * 0.3, radius * 0.3, 0.08);

                    // Screen shake via knockback
                    if (tick == 85) {
                        for (Player p : convergencePoint.getNearbyPlayers(32)) {
                            Vector dir = p.getLocation().toVector().subtract(convergencePoint.toVector()).normalize().multiply(0.3);
                            dir.setY(0.15);
                            p.setVelocity(p.getVelocity().add(dir));
                        }
                        world.playSound(convergencePoint, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
                    }
                }

                // Final tick: remove altar blocks and spawn the boss
                if (tick == 100) {
                    // Remove altar soul soil platform
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block block = world.getBlockAt(cx + dx, cy, cz + dz);
                            if (block.getType() == Material.SOUL_SOIL) {
                                block.setType(Material.AIR);
                            }
                        }
                    }

                    // Remove any remaining wither roses and soul lanterns on the platform
                    int[][] platformSpots = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                    for (int[] spot : platformSpots) {
                        Block above = world.getBlockAt(cx + spot[0], cy + 1, cz + spot[1]);
                        if (above.getType() == Material.WITHER_ROSE || above.getType() == Material.SOUL_LANTERN) {
                            above.setType(Material.AIR);
                        }
                    }

                    // Remove remaining skull blocks (in case they weren't removed earlier)
                    int[][] corners = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
                    for (int[] corner : corners) {
                        Block skullBlock = world.getBlockAt(cx + corner[0], cy + 4, cz + corner[1]);
                        if (skullBlock.getType() == Material.WITHER_SKELETON_SKULL
                                || skullBlock.getType() == Material.WITHER_SKELETON_WALL_SKULL) {
                            skullBlock.setType(Material.AIR);
                        }
                    }

                    // Spawn the Wither Sovereign boss 5 blocks above the altar center
                    Location spawnLoc = center.clone().add(0, 5, 0);
                    manager.spawnBoss(spawnLoc, nearbyPlayers);

                    // Final effects
                    world.playSound(spawnLoc, Sound.ENTITY_WITHER_SPAWN, 3.0f, 1.0f);
                    world.strikeLightningEffect(spawnLoc);

                    cancel();
                    return;
                }

                tick++;
            }

            private void cleanup() {
                for (ArmorStand stand : floatingSkullStands) {
                    if (stand.isValid()) stand.remove();
                }
                floatingSkullStands.clear();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
