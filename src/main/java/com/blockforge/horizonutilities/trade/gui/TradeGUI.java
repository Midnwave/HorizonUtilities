package com.blockforge.horizonutilities.trade.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.trade.TradeConfig;
import com.blockforge.horizonutilities.trade.TradeManager;
import com.blockforge.horizonutilities.trade.TradeSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 6-row (54 slot) split-chest GUI for trading between two players.
 *
 * Slot layout (rows 0-5, 9 columns each):
 *
 * Row 0:  [0][1][2][3]   [4]   [5][6][7][8]
 * Row 1:  [9][10][11][12] [13] [14][15][16][17]
 * Row 2: [18][19][20][21] [22] [23][24][25][26]
 * Row 3: [27][28][29][30] [31] [32][33][34][35]
 * Row 4: [36][37][38] [39][40][41] [42][43][44]   money / status row
 * Row 5: [45][46][47][48] [49] [50][51][52][53]   controls
 *
 * Divider column (slots 4,13,22,31)
 * P1 item slots: 0-3, 9-12, 18-21, 27-30  -> maps to TradeSession index 0-15
 * P2 item slots: 5-8, 14-17, 23-26, 32-35 -> maps to TradeSession index 0-15
 */
public class TradeGUI implements InventoryHolder {

    // ---- Slot constants ----

    private static final int[] P1_ITEM_SLOTS = {
        0,  1,  2,  3,
        9,  10, 11, 12,
        18, 19, 20, 21,
        27, 28, 29, 30
    };

    private static final int[] P2_ITEM_SLOTS = {
        5,  6,  7,  8,
        14, 15, 16, 17,
        23, 24, 25, 26,
        32, 33, 34, 35
    };

    private static final int[] DIVIDER_SLOTS = { 4, 13, 22, 31 };

    // Row 4 (money row)
    private static final int[] P1_MONEY_SLOTS = { 36, 37, 38 };
    private static final int   CENTER_DIVIDER  = 40;
    private static final int[] P2_MONEY_SLOTS  = { 42, 43, 44 };
    private static final int   DECO_LEFT       = 39;
    private static final int   DECO_RIGHT      = 41;

    // Row 5 (controls)
    public static final int SLOT_P1_CONFIRM      = 45;
    public static final int SLOT_P1_ADD_MONEY    = 46;
    public static final int SLOT_P1_REMOVE_MONEY = 47;
    public static final int SLOT_P1_CANCEL       = 48;
    public static final int SLOT_STATUS          = 49;
    public static final int SLOT_P2_CANCEL       = 50;
    public static final int SLOT_P2_REMOVE_MONEY = 51;
    public static final int SLOT_P2_ADD_MONEY    = 52;
    public static final int SLOT_P2_CONFIRM      = 53;

    // ---- Fields ----

