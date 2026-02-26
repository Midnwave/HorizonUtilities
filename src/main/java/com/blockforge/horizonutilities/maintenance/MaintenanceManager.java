package com.blockforge.horizonutilities.maintenance;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

public class MaintenanceManager {

    private static final Set<UUID> AUTHORIZED = Set.of(
            UUID.fromString("d56dabb6-0ae4-410f-9c46-31e3a84d5118"), // HypremeGuy
            UUID.fromString("4b3804dc-33b9-4a66-aee0-8442a6a24dcf"), // YaBoiCameronYT
            UUID.fromString("6c16714f-c90f-4104-a828-53d73f8a4e03")  // SparkedtoLife
    );

    private final HorizonUtilitiesPlugin plugin;
    private final File stateFile;
    private final Gson gson = new Gson();
    private boolean enabled;

    public MaintenanceManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "maintenance.json");
    }

    public void load() {
        if (!stateFile.exists()) {
            enabled = false;
            return;
        }
        try {
            String json = Files.readString(stateFile.toPath());
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            enabled = obj.has("enabled") && obj.get("enabled").getAsBoolean();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load maintenance state: " + e.getMessage());
            enabled = false;
        }
    }

    public void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", enabled);
            Files.writeString(stateFile.toPath(), gson.toJson(obj));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save maintenance state: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();

        if (enabled) {
            kickNonAuthorized();
        }
    }

    public boolean isAuthorized(UUID uuid) {
        return AUTHORIZED.contains(uuid);
    }

    private void kickNonAuthorized() {
        Component kickMsg = Component.text("")
                .append(Component.text("Server Maintenance", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("The server is currently under maintenance.", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Please try again later.", NamedTextColor.GRAY));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isAuthorized(player.getUniqueId())) {
                player.kick(kickMsg);
            }
        }
    }

    public Component getKickMessage() {
        return Component.text("")
                .append(Component.text("Server Maintenance", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("The server is currently under maintenance.", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Please try again later.", NamedTextColor.GRAY));
    }

    public Component getMotd() {
        return Component.text("")
                .append(Component.text("            ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠ ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("Server Maintenance", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" ⚠", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("         ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Currently under maintenance", NamedTextColor.GRAY));
    }
}
