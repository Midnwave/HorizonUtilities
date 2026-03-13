package com.blockforge.horizonutilities.chat;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.chat.placeholders.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

        // Serialize to plain text to detect tokens — Paper 1.21's signed messages
        // may fragment <token> across child Component nodes, so matchLiteral fails
        // on the original tree. Flatten to a single TextComponent first.
        String plain = PlainTextComponentSerializer.plainText().serialize(message);

        boolean hasAny = false;
        for (PlaceholderHandler handler : handlers) {
            if (plain.contains(handler.getToken())) { hasAny = true; break; }
        }
        if (!hasAny) return message;

        // Rebuild as flat text so replaceText can find tokens as contiguous strings
        Component result = Component.text(plain);
        for (PlaceholderHandler handler : handlers) {
            String tokenKey = handler.getToken().replace("<", "").replace(">", "");
            if (!plugin.getChatPlaceholdersConfig().isPlaceholderEnabled(tokenKey)) continue;
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
