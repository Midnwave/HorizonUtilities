package com.blockforge.horizonutilities.chat.placeholders;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.chat.PlaceholderHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class PingPlaceholder implements PlaceholderHandler {

    private final HorizonUtilitiesPlugin plugin;

    public PingPlaceholder(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getToken() { return "<ping>"; }

    @Override
    public String getPermission() { return "horizonutilities.placeholder.ping"; }

    @Override
    public Component resolve(Player player) {
        int ping = player.getPing();
        NamedTextColor color = ping < 50 ? NamedTextColor.GREEN
                : ping < 150 ? NamedTextColor.YELLOW
                : NamedTextColor.RED;
        return Component.text(ping + "ms").color(color);
    }
}
