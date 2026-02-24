package com.blockforge.horizonutilities.auction.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auction.AuctionTransaction;
import com.blockforge.horizonutilities.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AuctionHistoryGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_NEXT = 50;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault());

    private static final Map<UUID, HistoryState> states = new HashMap<>();

    public static HistoryState getState(UUID uuid) { return states.get(uuid); }
    public static void removeState(UUID uuid) { states.remove(uuid); }

    public static void open(Player player, int page) {
        var plugin = HorizonUtilitiesPlugin.getInstance();
        var msg = plugin.getMessagesManager();
        var am = plugin.getAuctionManager();
        var vault = plugin.getVaultHook();

        String uuid = player.getUniqueId().toString();
        List<AuctionTransaction> transactions = am.getTransactions(uuid, page, PER_PAGE);
        int totalCount = am.countTransactions(uuid);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PER_PAGE));

        Component title = msg.format("ah-gui-history-title");
        Inventory inv = Bukkit.createInventory(null, SIZE, title);

        for (int i = 0; i < transactions.size() && i < PER_PAGE; i++) {
            AuctionTransaction tx = transactions.get(i);
            inv.setItem(i, buildTransactionItem(plugin, tx, player));
        }

        // bottom row
        ItemStack back = new ItemStack(Material.ARROW);
        var backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, back);

        if (page > 0) {
            inv.setItem(SLOT_PREV, makeArrow(msg.format("ah-gui-prev-page")));
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeArrow(msg.format("ah-gui-next-page")));
        }

        states.put(player.getUniqueId(), new HistoryState(page));
        player.openInventory(inv);
    }

    private static ItemStack buildTransactionItem(HorizonUtilitiesPlugin plugin, AuctionTransaction tx, Player viewer) {
        ItemStack display = tx.item() != null ? tx.item().clone() : new ItemStack(Material.BARRIER);
        var meta = display.getItemMeta();
        var vault = plugin.getVaultHook();

        boolean isSeller = tx.sellerUuid().equals(viewer.getUniqueId().toString());
        String role = isSeller ? "Sold" : "Bought";
        NamedTextColor roleColor = isSeller ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        String date = DATE_FORMAT.format(Instant.ofEpochMilli(tx.completedAt()));

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text(role, roleColor).decoration(TextDecoration.ITALIC, false));
        lore.add(infoLine("Price: ", vault.format(tx.salePrice())));
        lore.add(infoLine("Type: ", tx.saleType()));
        if (tx.taxAmount() > 0) lore.add(infoLine("Tax: ", vault.format(tx.taxAmount())));
        lore.add(infoLine("Date: ", date));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static Component infoLine(String label, String value) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack makeArrow(Component name) {
        ItemStack item = new ItemStack(Material.ARROW);
        var meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public record HistoryState(int page) {}
}