    private final HorizonUtilitiesPlugin plugin;
    private final TradeManager tradeManager;
    private final TradeConfig tradeConfig;
    private final TradeSession session;
    private final Player viewingPlayer;
    private Inventory inventory;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TradeGUI(HorizonUtilitiesPlugin plugin, TradeManager tradeManager,
                    TradeConfig tradeConfig, TradeSession session, Player viewingPlayer) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
        this.tradeConfig = tradeConfig;
        this.session = session;
        this.viewingPlayer = viewingPlayer;
    }

    // -------------------------------------------------------------------------
    // InventoryHolder
    // -------------------------------------------------------------------------

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // -------------------------------------------------------------------------
    // Open / Update
    // -------------------------------------------------------------------------

    /** Create the inventory and open it for the viewing player. */
    public void open() {
        Player p1 = plugin.getServer().getPlayer(session.getPlayer1Uuid());
        Player p2 = plugin.getServer().getPlayer(session.getPlayer2Uuid());
        String p1Name = (p1 != null) ? p1.getName() : "Player1";
        String p2Name = (p2 != null) ? p2.getName() : "Player2";

        String rawTitle = tradeConfig.getGuiTitle()
                .replace("<player1>", p1Name)
                .replace("<player2>", p2Name);
        Component title = mm.deserialize(rawTitle);

        inventory = Bukkit.createInventory(this, 54, title);
        populate();
        viewingPlayer.openInventory(inventory);
    }

    /** Refresh the inventory contents to match the current session state. */
    public void update() {
        if (inventory == null) return;
        populate();
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    /**
     * Handle a click inside this GUI.
     * The event has already been cancelled by the listener before this is called.
     * This method may un-cancel it for legitimate item placement actions.
     */
    public void handleClick(int slot, Player clicker, InventoryClickEvent event) {
        UUID clickerUuid = clicker.getUniqueId();
        boolean isP1 = session.isPlayer1(clickerUuid);

        // Status indicator is purely decorative
        if (slot == SLOT_STATUS) return;

        // Confirm buttons — only the owning side
        if (slot == SLOT_P1_CONFIRM) {
            if (!isP1) return;
            handleConfirmClick(clicker);
            return;
        }
        if (slot == SLOT_P2_CONFIRM) {
            if (isP1) return;
            handleConfirmClick(clicker);
            return;
        }

        // Cancel buttons — either player
        if (slot == SLOT_P1_CANCEL || slot == SLOT_P2_CANCEL) {
            tradeManager.cancelSession(clickerUuid);
            return;
        }

        // Add money — only owning side
        if (slot == SLOT_P1_ADD_MONEY && isP1) {
            TradeGUIListener.startMoneyPrompt(clicker, session, true);
            clicker.closeInventory();
            return;
        }
        if (slot == SLOT_P2_ADD_MONEY && !isP1) {
            TradeGUIListener.startMoneyPrompt(clicker, session, true);
            clicker.closeInventory();
            return;
        }

        // Remove money — only owning side
        if (slot == SLOT_P1_REMOVE_MONEY && isP1) {
            TradeGUIListener.startMoneyPrompt(clicker, session, false);
            clicker.closeInventory();
            return;
        }
        if (slot == SLOT_P2_REMOVE_MONEY && !isP1) {
            TradeGUIListener.startMoneyPrompt(clicker, session, false);
            clicker.closeInventory();
            return;
        }

        // Silently block clicks on the wrong side's buttons
        if (slot == SLOT_P1_ADD_MONEY || slot == SLOT_P1_REMOVE_MONEY ||
            slot == SLOT_P2_ADD_MONEY || slot == SLOT_P2_REMOVE_MONEY) {
            return;
        }

        // Item slot interactions (own side only)
        if (isPlayerOwnSlot(slot, clickerUuid)) {
            handleItemSlotClick(slot, clicker, event, isP1);
        }
        // Clicks on opponent side or decorations are silently blocked (event already cancelled)
    }

    // -------------------------------------------------------------------------
    // Slot ownership
    // -------------------------------------------------------------------------

    /** Returns true if the given GUI slot belongs to the given player. */
    public boolean isPlayerOwnSlot(int slot, UUID playerUuid) {
        boolean isP1 = session.isPlayer1(playerUuid);
        if (isP1) {
            for (int s : P1_ITEM_SLOTS) if (s == slot) return true;
            return slot == SLOT_P1_CONFIRM || slot == SLOT_P1_ADD_MONEY
                    || slot == SLOT_P1_REMOVE_MONEY || slot == SLOT_P1_CANCEL;
        } else {
            for (int s : P2_ITEM_SLOTS) if (s == slot) return true;
            return slot == SLOT_P2_CONFIRM || slot == SLOT_P2_ADD_MONEY
                    || slot == SLOT_P2_REMOVE_MONEY || slot == SLOT_P2_CANCEL;
        }
    }

    // -------------------------------------------------------------------------
    // Money display item
    // -------------------------------------------------------------------------

    public ItemStack buildMoneyDisplay(double amount) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(plugin.getVaultHook().format(amount))
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("Money offered in this trade")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void populate() {
        inventory.clear();

        // Divider column (rows 0-3)
        ItemStack grayPane = makeDivider(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s : DIVIDER_SLOTS) inventory.setItem(s, grayPane);

        // Row 4 dividers / decorations
        inventory.setItem(CENTER_DIVIDER, grayPane);
        ItemStack blackPane = makeDivider(Material.BLACK_STAINED_GLASS_PANE, " ");
        inventory.setItem(DECO_LEFT,  blackPane);
        inventory.setItem(DECO_RIGHT, blackPane);

        // P1 item slots
        ItemStack[] p1Items = session.getPlayer1Items();
        for (int i = 0; i < P1_ITEM_SLOTS.length; i++) {
            inventory.setItem(P1_ITEM_SLOTS[i], p1Items[i]);
        }

        // P2 item slots
        ItemStack[] p2Items = session.getPlayer2Items();
        for (int i = 0; i < P2_ITEM_SLOTS.length; i++) {
            inventory.setItem(P2_ITEM_SLOTS[i], p2Items[i]);
        }

        // Money row
        ItemStack p1Money = buildMoneyDisplay(session.getPlayer1Money());
        for (int s : P1_MONEY_SLOTS) inventory.setItem(s, p1Money);

        ItemStack p2Money = buildMoneyDisplay(session.getPlayer2Money());
        for (int s : P2_MONEY_SLOTS) inventory.setItem(s, p2Money);

        // Control row
        inventory.setItem(SLOT_P1_CONFIRM,      buildConfirmButton(session.getPlayer1Uuid()));
        inventory.setItem(SLOT_P1_ADD_MONEY,    buildControlButton(Material.GOLD_INGOT,   "<green>Add Money",    "<yellow>Click to add money to your offer"));
        inventory.setItem(SLOT_P1_REMOVE_MONEY, buildControlButton(Material.GOLD_NUGGET,  "<red>Remove Money",   "<yellow>Click to remove money from your offer"));
        inventory.setItem(SLOT_P1_CANCEL,       buildControlButton(Material.RED_WOOL,     "<red>Cancel Trade",   "<gray>Cancel and return all items"));
        inventory.setItem(SLOT_STATUS,          buildStatusItem());
        inventory.setItem(SLOT_P2_CANCEL,       buildControlButton(Material.RED_WOOL,     "<red>Cancel Trade",   "<gray>Cancel and return all items"));
        inventory.setItem(SLOT_P2_REMOVE_MONEY, buildControlButton(Material.GOLD_NUGGET,  "<red>Remove Money",   "<yellow>Click to remove money from your offer"));
        inventory.setItem(SLOT_P2_ADD_MONEY,    buildControlButton(Material.GOLD_INGOT,   "<green>Add Money",    "<yellow>Click to add money to your offer"));
        inventory.setItem(SLOT_P2_CONFIRM,      buildConfirmButton(session.getPlayer2Uuid()));
    }

    private ItemStack buildConfirmButton(UUID playerUuid) {
        boolean confirmed = session.isConfirmedFor(playerUuid);
        Material mat  = confirmed ? Material.LIME_WOOL  : Material.GREEN_WOOL;
        String label  = confirmed ? "<green><bold>✔ CONFIRMED" : "<yellow>Click to Confirm";
        String lore   = confirmed ? "<gray>Waiting for other player..." : "<gray>Click when happy with the offer";

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize(label).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(mm.deserialize(lore).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildStatusItem() {
        TradeSession.TradeStatus status = session.getStatus();
        String label;
        String lore;
        Material mat;

        switch (status) {
            case BOTH_CONFIRMED -> {
                label = "<green><bold>✔ Both Confirmed!";
                lore  = "<yellow>Executing trade...";
                mat   = Material.LIME_STAINED_GLASS_PANE;
            }
            case PLAYER1_CONFIRMED -> {
                label = "<yellow>Waiting...";
                lore  = "<gray>Player 1 has confirmed";
                mat   = Material.YELLOW_STAINED_GLASS_PANE;
            }
            case PLAYER2_CONFIRMED -> {
                label = "<yellow>Waiting...";
                lore  = "<gray>Player 2 has confirmed";
                mat   = Material.YELLOW_STAINED_GLASS_PANE;
            }
            default -> {
                label = "<gray>Waiting for confirmations";
                lore  = "<gray>Both players must confirm";
                mat   = Material.GRAY_STAINED_GLASS_PANE;
            }
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize(label).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(mm.deserialize(lore).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildControlButton(Material mat, String label, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize(label).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(mm.deserialize(lore).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeDivider(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleConfirmClick(Player clicker) {
        boolean currentlyConfirmed = session.isConfirmedFor(clicker.getUniqueId());
        session.setConfirmed(clicker.getUniqueId(), !currentlyConfirmed);
        tradeManager.updateBothGuis(session);

        if (session.isFullyConfirmed()) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> tradeManager.completeTrade(session), 1L);
        }
    }

    private void handleItemSlotClick(int slot, Player clicker, InventoryClickEvent event, boolean isP1) {
        int[] ownSlots = isP1 ? P1_ITEM_SLOTS : P2_ITEM_SLOTS;
        int sessionIndex = -1;
        for (int i = 0; i < ownSlots.length; i++) {
            if (ownSlots[i] == slot) { sessionIndex = i; break; }
        }
        if (sessionIndex < 0) return;

        ItemStack cursor  = event.getCursor();
        ItemStack current = inventory.getItem(slot);

        if (cursor != null && cursor.getType() != Material.AIR) {
            // Player placing an item from cursor into their offer slot
            if (current != null && current.getType() != Material.AIR) {
                // Slot occupied — no swapping in trade GUI
                return;
            }
            // Check blacklist
            if (tradeConfig.isBlacklisted(cursor.getType())) {
                plugin.getMessagesManager().send(clicker, "trade-item-blacklisted");
                return;
            }
            boolean added = session.addItem(clicker.getUniqueId(), cursor);
            if (added) {
                // Clear cursor by un-cancelling and setting AIR
                event.setCancelled(false);
                clicker.setItemOnCursor(new ItemStack(Material.AIR));
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> tradeManager.updateBothGuis(session), 1L);
            }
        } else if (current != null && current.getType() != Material.AIR) {
            // Player clicking an existing item — remove it and return to inventory
            ItemStack removed = session.removeItem(clicker.getUniqueId(), sessionIndex);
            if (removed != null) {
                Map<Integer, ItemStack> leftover = clicker.getInventory().addItem(removed);
                leftover.values().forEach(stack ->
                        clicker.getWorld().dropItemNaturally(clicker.getLocation(), stack));
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> tradeManager.updateBothGuis(session), 1L);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public TradeSession getSession()        { return session; }
    public Player getViewingPlayer()        { return viewingPlayer; }
    public static int[] getP1ItemSlots()    { return P1_ITEM_SLOTS; }
    public static int[] getP2ItemSlots()    { return P2_ITEM_SLOTS; }
}
