package com.blockforge.horizonutilities.chat.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.hooks.LuckPermsHook;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public ChatListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Apply internal chat placeholders (item, pos, health, etc.)
        event.message(plugin.getPlaceholderManager().process(player, event.message()));

        // LuckPerms chat formatting (only when format-chat: true)
        if (!plugin.getChatPlaceholdersConfig().isFormatChat()) return;

        LuckPermsHook lp = plugin.getLuckPermsHook();
        String rawPrefix = (lp != null && lp.isAvailable()) ? lp.getPrefix(player) : "";
        String rawSuffix = (lp != null && lp.isAvailable()) ? lp.getSuffix(player) : "";

        // Convert legacy-formatted prefix/suffix from LuckPerms to Adventure Components
        Component prefixComp = rawPrefix.isEmpty()
                ? Component.empty()
                : LEGACY.deserialize(rawPrefix);
        Component suffixComp = rawSuffix.isEmpty()
                ? Component.empty()
                : LEGACY.deserialize(rawSuffix);

        final Component finalMessage = event.message();
        event.renderer((source, displayName, message, viewer) ->
                prefixComp
                        .append(displayName)
                        .append(suffixComp)
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(finalMessage));
    }
}
