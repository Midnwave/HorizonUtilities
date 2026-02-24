package com.blockforge.horizonutilities.chat.placeholders;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.chat.PlaceholderHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class BalancePlaceholder implements PlaceholderHandler {

    private final HorizonUtilitiesPlugin plugin;

    public BalancePlaceholder(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getToken() { return "<balance>"; }

    @Override
    public String getPermission() { return "horizonutilities.placeholder.balance"; }

    @Override
    public Component resolve(Player player) {
        if (!plugin.getVaultHook().isAvailable()) {
            return Component.text("N/A").color(NamedTextColor.GRAY);
        }
        String formatted = plugin.getVaultHook().format(plugin.getVaultHook().getBalance(player));
        return Component.text(formatted).color(NamedTextColor.GREEN);
    }
}
