package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages sealed doors in story structures.
 * Doors are 2-wide x 3-tall block walls that slide open when the required key item is used.
 * Door anchors (bottom-left corner) are saved to story-doors.yml.
 */
public class StoryDoorManager {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryConfig config;
    private final StoryItemManager itemManager;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    // stage:doorId -> anchor location
    private final Map<String, Location> doorAnchors = new LinkedHashMap<>();
    private final Set<String> openedDoors = new HashSet<>();

    private File doorsFile;
    private FileConfiguration doorsConfig;

    public StoryDoorManager(HorizonUtilitiesPlugin plugin, StoryConfig config, StoryItemManager itemManager) {
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        loadDoors();
    }

    private void loadDoors() {
        doorsFile = new File(plugin.getDataFolder(), "story-doors.yml");
        if (!doorsFile.exists()) {
            try { doorsFile.createNewFile(); } catch (IOException ignored) {}
        }
        doorsConfig = YamlConfiguration.loadConfiguration(doorsFile);

        doorAnchors.clear();
        var section = doorsConfig.getConfigurationSection("doors");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                var ds = section.getConfigurationSection(key);
                if (ds != null) {
                    World world = Bukkit.getWorld(ds.getString("world", "world"));
                    if (world != null) {
                        doorAnchors.put(key, new Location(world,
                            ds.getDouble("x"), ds.getDouble("y"), ds.getDouble("z")));
                    }
                }
            }
        }
        plugin.getLogger().info("[Story] Loaded " + doorAnchors.size() + " door anchor(s).");
    }

    /**
     * Normalizes stage key — accepts "1" or "stage-1" format.
     */
    private String normalizeStageKey(String input) {
        if (!input.startsWith("stage-")) {
            return "stage-" + input;
        }
        return input;
    }

    public void saveDoorAnchor(String stageKey, String doorId, Location loc) {
        stageKey = normalizeStageKey(stageKey);
        String key = stageKey + ":" + doorId;
        doorAnchors.put(key, loc.clone());

        String path = "doors." + key.replace(":", "_");
        doorsConfig.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
        doorsConfig.set(path + ".x", loc.getX());
        doorsConfig.set(path + ".y", loc.getY());
        doorsConfig.set(path + ".z", loc.getZ());

        try {
            doorsConfig.save(doorsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Story] Failed to save doors: " + e.getMessage());
        }
    }

    /**
     * Attempts to open a door for a player.
     * Checks if the player has the required key item.
     * Returns true if the door was opened (or is already open).
     */
    public boolean tryOpenDoor(Player player, String stageKey, String doorId) {
        stageKey = normalizeStageKey(stageKey);
        String key = stageKey + ":" + doorId;

        if (openedDoors.contains(key + ":" + player.getUniqueId())) {
            return true; // Already opened for this player
        }

        StoryConfig.DoorData doorData = config.getDoor(stageKey, doorId);
        if (doorData == null) return false;

        Location anchor = doorAnchors.get(key);
        if (anchor == null) return false;

        // Check for required item
        String requiredItem = doorData.requiredItem();
        if (requiredItem != null && !requiredItem.isEmpty()) {
            if (!itemManager.isItem(player.getInventory().getItemInMainHand(), requiredItem)) {
                // Push player back gently
                Vector pushBack = player.getLocation().getDirection().multiply(-0.5);
                pushBack.setY(0.2);
                player.setVelocity(pushBack);
                player.sendMessage(MINI.deserialize(doorData.denyMessage()));
                return false;
            }

            // Consume key
            var mainHand = player.getInventory().getItemInMainHand();
            mainHand.setAmount(mainHand.getAmount() - 1);
        }

        // Open the door with animation
        openedDoors.add(key + ":" + player.getUniqueId());
        animateDoorOpen(anchor, doorData);
        player.sendMessage(MINI.deserialize(doorData.openMessage()));
        return true;
    }

    /**
     * Force-opens a door without requiring a key (admin command).
     * Returns true if the door was found and opened.
     */
    public boolean forceOpen(String stageKey, String doorId) {
        stageKey = normalizeStageKey(stageKey);
        String key = stageKey + ":" + doorId;

        StoryConfig.DoorData doorData = config.getDoor(stageKey, doorId);
        if (doorData == null) return false;

        Location anchor = doorAnchors.get(key);
        if (anchor == null) return false;

        animateDoorOpen(anchor, doorData);
        return true;
    }

    /**
     * Returns all door keys ("stage:doorId") that have anchors set.
     */
    public Set<String> getDoorKeys() {
        return Collections.unmodifiableSet(doorAnchors.keySet());
    }

    /**
     * Animates a door opening using BlockDisplay entities.
     * Blocks are replaced with barriers, BlockDisplays slide apart smoothly
     * with stone grinding sounds, then barriers are cleared.
     */
    private void animateDoorOpen(Location anchor, StoryConfig.DoorData doorData) {
        int width = doorData.width();
        int height = doorData.height();
        World world = anchor.getWorld();
        if (world == null) return;

        record DoorBlock(Block block, BlockDisplay display, int col, int row) {}
        List<DoorBlock> doorBlocks = new ArrayList<>();
        Quaternionf identity = new Quaternionf();

        // Phase 1: Replace each block with barrier + spawn BlockDisplay in its place
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height; row++) {
                Block block = world.getBlockAt(
                    anchor.getBlockX() + col,
                    anchor.getBlockY() + row,
                    anchor.getBlockZ()
                );
                if (block.getType().isAir() || block.getType() == Material.BARRIER) continue;

                BlockData blockData = block.getBlockData().clone();
                block.setType(Material.BARRIER);

                BlockDisplay display = world.spawn(block.getLocation(), BlockDisplay.class, d -> {
                    d.setBlock(blockData);
                    d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0), identity,
                        new Vector3f(1, 1, 1), identity
                    ));
                });

                doorBlocks.add(new DoorBlock(block, display, col, row));
            }
        }

        if (doorBlocks.isEmpty()) return;

        Location center = anchor.clone().add(width / 2.0, height / 2.0, 0);
        int slideDuration = 60; // 3 seconds of smooth sliding

        // Phase 2: Initial rumble — the door shudders before moving
        world.playSound(center, Sound.BLOCK_STONE_HIT, 1.2f, 0.3f);
        world.playSound(center, Sound.BLOCK_PISTON_EXTEND, 0.7f, 0.5f);
        world.spawnParticle(Particle.DUST, center, 15, 0.8, 1.0, 0.3, 0,
            new Particle.DustOptions(Color.fromRGB(80, 80, 80), 1.5f));

        // Phase 3: After a short pause, start the slide
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (DoorBlock db : doorBlocks) {
                // Bottom rows start first, staggered upward
                int rowDelay = (height - 1 - db.row) * 3;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Left half slides left, right half slides right
                    boolean slideLeft = db.col < width / 2.0;
                    float slideX = slideLeft ? -(width + 0.5f) : (width + 0.5f);

                    db.display.setInterpolationDelay(0);
                    db.display.setInterpolationDuration(slideDuration);
                    db.display.setTransformation(new Transformation(
                        new Vector3f(slideX, -0.05f, 0), identity,
                        new Vector3f(1, 1, 1), identity
                    ));
                }, rowDelay);
            }
        }, 12L);

        // Phase 4: Stone grinding sounds + dust particles during slide
        BukkitTask grindTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            world.playSound(center, Sound.BLOCK_GRINDSTONE_USE, 0.5f, 0.3f);
            world.playSound(center, Sound.BLOCK_STONE_STEP, 0.4f, 0.4f);
            world.spawnParticle(Particle.DUST, center, 6, 0.6, 0.8, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(100, 100, 100), 1.0f));
        }, 14L, 10L);

        // Phase 5: Cleanup — remove displays and barriers, final thud
        long cleanupDelay = 12L + slideDuration + 10L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            grindTask.cancel();

            for (DoorBlock db : doorBlocks) {
                db.display.remove();
                db.block.setType(Material.AIR);
            }

            // Heavy stone thud + soul effect
            world.playSound(center, Sound.BLOCK_STONE_PLACE, 1.2f, 0.4f);
            world.playSound(center, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 1.2f);
            world.spawnParticle(Particle.SOUL, center, 25, 1.0, 1.5, 0.5, 0.02);
        }, cleanupDelay);
    }
}
