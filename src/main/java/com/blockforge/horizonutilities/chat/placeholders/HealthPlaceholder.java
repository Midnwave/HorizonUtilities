package com.blockforge.horizonutilities.chat.placeholders;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.chat.PlaceholderHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class HealthPlaceholder implements PlaceholderHandler {

    private final HorizonUtilitiesPlugin plugin;

    public HealthPlaceholder(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getToken() { return "<health>"; }

    @Override
    public String getPermission() { return "horizonutilities.placeholder.health"; }

    @Override
    public Component resolve(Player player) {
        double current = player.getHealth();
        double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        return Component.text(String.format("\u2764 %.1f/%.1f", current, max))
                .color(NamedTextColor.RED);
    }
}
