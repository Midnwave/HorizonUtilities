package com.blockforge.horizonutilities.warps.player;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the player warp system: creation, deletion, teleportation, and ratings.
 */
public class PlayerWarpManager {

    private final HorizonUtilitiesPlugin plugin;
    private final PlayerWarpStorageManager storage;

    private double creationCost;
    private int cooldownSeconds;
    private int warmupSeconds;
    private int defaultMaxWarps;
    private final Map<String, Integer> permissionMaxWarps = new LinkedHashMap<>();

    private final Map<UUID, Long> cooldowns   = new ConcurrentHashMap<>();
    private final Map<UUID, Location[]> pending = new ConcurrentHashMap<>(); // [0]=start loc
    private final Map<UUID, BukkitTask> warmupTasks = new ConcurrentHashMap<>();

    public PlayerWarpManager(HorizonUtilitiesPlugin plugin) {
        this.plugin  = plugin;
        this.storage = new PlayerWarpStorageManager(plugin);
        loadConfig();
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "player-warps.yml");
        if (!file.exists()) plugin.saveResource("player-warps.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        creationCost    = cfg.getDouble("creation-cost", 500.0);
        cooldownSeconds = cfg.getInt("teleport-cooldown-seconds", 10);
        warmupSeconds   = cfg.getInt("teleport-warmup-seconds", 3);
        defaultMaxWarps = cfg.getInt("max-warps.default", 3);

        permissionMaxWarps.clear();
        var maxSection = cfg.getConfigurationSection("max-warps");
        if (maxSection != null) {
            for (String key : maxSection.getKeys(false)) {
                if (key.equals("default")) continue;
                permissionMaxWarps.put(key, maxSection.getInt(key));
            }
        }
    }

    public int getMaxWarps(Player player) {
        for (Map.Entry<String, Integer> entry : permissionMaxWarps.entrySet()) {
            if (player.hasPermission(entry.getKey())) return entry.getValue();
        }
        return defaultMaxWarps;
    }

    // -------------------------------------------------------------------------
    // Create / delete
    // -------------------------------------------------------------------------

    public boolean createWarp(Player player, String name) {
        if (!player.hasPermission("horizonutilities.pwarp.set")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return false;
        }
        int max = getMaxWarps(player);
        int current = storage.countByOwner(player.getUniqueId());
        if (max >= 0 && current >= max) {
            player.sendMessage(Component.text("You can only have " + max + " player warp(s).", NamedTextColor.RED));
            return false;
        }
        if (creationCost > 0 && plugin.getVaultHook().isAvailable()) {
            if (!plugin.getVaultHook().has(player, creationCost)) {
                player.sendMessage(Component.text("You need " +
                        plugin.getVaultHook().format(creationCost) + " to create a warp.", NamedTextColor.RED));
                return false;
            }
            plugin.getVaultHook().withdraw(player, creationCost);
        }
        var loc = player.getLocation();
        boolean ok = storage.create(player.getUniqueId(), player.getName(), name,
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        if (ok) {
            player.sendMessage(Component.text("Warp ", NamedTextColor.GREEN)
                    .append(Component.text(name, NamedTextColor.GOLD))
                    .append(Component.text(" created.", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("A warp with that name already exists.", NamedTextColor.RED));
        }
        return ok;
    }

    public boolean deleteWarp(Player player, String name) {
        if (!player.hasPermission("horizonutilities.pwarp.delete") &&
                !player.hasPermission("horizonutilities.pwarp.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return false;
        }
        PlayerWarp warp = storage.getByOwnerAndName(player.getUniqueId(), name);
        if (warp == null && player.hasPermission("horizonutilities.pwarp.admin")) {
            // Admin can delete by owner:name syntax (handled elsewhere)
        }
        if (warp == null) {
            player.sendMessage(Component.text("You don't have a warp named '" + name + "'.", NamedTextColor.RED));
            return false;
        }
        storage.delete(warp.getId());
        player.sendMessage(Component.text("Warp ", NamedTextColor.RED)
                .append(Component.text(name, NamedTextColor.GOLD))
                .append(Component.text(" deleted.", NamedTextColor.RED)));
        return true;
    }

    // -------------------------------------------------------------------------
    // Teleport
    // -------------------------------------------------------------------------

    public void teleportToWarp(Player player, PlayerWarp warp) {
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = (cooldownSeconds * 1000L) - (now - last);
        if (remaining > 0) {
            player.sendMessage(Component.text("Cooldown: " + (remaining / 1000 + 1) + "s remaining.", NamedTextColor.RED));
            return;
        }

        cancelWarmup(player.getUniqueId());

        if (warmupSeconds <= 0) {
            doTeleport(player, warp);
            return;
        }

        var startBlock = player.getLocation().toBlockLocation();
        player.sendMessage(Component.text("Teleporting to ", NamedTextColor.GRAY)
                .append(Component.text(warp.getName(), NamedTextColor.GOLD))
                .append(Component.text(" by ", NamedTextColor.GRAY))
                .append(Component.text(warp.getOwnerName(), NamedTextColor.AQUA))
                .append(Component.text(" in " + warmupSeconds + "s. Don't move!", NamedTextColor.GRAY)));

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            warmupTasks.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (!player.getLocation().toBlockLocation().equals(startBlock)) {
                player.sendMessage(Component.text("Teleport cancelled — you moved!", NamedTextColor.RED));
                return;
            }
            doTeleport(player, warp);
        }, warmupSeconds * 20L);
        warmupTasks.put(player.getUniqueId(), task);
    }

    private void doTeleport(Player player, PlayerWarp warp) {
        var dest = warp.toLocation();
        if (dest == null || dest.getWorld() == null) {
            player.sendMessage(Component.text("That warp's world is not available.", NamedTextColor.RED));
            return;
        }
        player.teleport(dest);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(Component.text("Teleported to ", NamedTextColor.GREEN)
                .append(Component.text(warp.getName(), NamedTextColor.GOLD))
                .append(Component.text("!", NamedTextColor.GREEN)));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> storage.incrementVisits(warp.getId()));
    }

    public void cancelWarmup(UUID uuid) {
        BukkitTask t = warmupTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    // -------------------------------------------------------------------------
    // Rating
    // -------------------------------------------------------------------------

    public boolean rateWarp(Player player, PlayerWarp warp, int stars) {
        if (!player.hasPermission("horizonutilities.pwarp.rate")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return false;
        }
        if (warp.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You can't rate your own warp.", NamedTextColor.RED));
            return false;
        }
        if (stars < 1 || stars > 5) {
            player.sendMessage(Component.text("Rating must be 1-5 stars.", NamedTextColor.RED));
            return false;
        }
        boolean ok = storage.setRating(warp.getId(), player.getUniqueId(), stars);
        if (ok) {
            player.sendMessage(Component.text("Rated ", NamedTextColor.GOLD)
                    .append(Component.text(warp.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" " + stars + " star(s).", NamedTextColor.GOLD)));
        }
        return ok;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<PlayerWarp> getAllWarps() { return storage.getAll(); }
    public List<PlayerWarp> getPlayerWarps(UUID uuid) { return storage.getByOwner(uuid); }
    public PlayerWarpStorageManager getStorage() { return storage; }
}
