package com.blockforge.horizonutilities.trade.gui;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.trade.TradeManager;
import com.blockforge.horizonutilities.trade.TradeSession;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all inventory and chat events for the Trade GUI.
 */
public class TradeGUIListener implements Listener {

    // ---- Pending money input ----

    /**
     * Tracks players who are in the middle of typing a money amount into chat.
     * true  = adding money (set)
     * false = removing money (set to 0)
     */
    private static final Map<UUID, PendingMoneyInput> PENDING_MONEY_INPUTS = new HashMap<>();

    public record PendingMoneyInput(TradeSession session, boolean adding) {}

    /**
     * Called from TradeGUI to start a money input prompt for a player.
     * The player's inventory is closed by TradeGUI before this is called.
     */
    public static void startMoneyPrompt(Player player, TradeSession session, boolean adding) {
        PENDING_MONEY_INPUTS.put(player.getUniqueId(), new PendingMoneyInput(session, adding));
        MiniMessage mm = MiniMessage.miniMessage();
        if (adding) {
            player.sendMessage(mm.deserialize("<gold>Enter the amount of money to add to your offer, or type <red>cancel</red>:"));
        } else {
            player.sendMessage(mm.deserialize("<gold>Enter the amount of money to remove from your offer, or type <red>cancel</red>:"));
        }
    }

    // ---- Fields ----

    private final HorizonUtilitiesPlugin plugin;
    private final TradeManager tradeManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TradeGUIListener(HorizonUtilitiesPlugin plugin, TradeManager tradeManager) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
    }

    // -------------------------------------------------------------------------
    // Inventory click
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof TradeGUI gui)) return;

        // Always cancel by default — the GUI handler decides if it should be un-cancelled
        event.setCancelled(true);

        // Clicks in the player's bottom inventory (their own inv) are blocked while trade is open
        if (event.getClickedInventory() == event.getView().getBottomInventory()) return;

        // Only handle clicks in the top (trade) inventory
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        gui.handleClick(slot, player, event);
    }

    // -------------------------------------------------------------------------
    // Inventory drag
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof TradeGUI)) return;
        // Cancel all drags in the trade GUI
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Inventory close
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof TradeGUI gui)) return;

        TradeSession session = gui.getSession();
        if (session.getStatus() == TradeSession.TradeStatus.COMPLETED
                || session.getStatus() == TradeSession.TradeStatus.CANCELLED) {
            return;
        }

        // Player closed GUI unexpectedly — cancel the trade (deferred one tick
        // to allow programmatic close-and-reopen in money prompt flow)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Only cancel if the session is still active (not already cancelled/completed)
            TradeSession current = tradeManager.getSession(player.getUniqueId());
            if (current != null && current == session
                    && session.getStatus() != TradeSession.TradeStatus.COMPLETED
                    && session.getStatus() != TradeSession.TradeStatus.CANCELLED) {
                // Don't cancel if the player is entering money via chat
                if (!PENDING_MONEY_INPUTS.containsKey(player.getUniqueId())) {
                    tradeManager.cancelSession(player.getUniqueId());
                }
            }
        }, 1L);
    }

    // -------------------------------------------------------------------------
    // Player quit
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PENDING_MONEY_INPUTS.remove(uuid);
        if (tradeManager.hasActiveSession(uuid)) {
            tradeManager.cancelSession(uuid);
        }
    }

    // -------------------------------------------------------------------------
    // Chat — money input
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        PendingMoneyInput pending = PENDING_MONEY_INPUTS.get(uuid);
        if (pending == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (message.equalsIgnoreCase("cancel")) {
            PENDING_MONEY_INPUTS.remove(uuid);
            player.sendMessage(mm.deserialize("<gray>Money input cancelled."));
            reopenGui(player, pending.session());
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(message);
        } catch (NumberFormatException e) {
            player.sendMessage(mm.deserialize("<red>Invalid amount. Enter a number or type <yellow>cancel</yellow>."));
            return;
        }

        if (amount < 0) {
            player.sendMessage(mm.deserialize("<red>Amount cannot be negative."));
            return;
        }

        PENDING_MONEY_INPUTS.remove(uuid);
        TradeSession session = pending.session();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (pending.adding()) {
                double currentMoney = session.getMoneyFor(uuid);
                double newAmount    = currentMoney + amount;
                // Validate the player has enough funds
                if (!plugin.getVaultHook().has(player, newAmount)) {
                    plugin.getMessagesManager().send(player, "trade-insufficient-funds");
                    reopenGui(player, session);
                    return;
                }
                session.setMoney(uuid, newAmount);
                plugin.getMessagesManager().send(player, "trade-money-set",
                        Placeholder.unparsed("amount", plugin.getVaultHook().format(newAmount)));
            } else {
                double currentMoney = session.getMoneyFor(uuid);
                double newAmount    = Math.max(0, currentMoney - amount);
                session.setMoney(uuid, newAmount);
                plugin.getMessagesManager().send(player, "trade-money-set",
                        Placeholder.unparsed("amount", plugin.getVaultHook().format(newAmount)));
            }
            tradeManager.updateBothGuis(session);
            reopenGui(player, session);
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void reopenGui(Player player, TradeSession session) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (session.getStatus() == TradeSession.TradeStatus.CANCELLED
                    || session.getStatus() == TradeSession.TradeStatus.COMPLETED) {
                return;
            }
            TradeGUI gui = tradeManager.getGui(player.getUniqueId());
            if (gui != null) {
                gui.open();
            }
        }, 1L);
    }
}
