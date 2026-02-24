package com.blockforge.horizonutilities.auction;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class AuctionNotificationManager {

    private final HorizonUtilitiesPlugin plugin;

    public AuctionNotificationManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void notify(String playerUuid, String messageKey, String... placeholderPairs) {
        Player player = Bukkit.getPlayer(UUID.fromString(playerUuid));
        if (player != null && player.isOnline()) {
            TagResolver[] resolvers = buildResolvers(placeholderPairs);
            plugin.getMessagesManager().send(player, messageKey, resolvers);
            playSound(player);
        } else if (plugin.getAuctionHouseConfig().isQueueOffline()) {
            queueNotification(playerUuid, messageKey, String.join("|", placeholderPairs));
        }
    }

    public void sendQueuedNotifications(Player player) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, message_key, message_data FROM ah_notifications WHERE player_uuid = ? ORDER BY created_at ASC");
            stmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = stmt.executeQuery();

            int count = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                String key = rs.getString("message_key");
                String data = rs.getString("message_data");
                String[] pairs = data != null ? data.split("\\|") : new String[0];

                // delay each notification by 1 tick
                final TagResolver[] resolvers = buildResolvers(pairs);
                final String msgKey = key;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getMessagesManager().send(player, msgKey, resolvers);
                }, (count + 1) * 20L);

                // delete after sending
                PreparedStatement delete = conn.prepareStatement("DELETE FROM ah_notifications WHERE id = ?");
                delete.setInt(1, id);
                delete.executeUpdate();
                count++;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to send queued notifications: " + e.getMessage());
        }
    }

    private void queueNotification(String playerUuid, String messageKey, String messageData) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO ah_notifications (player_uuid, message_key, message_data, created_at) VALUES (?, ?, ?, ?)");
            stmt.setString(1, playerUuid);
            stmt.setString(2, messageKey);
            stmt.setString(3, messageData);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to queue notification: " + e.getMessage());
        }
    }

    private void playSound(Player player) {
        try {
            String soundName = plugin.getAuctionHouseConfig().getNotificationSound();
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    private TagResolver[] buildResolvers(String[] pairs) {
        if (pairs.length < 2) return new TagResolver[0];
        TagResolver[] resolvers = new TagResolver[pairs.length / 2];
        for (int i = 0; i < pairs.length - 1; i += 2) {
            resolvers[i / 2] = Placeholder.unparsed(pairs[i], pairs[i + 1]);
        }
        return resolvers;
    }
}
