package com.blockforge.horizonutilities.blackmarket.listeners;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.blackmarket.BreakerItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BreakerUseListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public BreakerUseListener(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();

        if (!BreakerItemFactory.isBreakerTool(mainHand)) return;

        String brokenMaterial = event.getBlock().getType().name();
        List<String> breakableBlocks = BreakerItemFactory.getBreakableBlocks(mainHand);

        boolean canBreak = breakableBlocks.stream()
                .anyMatch(b -> b.equalsIgnoreCase(brokenMaterial));

        if (!canBreak) {
            // If this is normally unbreakable (bedrock, barrier, etc.) and the player
            // has no other means, cancel the break to prevent exploits.
            if (isNormallyUnbreakable(event.getBlock().getType())) {
                event.setCancelled(true);
            }
            // Breakable blocks that just aren't in this tool's list are allowed naturally.
            return;
        }

        // Allowed — decrement uses
        int remaining = BreakerItemFactory.decrementUses(mainHand);

        if (remaining <= 0) {
            // Remove the item from the player's hand
            event.getPlayer().getInventory().setItemInMainHand(null);
            event.getPlayer().sendMessage(
                    Component.text("Your breaker has been used up!", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
        } else {
            event.getPlayer().sendMessage(
                    MINI.deserialize("<gray>Breaker uses remaining: <white>" + remaining)
                            .decoration(TextDecoration.ITALIC, false));
        }
    }

    /**
     * Returns true for blocks that are unbreakable under normal survival conditions
     * and should not be broken by a breaker tool targeting different blocks.
     */
    private boolean isNormallyUnbreakable(Material material) {
        return switch (material) {
            case BEDROCK,
                 BARRIER,
                 COMMAND_BLOCK,
                 CHAIN_COMMAND_BLOCK,
                 REPEATING_COMMAND_BLOCK,
                 STRUCTURE_BLOCK,
                 JIGSAW,
                 END_PORTAL_FRAME,
                 END_PORTAL,
                 NETHER_PORTAL,
                 REINFORCED_DEEPSLATE -> true;
            default -> false;
        };
    }
}
