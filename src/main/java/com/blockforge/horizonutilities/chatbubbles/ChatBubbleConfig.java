package com.blockforge.horizonutilities.chatbubbles;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ChatBubbleConfig {

    private final HorizonUtilitiesPlugin plugin;

    private boolean enabled;
    private int durationSeconds;
    private double heightOffset;
    private int maxMessageLength;
    private int backgroundOpacity;

    public ChatBubbleConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "chat-bubbles.yml");
        if (!file.exists()) {
            plugin.saveResource("chat-bubbles.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        enabled           = cfg.getBoolean("enabled", true);
        durationSeconds   = cfg.getInt("duration-seconds", 5);
        heightOffset      = cfg.getDouble("height-offset", 0.5);
        maxMessageLength  = cfg.getInt("max-message-length", 50);
        backgroundOpacity = cfg.getInt("background-opacity", 180);
    }

    public boolean isEnabled()        { return enabled; }
    public int getDurationSeconds()   { return durationSeconds; }
    public double getHeightOffset()   { return heightOffset; }
    public int getMaxMessageLength()  { return maxMessageLength; }
    public int getBackgroundOpacity() { return backgroundOpacity; }
}
