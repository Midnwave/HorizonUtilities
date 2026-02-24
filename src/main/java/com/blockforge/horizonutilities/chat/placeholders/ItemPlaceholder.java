package com.blockforge.horizonutilities.chat.placeholders;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.chat.PlaceholderHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemPlaceholder implements PlaceholderHandler {

    private final HorizonUtilitiesPlugin plugin;

    public ItemPlaceholder(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getToken() { return "<item>"; }

    @Override
    public String getPermission() { return "horizonutilities.placeholder.item"; }

    @Override
    public Component resolve(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            return plugin.getMessagesManager().format("placeholder-empty-hand");
        }

        // show item name with full vanilla tooltip on hover (like death messages)
        return Component.text("[")
                .color(NamedTextColor.WHITE)
                .append(held.displayName().hoverEvent(held.asHoverEvent()))
                .append(Component.text("]").color(NamedTextColor.WHITE));
    }
}
