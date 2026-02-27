package com.blockforge.horizonutilities.warps.admin;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages admin warps and the server spawn point. Warps are stored in admin-warps.yml.
 * Teleportation uses a warmup (cancel on move) and a per-player cooldown.
 */
public class AdminWarpManager {

    private final HorizonUtilitiesPlugin plugin;
    private File configFile;
    private FileConfiguration cfg;

    /** warp name (lowercase) -> Location */
    private final Map<String, Location> warps = new LinkedHashMap<>();
    private Location spawnLocation;

    /** UUID -> block position at warp request time (for move-cancel) */
    private final Map<UUID, Location> pendingWarps = new ConcurrentHashMap<>();
    /** UUID -> scheduled teleport task */
    private final Map<UUID, BukkitTask> warmupTasks = new ConcurrentHashMap<>();
    /** UUID -> timestamp of last successful teleport */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private int warmupSeconds;
    private int cooldownSeconds;

    public AdminWarpManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // -------------------------------------------------------------------------
    // Load / save
    // -------------------------------------------------------------------------

    public void load() {
        configFile = new File(plugin.getDataFolder(), "admin-warps.yml");
        if (!configFile.exists()) {
            plugin.saveResource("admin-warps.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(configFile);

        warmupSeconds  = cfg.getInt("teleport-warmup-seconds", 3);
        cooldownSeconds = cfg.getInt("teleport-cooldown-seconds", 5);

        // Load spawn
        String worldName = cfg.getString("spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) world = Bukkit.getWorlds().get(0);
        spawnLocation = new Location(world,
                cfg.getDouble("spawn.x", 0),
                cfg.getDouble("spawn.y", 64),
                cfg.getDouble("spawn.z", 0),
                (float) cfg.getDouble("spawn.yaw", 0),
                (float) cfg.getDouble("spawn.pitch", 0));

        // Load warps
        warps.clear();
        var warpsSection = cfg.getConfigurationSection("warps");
        if (warpsSection != null) {
            for (String name : warpsSection.getKeys(false)) {
                var sec = warpsSection.getConfigurationSection(name);
                if (sec == null) continue;
                String wWorldName = sec.getString("world", "world");
                World wWorld = Bukkit.getWorld(wWorldName);
                if (wWorld == null) continue;
                warps.put(name.toLowerCase(Locale.ROOT), new Location(wWorld,
                        sec.getDouble("x"),
                        sec.getDouble("y"),
                        sec.getDouble("z"),
                        (float) sec.getDouble("yaw", 0),
                        (float) sec.getDouble("pitch", 0)));
            }
        }
    }

    private void save() {
        cfg.set("teleport-warmup-seconds", warmupSeconds);
        cfg.set("teleport-cooldown-seconds", cooldownSeconds);
        cfg.set("spawn.world", spawnLocation.getWorld().getName());
        cfg.set("spawn.x", spawnLocation.getX());
        cfg.set("spawn.y", spawnLocation.getY());
        cfg.set("spawn.z", spawnLocation.getZ());
        cfg.set("spawn.yaw", spawnLocation.getYaw());
        cfg.set("spawn.pitch", spawnLocation.getPitch());

        cfg.set("warps", null); // clear
        for (Map.Entry<String, Location> entry : warps.entrySet()) {
            String key = "warps." + entry.getKey();
            Location loc = entry.getValue();
            cfg.set(key + ".world", loc.getWorld().getName());
            cfg.set(key + ".x", loc.getX());
            cfg.set(key + ".y", loc.getY());
            cfg.set(key + ".z", loc.getZ());
            cfg.set(key + ".yaw", loc.getYaw());
            cfg.set(key + ".pitch", loc.getPitch());
        }
        try {
            cfg.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[AdminWarps] Failed to save: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Warp management
    // -------------------------------------------------------------------------

    public boolean setWarp(String name, Location location) {
        warps.put(name.toLowerCase(Locale.ROOT), location.clone());
        save();
        return true;
    }

    public boolean deleteWarp(String name) {
        if (warps.remove(name.toLowerCase(Locale.ROOT)) == null) return false;
        save();
        return true;
    }

    public Location getWarp(String name) {
        return warps.get(name.toLowerCase(Locale.ROOT));
    }

    public Set<String> getWarpNames() {
        return Collections.unmodifiableSet(warps.keySet());
    }

    public void setSpawn(Location location) {
        this.spawnLocation = location.clone();
        save();
    }

    public Location getSpawn() {
        return spawnLocation.clone();
    }

    // -------------------------------------------------------------------------
    // Teleportation
    // -------------------------------------------------------------------------

    /** Initiates a warmup-based teleport to the given destination. */
    public void teleport(Player player, Location destination, String label) {
        // Cooldown check
        long now = System.currentTimeMillis();
        long lastTp = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = (cooldownSeconds * 1000L) - (now - lastTp);
        if (remaining > 0) {
            player.sendMessage(Component.text("You must wait " + (remaining / 1000 + 1) +
                    "s before teleporting again.", NamedTextColor.RED));
            return;
        }

        // Cancel any existing warmup
        cancelWarmup(player.getUniqueId());

        if (warmupSeconds <= 0) {
            executeTeleport(player, destination);
            return;
        }

        Location startBlock = player.getLocation().toBlockLocation();
        pendingWarps.put(player.getUniqueId(), startBlock);

        player.sendMessage(Component.text("Teleporting to ", NamedTextColor.GRAY)
                .append(Component.text(label, NamedTextColor.GOLD))
                .append(Component.text(" in " + warmupSeconds + "s. Don't move!", NamedTextColor.GRAY)));

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingWarps.remove(player.getUniqueId());
            warmupTasks.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            // Check if player moved
            if (!player.getLocation().toBlockLocation().equals(startBlock)) {
                player.sendMessage(Component.text("Teleport cancelled — you moved!", NamedTextColor.RED));
                return;
            }
            executeTeleport(player, destination);
        }, warmupSeconds * 20L);

        warmupTasks.put(player.getUniqueId(), task);
    }

    private void executeTeleport(Player player, Location destination) {
        player.teleport(destination);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(Component.text("Teleported!", NamedTextColor.GREEN));
    }

    public void cancelWarmup(UUID uuid) {
        pendingWarps.remove(uuid);
        BukkitTask task = warmupTasks.remove(uuid);
        if (task != null) task.cancel();
    }
}
