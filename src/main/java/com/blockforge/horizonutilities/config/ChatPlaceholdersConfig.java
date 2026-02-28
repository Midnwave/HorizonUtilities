package com.blockforge.horizonutilities.config;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ChatPlaceholdersConfig {

    private final HorizonUtilitiesPlugin plugin;
    private YamlConfiguration config;
    private boolean enabled;

    public ChatPlaceholdersConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean formatChat;
    private String chatFormat;

    public void load() {
        File file = new File(plugin.getDataFolder(), "chat-placeholders.yml");
        if (!file.exists()) plugin.saveResource("chat-placeholders.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("enabled", true);
        formatChat = config.getBoolean("format-chat", false);
        chatFormat = config.getString("chat-format", "<prefix><displayname><gray>: </gray><message>");
        mentionsEnabled = config.getBoolean("mentions.enabled", true);
        mentionSound = config.getString("mentions.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        mentionHighlightColor = config.getString("mentions.highlight-color", "gold");
    }

    private boolean mentionsEnabled;
    private String mentionSound;
    private String mentionHighlightColor;

    public boolean isEnabled() { return enabled; }
    public boolean isFormatChat() { return formatChat; }
    public String getChatFormat() { return chatFormat; }
    public boolean isMentionsEnabled() { return mentionsEnabled; }
    public String getMentionSound() { return mentionSound; }
    public String getMentionHighlightColor() { return mentionHighlightColor; }

    public boolean isPlaceholderEnabled(String name) {
        return config.getBoolean("placeholders." + name + ".enabled", true);
    }

    public String getToken(String name) {
        return config.getString("placeholders." + name + ".token", "<" + name + ">");
    }

    public String getFormat(String name) {
        return config.getString("placeholders." + name + ".format", "");
    }
}
