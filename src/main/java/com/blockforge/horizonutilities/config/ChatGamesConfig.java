package com.blockforge.horizonutilities.config;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class ChatGamesConfig {

    private final HorizonUtilitiesPlugin plugin;
    private YamlConfiguration config;

    private int intervalSeconds;
    private int varianceSeconds;
    private int timeoutSeconds;

    public ChatGamesConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "chat-games.yml");
        if (!file.exists()) plugin.saveResource("chat-games.yml", false);
        config = YamlConfiguration.loadConfiguration(file);

        intervalSeconds = config.getInt("scheduling.interval-seconds", 300);
        varianceSeconds = config.getInt("scheduling.variance-seconds", 60);
        timeoutSeconds = config.getInt("scheduling.timeout-seconds", 30);
    }

    public boolean isGameEnabled(String gameType) {
        return config.getBoolean("games." + gameType + ".enabled", true);
    }

    public double getRewardMoney(String gameType) {
        return config.getDouble("games." + gameType + ".reward-money", 100.0);
    }

    public List<String> getRewardCommands(String gameType) {
        return config.getStringList("games." + gameType + ".reward-commands");
    }

    public int getInt(String path, int def) {
        return config.getInt("games." + path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean("games." + path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList("games." + path);
    }

    public int getIntervalSeconds() { return intervalSeconds; }
    public int getVarianceSeconds() { return varianceSeconds; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
}
