package com.blockforge.horizonutilities.auction.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.PriceHistoryManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class PriceHistoryGUI {

    private static final int SIZE = 54;
    public static final int SLOT_BACK = 45;

    // columns 1-7 are for the chart bars (7 columns for 7 days)
    // rows 0-4 (5 rows) for bar height
    private static final int CHART_START_COL = 1;
    private static final int CHART_COLS = 7;
    private static final int CHART_ROWS = 5;

    public static void open(Player player, String material) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var msg = plugin.getMessagesManager();

        List<PriceHistoryManager.PricePoint> history = plugin.getPriceHistoryManager().getHistory(material, 7);

        String displayName = material.toLowerCase().replace('_', ' ');
        Component title = msg.format("ah-gui-price-history-title", Placeholder.unparsed("item", displayName));
        Inventory inv = Bukkit.createInventory(null, SIZE, title);

        // fill with gray glass
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, bg);

        if (!history.isEmpty()) {
            // find price range for scaling
            double maxPrice = history.stream().mapToDouble(PriceHistoryManager.PricePoint::maxPrice).max().orElse(1);
            double minPrice = history.stream().mapToDouble(PriceHistoryManager.PricePoint::minPrice).min().orElse(0);
            double range = maxPrice - minPrice;
            if (range <= 0) range = 1;

            // draw bars for each day
            for (int col = 0; col < Math.min(history.size(), CHART_COLS); col++) {
                PriceHistoryManager.PricePoint point = history.get(history.size() - 1 - col);
                // reverse so latest is on the right
                int displayCol = CHART_COLS - 1 - col;

                // height based on avg price (1-5 bars)
                int height = 1 + (int) ((point.avgPrice() - minPrice) / range * (CHART_ROWS - 1));
                height = Math.max(1, Math.min(CHART_ROWS, height));

                for (int row = 0; row < CHART_ROWS; row++) {
                    int slot = (CHART_ROWS - 1 - row) * 9 + CHART_START_COL + displayCol;
                    if (row < height) {
                        Material barMat = getBarColor(height, CHART_ROWS);
                        ItemStack bar = new ItemStack(barMat);
                        var barMeta = bar.getItemMeta();
                        barMeta.displayName(Component.text(point.date(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                        barMeta.lore(List.of(
                                Component.text("Avg: ", NamedTextColor.GRAY).append(Component.text(String.format("%.2f", point.avgPrice()), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false),
                                Component.text("Min: ", NamedTextColor.GRAY).append(Component.text(String.format("%.2f", point.minPrice()), NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false),
                                Component.text("Max: ", NamedTextColor.GRAY).append(Component.text(String.format("%.2f", point.maxPrice()), NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false),
                                Component.text("Sales: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(point.saleCount()), NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false)
                        ));
                        bar.setItemMeta(barMeta);
                        inv.setItem(slot, bar);
                    }
                }
            }
        } else {
            // no data
            ItemStack noData = new ItemStack(Material.BARRIER);
            var noMeta = noData.getItemMeta();
            noMeta.displayName(Component.text("No price data", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            noData.setItemMeta(noMeta);
            inv.setItem(22, noData);
        }

        // info item
        ItemStack info = new ItemStack(Material.matchMaterial(material) != null ? Material.matchMaterial(material) : Material.PAPER);
        var infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text(displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(
                Component.text("7-day price history", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(history.size() + " day(s) of data", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(8, info);

        // back button
        ItemStack back = new ItemStack(Material.ARROW);
        var backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, back);

        player.openInventory(inv);
    }

    private static Material getBarColor(int height, int maxHeight) {
        double ratio = (double) height / maxHeight;
        if (ratio >= 0.8) return Material.RED_STAINED_GLASS_PANE;
        if (ratio >= 0.6) return Material.ORANGE_STAINED_GLASS_PANE;
        if (ratio >= 0.4) return Material.YELLOW_STAINED_GLASS_PANE;
        if (ratio >= 0.2) return Material.LIME_STAINED_GLASS_PANE;
        return Material.GREEN_STAINED_GLASS_PANE;
    }
}
