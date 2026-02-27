package com.blockforge.horizonutilities.warps.player.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.warps.player.PlayerWarp;
import com.blockforge.horizonutilities.warps.player.PlayerWarpManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerWarpGUIListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final PlayerWarpManager manager;

    /** Tracks current page per viewer UUID. */
    private final Map<UUID, Integer> pages = new ConcurrentHashMap<>();

    public PlayerWarpGUIListener(HorizonUtilitiesPlugin plugin, PlayerWarpManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    /** Opens the warp list GUI for a player at page 0. */
    public void open(Player player) {
        pages.put(player.getUniqueId(), 0);
        List<PlayerWarp> warps = manager.getAllWarps();
        player.openInventory(PlayerWarpGUI.build(warps, 0));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().title() == null) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.startsWith(PlayerWarpGUI.TITLE_PREFIX)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        int page = pages.getOrDefault(player.getUniqueId(), 0);
        List<PlayerWarp> warps = manager.getAllWarps();
        int totalPages = Math.max(1, (int) Math.ceil((double) warps.size() / 45));

        // Navigation slots
        if (slot == 45 && page > 0) {
            page--;
            pages.put(player.getUniqueId(), page);
            player.openInventory(PlayerWarpGUI.build(warps, page));
            return;
        }
        if (slot == 53 && page < totalPages - 1) {
            page++;
            pages.put(player.getUniqueId(), page);
            player.openInventory(PlayerWarpGUI.build(warps, page));
            return;
        }

        // Warp slots (0-44)
        if (slot < 45) {
            int index = page * 45 + slot;
            if (index >= warps.size()) return;
            PlayerWarp warp = warps.get(index);
            player.closeInventory();
            manager.teleportToWarp(player, warp);
        }
    }
}
