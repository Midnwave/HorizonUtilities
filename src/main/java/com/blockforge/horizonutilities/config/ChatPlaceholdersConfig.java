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

    public void load() {
        File file = new File(plugin.getDataFolder(), "chat-placeholders.yml");
        if (!file.exists()) plugin.saveResource("chat-placeholders.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("enabled", true);
    }

    public boolean isEnabled() { return enabled; }

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
