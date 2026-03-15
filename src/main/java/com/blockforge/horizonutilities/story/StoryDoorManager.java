package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

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

    public void saveDoorAnchor(String stageKey, String doorId, Location loc) {
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
     * Animates a door opening: blocks removed bottom-up, left then right.
     */
    private void animateDoorOpen(Location anchor, StoryConfig.DoorData doorData) {
        int width = doorData.width();
        int height = doorData.height();
        int speed = doorData.animationSpeedTicks();

        // Queue block removals: bottom-up, column by column
        List<Block> blocksToRemove = new ArrayList<>();
        World world = anchor.getWorld();
        if (world == null) return;

        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height; row++) {
                Block block = world.getBlockAt(
                    anchor.getBlockX() + col,
                    anchor.getBlockY() + row,
                    anchor.getBlockZ()
                );
                blocksToRemove.add(block);
            }
        }

        // Stagger the removal
        for (int i = 0; i < blocksToRemove.size(); i++) {
            Block block = blocksToRemove.get(i);
            long delay = (long) i * speed;

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Particle effect
                Location center = block.getLocation().add(0.5, 0.5, 0.5);
                world.spawnParticle(Particle.SOUL, center, 8, 0.3, 0.3, 0.3, 0.02);
                world.playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 0.8f, 0.6f);

                block.setType(Material.AIR);
            }, delay);
        }

        // Soul lantern flicker effect after door fully opens
        long totalDelay = (long) blocksToRemove.size() * speed + 10;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Location center = anchor.clone().add(width / 2.0, height / 2.0, 0);
            world.playSound(center, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 1.2f);
        }, totalDelay);
    }
}
