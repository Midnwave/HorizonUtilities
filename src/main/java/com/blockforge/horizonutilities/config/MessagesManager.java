package com.blockforge.horizonutilities.config;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessagesManager {

    private final HorizonUtilitiesPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;

    public MessagesManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String getRaw(String key) {
        return messages.getString(key, "<red>Missing message: " + key);
    }

    public Component format(String key, TagResolver... resolvers) {
        String raw = getRaw(key);
        String prefix = messages.getString("prefix", "");
        raw = raw.replace("<prefix>", prefix);
        return miniMessage.deserialize(raw, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(format(key, resolvers));
    }

    public MiniMessage getMiniMessage() { return miniMessage; }
}
