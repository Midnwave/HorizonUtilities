package com.blockforge.horizonutilities.chatbubbles;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chat bubble TextDisplay entities spawned above players when they chat.
 * Players can toggle their own bubbles on/off. Preferences persist in the database.
 */
public class ChatBubbleManager {

    private final HorizonUtilitiesPlugin plugin;
    private final ChatBubbleConfig config;

    /** Active TextDisplay entity per player. Old bubble is removed when a new one spawns. */
    private final Map<UUID, TextDisplay> activeBubbles = new ConcurrentHashMap<>();
    /** Scheduled removal tasks per player. */
    private final Map<UUID, BukkitTask> removeTasks = new ConcurrentHashMap<>();
    /** Cached preference: true = bubbles enabled for this player. */
    private final Map<UUID, Boolean> preferences = new ConcurrentHashMap<>();

    public ChatBubbleManager(HorizonUtilitiesPlugin plugin, ChatBubbleConfig config) {
        this.plugin = plugin;
        this.config = config;
        initTable();
    }

    private void initTable() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS chatbubble_preferences " +
                     "(player_uuid TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 1)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[ChatBubbles] Failed to create table: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Preference management
    // -------------------------------------------------------------------------

    /** Loads the player's bubble preference from DB into the cache. */
    public void loadPreference(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT enabled FROM chatbubble_preferences WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        preferences.put(uuid, rs.getInt("enabled") == 1);
                    } else {
                        preferences.put(uuid, true); // default: enabled
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[ChatBubbles] Failed to load preference: " + e.getMessage());
            }
        });
    }

    /** Removes the player's preference from the cache on logout. */
    public void unloadPreference(UUID uuid) {
        preferences.remove(uuid);
    }

    /** Returns whether the player has chat bubbles enabled. */
    public boolean isEnabled(UUID uuid) {
        return preferences.getOrDefault(uuid, true);
    }

    /**
     * Toggles the player's chat bubble preference.
     *
     * @return true if bubbles are now enabled, false if disabled
     */
    public boolean toggle(Player player) {
        boolean newValue = !isEnabled(player.getUniqueId());
        preferences.put(player.getUniqueId(), newValue);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO chatbubble_preferences(player_uuid, enabled) VALUES(?,?) " +
                         "ON CONFLICT(player_uuid) DO UPDATE SET enabled=excluded.enabled")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setInt(2, newValue ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[ChatBubbles] Failed to save preference: " + e.getMessage());
            }
        });
        return newValue;
    }

    // -------------------------------------------------------------------------
    // Bubble spawning
    // -------------------------------------------------------------------------

    /**
     * Spawns a chat bubble above the player's head. Must be called on the main thread.
     */
    public void spawnBubble(Player player, Component message) {
        if (!config.isEnabled()) return;
        if (!isEnabled(player.getUniqueId())) return;

        // Remove any existing bubble for this player
        removeBubble(player.getUniqueId());

        // Truncate message if needed
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        Component display = message;
        if (plain.length() > config.getMaxMessageLength()) {
            display = Component.text(plain.substring(0, config.getMaxMessageLength()) + "...");
        }
        final Component finalDisplay = display;

        // Spawn location: above the player's head
        Location loc = player.getLocation().add(0,
                player.getEyeHeight() + config.getHeightOffset(), 0);

        TextDisplay entity = player.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.text(finalDisplay);
            td.setBillboard(Display.Billboard.CENTER);
            td.setViewRange(0.4f);  // ~32 blocks
            td.setBackgroundColor(Color.fromARGB(config.getBackgroundOpacity(), 0, 0, 0));
            td.setShadowed(false);
            td.setDefaultBackground(false);
        });

        activeBubbles.put(player.getUniqueId(), entity);

        // Schedule removal
        long ticks = config.getDurationSeconds() * 20L;
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                removeBubble(player.getUniqueId()), ticks);
        removeTasks.put(player.getUniqueId(), task);
    }

    /** Removes the active bubble for the given player UUID. */
    public void removeBubble(UUID uuid) {
        BukkitTask task = removeTasks.remove(uuid);
        if (task != null) task.cancel();
        TextDisplay display = activeBubbles.remove(uuid);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }
}
