package com.blockforge.horizonutilities.games.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.games.LeaderboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class LeaderboardGUI {

    private final HorizonUtilitiesPlugin plugin;
    private final Player player;

    public LeaderboardGUI(HorizonUtilitiesPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(null, 54,
                plugin.getMessagesManager().format("leaderboard-gui-title"));

        List<LeaderboardManager.LeaderboardEntry> top = plugin.getLeaderboardManager().getTop(28);

        for (int i = 0; i < top.size() && i < 28; i++) {
            var entry = top.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.displayName(Component.text("#" + (i + 1) + " " + entry.playerName(), NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("Wins: " + entry.wins(), NamedTextColor.GRAY),
                    Component.text("Streak: " + entry.currentStreak(), NamedTextColor.GRAY),
                    Component.text("Best Streak: " + entry.bestStreak(), NamedTextColor.GRAY),
                    Component.text("Fastest: " + entry.fastestTimeMs() + "ms", NamedTextColor.GRAY)
            ));
            head.setItemMeta(meta);

            // fill slots 10-37 (skipping borders)
            int slot = getSlot(i);
            if (slot >= 0) inv.setItem(slot, head);
        }

        player.openInventory(inv);
    }

    private int getSlot(int index) {
        // map index to inner slots of a 6-row chest (skip border)
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        return index < slots.length ? slots[index] : -1;
    }
}
