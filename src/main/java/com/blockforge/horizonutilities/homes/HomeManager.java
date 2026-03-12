package com.blockforge.horizonutilities.homes;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {

    private final HorizonUtilitiesPlugin plugin;

    private int defaultMaxHomes;
    private int cooldownSeconds;
    private int warmupSeconds;
    private boolean allowCrossWorld;
    private int maxNameLength;
    private final Map<String, Integer> permissionMaxHomes = new LinkedHashMap<>();

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> warmupTasks = new ConcurrentHashMap<>();

    public HomeManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "homes.yml");
        if (!file.exists()) plugin.saveResource("homes.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        defaultMaxHomes = cfg.getInt("default-max-homes", 1);
        cooldownSeconds = cfg.getInt("teleport-cooldown-seconds", 5);
        warmupSeconds   = cfg.getInt("teleport-warmup-seconds", 3);
        allowCrossWorld = cfg.getBoolean("allow-cross-world", true);
        maxNameLength   = cfg.getInt("max-name-length", 16);

        permissionMaxHomes.clear();
        var maxSection = cfg.getConfigurationSection("max-homes");
        if (maxSection != null) {
            for (String key : maxSection.getKeys(false)) {
                permissionMaxHomes.put(key, maxSection.getInt(key));
            }
        }
    }

    public int getMaxHomes(Player player) {
        for (Map.Entry<String, Integer> entry : permissionMaxHomes.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                int val = entry.getValue();
                if (val < 0) return Integer.MAX_VALUE; // unlimited
                return val;
            }
        }
        return defaultMaxHomes;
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    public boolean setHome(Player player, String name) {
        if (!player.hasPermission("horizonutilities.homes.set")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return false;
        }
        String homeName = name.toLowerCase(Locale.ROOT);
        if (homeName.length() > maxNameLength) {
            player.sendMessage(Component.text("Home name too long (max " + maxNameLength + " characters).", NamedTextColor.RED));
            return false;
        }
        if (!homeName.matches("[a-z0-9_]+")) {
            player.sendMessage(Component.text("Home names can only contain letters, numbers, and underscores.", NamedTextColor.RED));
            return false;
        }

        List<Home> existing = getHomes(player.getUniqueId());
        Home existingHome = existing.stream()
                .filter(h -> h.getName().equalsIgnoreCase(homeName))
                .findFirst().orElse(null);

        if (existingHome != null) {
            // Overwrite existing home
            Location loc = player.getLocation();
            updateHome(existingHome.getId(), loc);
            player.sendMessage(Component.text("Home ", NamedTextColor.GREEN)
                    .append(Component.text(homeName, NamedTextColor.GOLD))
                    .append(Component.text(" updated!", NamedTextColor.GREEN)));
            return true;
        }

        int max = getMaxHomes(player);
        if (existing.size() >= max) {
            player.sendMessage(Component.text("You can only have " + max + " home(s). Delete one first.", NamedTextColor.RED));
            return false;
        }

        Location loc = player.getLocation();
        createHome(player.getUniqueId(), homeName, loc);
        player.sendMessage(Component.text("Home ", NamedTextColor.GREEN)
                .append(Component.text(homeName, NamedTextColor.GOLD))
                .append(Component.text(" set!", NamedTextColor.GREEN)));
        return true;
    }

    public boolean deleteHome(Player player, String name) {
        if (!player.hasPermission("horizonutilities.homes.delete")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return false;
        }
        name = name.toLowerCase(Locale.ROOT);
        Home home = getHome(player.getUniqueId(), name);
        if (home == null) {
            player.sendMessage(Component.text("You don't have a home named '" + name + "'.", NamedTextColor.RED));
            return false;
        }
        deleteHomeById(home.getId());
        player.sendMessage(Component.text("Home ", NamedTextColor.RED)
                .append(Component.text(name, NamedTextColor.GOLD))
                .append(Component.text(" deleted.", NamedTextColor.RED)));
        return true;
    }

    public void teleportToHome(Player player, String name) {
        name = name.toLowerCase(Locale.ROOT);
        Home home = getHome(player.getUniqueId(), name);
        if (home == null) {
            player.sendMessage(Component.text("You don't have a home named '" + name + "'.", NamedTextColor.RED));
            return;
        }
        teleportToHome(player, home);
    }

    public void teleportToHome(Player player, Home home) {
        if (!player.hasPermission("horizonutilities.homes.teleport")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        if (!allowCrossWorld && !player.getWorld().getName().equals(home.getWorld())) {
            player.sendMessage(Component.text("You can't teleport to homes in other worlds.", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = (cooldownSeconds * 1000L) - (now - last);
        if (remaining > 0 && !player.hasPermission("horizonutilities.homes.bypass.cooldown")) {
            player.sendMessage(Component.text("Cooldown: " + (remaining / 1000 + 1) + "s remaining.", NamedTextColor.RED));
            return;
        }

        cancelWarmup(player.getUniqueId());

        if (warmupSeconds <= 0) {
            doTeleport(player, home);
            return;
        }

        var startBlock = player.getLocation().toBlockLocation();
        player.sendMessage(Component.text("Teleporting to home ", NamedTextColor.GRAY)
                .append(Component.text(home.getName(), NamedTextColor.GOLD))
                .append(Component.text(" in " + warmupSeconds + "s. Don't move!", NamedTextColor.GRAY)));

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            warmupTasks.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (!player.getLocation().toBlockLocation().equals(startBlock)) {
                player.sendMessage(Component.text("Teleport cancelled — you moved!", NamedTextColor.RED));
                return;
            }
            doTeleport(player, home);
        }, warmupSeconds * 20L);
        warmupTasks.put(player.getUniqueId(), task);
    }

    private void doTeleport(Player player, Home home) {
        Location dest = home.toLocation();
        if (dest == null || dest.getWorld() == null) {
            player.sendMessage(Component.text("That home's world is not available.", NamedTextColor.RED));
            return;
        }
        player.teleport(dest);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(Component.text("Teleported to home ", NamedTextColor.GREEN)
                .append(Component.text(home.getName(), NamedTextColor.GOLD))
                .append(Component.text("!", NamedTextColor.GREEN)));
    }

    public void cancelWarmup(UUID uuid) {
        BukkitTask t = warmupTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    // -------------------------------------------------------------------------
    // Database operations
    // -------------------------------------------------------------------------

    private Connection conn() { return plugin.getDatabaseManager().getConnection(); }

    private void createHome(UUID owner, String name, Location loc) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    "INSERT INTO homes (player_uuid, name, world, x, y, z, yaw, pitch, created_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, owner.toString());
                ps.setString(2, name);
                ps.setString(3, loc.getWorld().getName());
                ps.setDouble(4, loc.getX());
                ps.setDouble(5, loc.getY());
                ps.setDouble(6, loc.getZ());
                ps.setFloat(7, loc.getYaw());
                ps.setFloat(8, loc.getPitch());
                ps.setLong(9, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to create home: " + e.getMessage());
            }
        });
    }

    private void updateHome(int id, Location loc) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    "UPDATE homes SET world=?, x=?, y=?, z=?, yaw=?, pitch=? WHERE id=?")) {
                ps.setString(1, loc.getWorld().getName());
                ps.setDouble(2, loc.getX());
                ps.setDouble(3, loc.getY());
                ps.setDouble(4, loc.getZ());
                ps.setFloat(5, loc.getYaw());
                ps.setFloat(6, loc.getPitch());
                ps.setInt(7, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update home: " + e.getMessage());
            }
        });
    }

    private void deleteHomeById(int id) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = conn().prepareStatement("DELETE FROM homes WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete home: " + e.getMessage());
            }
        });
    }

    public List<Home> getHomes(UUID owner) {
        List<Home> homes = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT id, player_uuid, name, world, x, y, z, yaw, pitch, created_at FROM homes WHERE player_uuid=? ORDER BY name")) {
            ps.setString(1, owner.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                homes.add(mapHome(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load homes: " + e.getMessage());
        }
        return homes;
    }

    public Home getHome(UUID owner, String name) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT id, player_uuid, name, world, x, y, z, yaw, pitch, created_at FROM homes WHERE player_uuid=? AND name=?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapHome(rs);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load home: " + e.getMessage());
        }
        return null;
    }

    private Home mapHome(ResultSet rs) throws SQLException {
        return new Home(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"),
                rs.getLong("created_at"));
    }

    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getWarmupSeconds()   { return warmupSeconds; }
}
