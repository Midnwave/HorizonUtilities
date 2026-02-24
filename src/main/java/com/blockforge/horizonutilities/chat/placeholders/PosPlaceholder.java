package com.blockforge.horizonutilities.chat.placeholders;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.chat.PlaceholderHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PosPlaceholder implements PlaceholderHandler {

    private final HorizonUtilitiesPlugin plugin;

    public PosPlaceholder(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getToken() { return "<pos>"; }

    @Override
    public String getPermission() { return "horizonutilities.placeholder.pos"; }

    @Override
    public Component resolve(Player player) {
        Location loc = player.getLocation();
        return Component.text(loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ())
                .color(NamedTextColor.GOLD);
    }
}
