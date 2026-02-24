package com.blockforge.horizonutilities.chat;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.chat.placeholders.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PlaceholderManager {

    private final HorizonUtilitiesPlugin plugin;
    private final List<PlaceholderHandler> handlers = new ArrayList<>();

    public PlaceholderManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    private void registerDefaults() {
        handlers.add(new ItemPlaceholder(plugin));
        handlers.add(new PosPlaceholder(plugin));
        handlers.add(new HealthPlaceholder(plugin));
        handlers.add(new BalancePlaceholder(plugin));
        handlers.add(new PingPlaceholder(plugin));
    }

    public Component process(Player player, Component message) {
        if (!plugin.getChatPlaceholdersConfig().isEnabled()) return message;

        Component result = message;
        for (PlaceholderHandler handler : handlers) {
            if (!plugin.getChatPlaceholdersConfig().isPlaceholderEnabled(
                    handler.getToken().replace("<", "").replace(">", ""))) continue;
            if (!player.hasPermission(handler.getPermission())) continue;

            Component replacement = handler.resolve(player);
            result = result.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(handler.getToken())
                    .replacement(replacement)
                    .build());
        }
        return result;
    }
}
