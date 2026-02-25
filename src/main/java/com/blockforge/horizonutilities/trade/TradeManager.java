package com.blockforge.horizonutilities.trade;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.economy.EconomyAuditLog;
import com.blockforge.horizonutilities.events.JobTradeEvent;
import com.blockforge.horizonutilities.trade.gui.TradeGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/**
 * Central manager for the trade system. Handles pending requests and active sessions.
 */
public class TradeManager {

    private final HorizonUtilitiesPlugin plugin;
    private final TradeConfig tradeConfig;
    private final EconomyAuditLog auditLog;

    /** Pending requests keyed by the TARGET player's UUID */
    private final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();

    /**
     * Active sessions. Both player UUIDs point to the same TradeSession instance,
     * so either player can look up their own session by their UUID.
     */
    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();

    /** GUI instances keyed by player UUID */
    private final Map<UUID, TradeGUI> openGuis = new HashMap<>();

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public TradeManager(HorizonUtilitiesPlugin plugin, TradeConfig tradeConfig) {
        this.plugin = plugin;
        this.tradeConfig = tradeConfig;
        this.auditLog = new EconomyAuditLog(plugin);
    }

    // -------------------------------------------------------------------------
    // Request handling
    // -------------------------------------------------------------------------

    /**
     * Sends a trade request from sender to target.
     * Performs all prerequisite checks before creating the request.
     */
    public void sendRequest(Player sender, Player target) {
        if (!sender.hasPermission("horizonutilities.trade.use")) {
            plugin.getMessagesManager().send(sender, "no-permission");
            return;
        }
        if (!target.hasPermission("horizonutilities.trade.use")) {
            plugin.getMessagesManager().send(sender, "trade-target-no-permission",
                    Placeholder.unparsed("player", target.getName()));
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            plugin.getMessagesManager().send(sender, "trade-self");
            return;
        }
        if (tradeConfig.isSameWorldOnly() && !sender.getWorld().equals(target.getWorld())) {
            plugin.getMessagesManager().send(sender, "trade-different-world");
            return;
        }
        if (hasActiveSession(sender.getUniqueId())) {
            plugin.getMessagesManager().send(sender, "trade-already-active");
            return;
        }
        if (hasActiveSession(target.getUniqueId())) {
            plugin.getMessagesManager().send(sender, "trade-target-busy",
                    Placeholder.unparsed("player", target.getName()));
            return;
        }
        if (pendingRequests.containsKey(target.getUniqueId())) {
            plugin.getMessagesManager().send(sender, "trade-request-already-pending",
                    Placeholder.unparsed("player", target.getName()));
            return;
        }

        TradeRequest request = new TradeRequest(
                sender.getUniqueId(), sender.getName(),
                target.getUniqueId(), target.getName()
        );
        pendingRequests.put(target.getUniqueId(), request);

        // Notify sender
        plugin.getMessagesManager().send(sender, "trade-request-sent",
                Placeholder.unparsed("player", target.getName()));

        // Notify target with clickable buttons
        target.sendMessage(miniMessage.deserialize(
                "<gold><bold>[Trade Request]</bold></gold> <yellow>" + sender.getName() +
                " wants to trade with you!</yellow>"));
        target.sendMessage(miniMessage.deserialize(
                "<green>[<click:run_command:/trade accept><hover:show_text:'<green>Accept trade'>" +
                "✔ Accept</hover></click>]</green> " +
                "<red>[<click:run_command:/trade decline><hover:show_text:'<red>Decline trade'>" +
                "✗ Decline</hover></click>]</red>"));

        // Schedule expiry task
        long timeoutTicks = tradeConfig.getRequestTimeoutSeconds() * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            TradeRequest current = pendingRequests.get(target.getUniqueId());
            if (current != null && current.getSenderUuid().equals(sender.getUniqueId()) && current.isExpired()) {
                pendingRequests.remove(target.getUniqueId());
                if (sender.isOnline()) {
                    plugin.getMessagesManager().send(sender, "trade-request-expired",
                            Placeholder.unparsed("player", target.getName()));
                }
                if (target.isOnline()) {
                    plugin.getMessagesManager().send(target, "trade-request-expired-target",
                            Placeholder.unparsed("player", sender.getName()));
                }
            }
        }, timeoutTicks);
    }

    /**
     * Accepts the pending request for the given target player.
     * Creates a new TradeSession and opens GUIs for both players.
     */
    public TradeSession acceptRequest(Player target) {
        TradeRequest request = pendingRequests.get(target.getUniqueId());
        if (request == null) {
            plugin.getMessagesManager().send(target, "trade-no-request");
            return null;
        }
        if (request.isExpired()) {
            pendingRequests.remove(target.getUniqueId());
            plugin.getMessagesManager().send(target, "trade-request-expired-target",
                    Placeholder.unparsed("player", request.getSenderName()));
            return null;
        }

        Player sender = plugin.getServer().getPlayer(request.getSenderUuid());
        if (sender == null || !sender.isOnline()) {
            pendingRequests.remove(target.getUniqueId());
            plugin.getMessagesManager().send(target, "trade-sender-offline");
            return null;
        }

        if (hasActiveSession(sender.getUniqueId()) || hasActiveSession(target.getUniqueId())) {
            pendingRequests.remove(target.getUniqueId());
            plugin.getMessagesManager().send(target, "trade-already-active");
            return null;
        }

        pendingRequests.remove(target.getUniqueId());

        // Create session — sender is player1, target is player2
        TradeSession session = new TradeSession(sender.getUniqueId(), target.getUniqueId());
        activeSessions.put(sender.getUniqueId(), session);
        activeSessions.put(target.getUniqueId(), session);

        // Open GUIs for both players (run next tick to avoid inventory issues)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            TradeGUI guiSender = new TradeGUI(plugin, this, tradeConfig, session, sender);
            TradeGUI guiTarget = new TradeGUI(plugin, this, tradeConfig, session, target);
            openGuis.put(sender.getUniqueId(), guiSender);
            openGuis.put(target.getUniqueId(), guiTarget);
            guiSender.open();
            guiTarget.open();
        }, 1L);

        return session;
    }

    /**
     * Declines the pending request for the given target player.
     */
    public void declineRequest(Player target) {
        TradeRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            plugin.getMessagesManager().send(target, "trade-no-request");
            return;
        }
        plugin.getMessagesManager().send(target, "trade-request-declined-self",
                Placeholder.unparsed("player", request.getSenderName()));

        Player sender = plugin.getServer().getPlayer(request.getSenderUuid());
        if (sender != null && sender.isOnline()) {
            plugin.getMessagesManager().send(sender, "trade-request-declined",
                    Placeholder.unparsed("player", target.getName()));
        }
    }

    // -------------------------------------------------------------------------
    // Session management
    // -------------------------------------------------------------------------

    /**
     * Cancels an active trade session. Returns all items to each player's inventory
     * and notifies both participants.
     */
    public void cancelSession(UUID playerUuid) {
        TradeSession session = activeSessions.get(playerUuid);
        if (session == null) return;

        session.setStatus(TradeSession.TradeStatus.CANCELLED);

        // Return items to both players
        returnItems(session.getPlayer1Uuid(), session.getPlayer1Items());
        returnItems(session.getPlayer2Uuid(), session.getPlayer2Items());

        // Close GUIs
        closeGuiFor(session.getPlayer1Uuid());
        closeGuiFor(session.getPlayer2Uuid());

        // Remove from map
        activeSessions.remove(session.getPlayer1Uuid());
        activeSessions.remove(session.getPlayer2Uuid());
        openGuis.remove(session.getPlayer1Uuid());
        openGuis.remove(session.getPlayer2Uuid());

        // Notify both players
        notifyIfOnline(session.getPlayer1Uuid(), "trade-cancelled");
        notifyIfOnline(session.getPlayer2Uuid(), "trade-cancelled");
    }

    /**
     * Finalizes the trade after both players have confirmed.
     * Performs full validation before executing.
     */
    public void completeTrade(TradeSession session) {
        if (!session.isFullyConfirmed()) return;

        Player p1 = plugin.getServer().getPlayer(session.getPlayer1Uuid());
        Player p2 = plugin.getServer().getPlayer(session.getPlayer2Uuid());

        if (p1 == null || !p1.isOnline() || p2 == null || !p2.isOnline()) {
            cancelSession(session.getPlayer1Uuid());
            return;
        }

        if (tradeConfig.isSameWorldOnly() && !p1.getWorld().equals(p2.getWorld())) {
            plugin.getMessagesManager().send(p1, "trade-different-world");
            plugin.getMessagesManager().send(p2, "trade-different-world");
            cancelSession(session.getPlayer1Uuid());
            return;
        }

        // Validate money
        if (session.getPlayer1Money() > 0 && !plugin.getVaultHook().has(p1, session.getPlayer1Money())) {
            plugin.getMessagesManager().send(p1, "trade-insufficient-funds");
            plugin.getMessagesManager().send(p2, "trade-partner-insufficient-funds",
                    Placeholder.unparsed("player", p1.getName()));
            session.resetConfirmations();
            updateBothGuis(session);
            return;
        }
        if (session.getPlayer2Money() > 0 && !plugin.getVaultHook().has(p2, session.getPlayer2Money())) {
            plugin.getMessagesManager().send(p2, "trade-insufficient-funds");
            plugin.getMessagesManager().send(p1, "trade-partner-insufficient-funds",
                    Placeholder.unparsed("player", p2.getName()));
            session.resetConfirmations();
            updateBothGuis(session);
            return;
        }

        // Count non-null items
        int p1ItemCount = countItems(session.getPlayer1Items());
        int p2ItemCount = countItems(session.getPlayer2Items());

        // Check inventory space — p1 receives p2's items, p2 receives p1's items
        if (!hasInventorySpace(p1, session.getPlayer2Items(), p2ItemCount)) {
            plugin.getMessagesManager().send(p1, "trade-no-inventory-space");
            plugin.getMessagesManager().send(p2, "trade-partner-no-space",
                    Placeholder.unparsed("player", p1.getName()));
            session.resetConfirmations();
            updateBothGuis(session);
            return;
        }
        if (!hasInventorySpace(p2, session.getPlayer1Items(), p1ItemCount)) {
            plugin.getMessagesManager().send(p2, "trade-no-inventory-space");
            plugin.getMessagesManager().send(p1, "trade-partner-no-space",
                    Placeholder.unparsed("player", p2.getName()));
            session.resetConfirmations();
            updateBothGuis(session);
            return;
        }

        // ---- Execute the trade ----
        session.setStatus(TradeSession.TradeStatus.COMPLETED);

        // Transfer money
        if (session.getPlayer1Money() > 0) {
            plugin.getVaultHook().withdraw(p1, session.getPlayer1Money());
            plugin.getVaultHook().deposit(p2, session.getPlayer1Money());
            double p1BalAfter = plugin.getVaultHook().getBalance(p1);
            double p2BalAfter = plugin.getVaultHook().getBalance(p2);
            auditLog.log(p1.getUniqueId(), p1.getName(), EconomyAuditLog.TRADE_MONEY,
                    -session.getPlayer1Money(), p1BalAfter, "trade", p2.getUniqueId());
            auditLog.log(p2.getUniqueId(), p2.getName(), EconomyAuditLog.TRADE_MONEY,
                    session.getPlayer1Money(), p2BalAfter, "trade", p1.getUniqueId());
        }
        if (session.getPlayer2Money() > 0) {
            plugin.getVaultHook().withdraw(p2, session.getPlayer2Money());
            plugin.getVaultHook().deposit(p1, session.getPlayer2Money());
            double p1BalAfter = plugin.getVaultHook().getBalance(p1);
            double p2BalAfter = plugin.getVaultHook().getBalance(p2);
            auditLog.log(p2.getUniqueId(), p2.getName(), EconomyAuditLog.TRADE_MONEY,
                    -session.getPlayer2Money(), p2BalAfter, "trade", p1.getUniqueId());
            auditLog.log(p1.getUniqueId(), p1.getName(), EconomyAuditLog.TRADE_MONEY,
                    session.getPlayer2Money(), p1BalAfter, "trade", p2.getUniqueId());
        }

        // Transfer items: p1's items go to p2, p2's items go to p1
        for (ItemStack item : session.getPlayer1Items()) {
            if (item != null) p2.getInventory().addItem(item.clone());
        }
        for (ItemStack item : session.getPlayer2Items()) {
            if (item != null) p1.getInventory().addItem(item.clone());
        }

        // Log to DB asynchronously
        if (tradeConfig.isLogTrades()) {
            final String p1Name = p1.getName();
            final String p2Name = p2.getName();
            final UUID p1Uuid = p1.getUniqueId();
            final UUID p2Uuid = p2.getUniqueId();
            final double p1Money = session.getPlayer1Money();
            final double p2Money = session.getPlayer2Money();
            final ItemStack[] p1Items = session.getPlayer1Items().clone();
            final ItemStack[] p2Items = session.getPlayer2Items().clone();

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    logTradeToDb(p1Uuid, p1Name, p2Uuid, p2Name, p1Items, p1Money, p2Items, p2Money));
        }

        // Close GUIs
        closeGuiFor(session.getPlayer1Uuid());
        closeGuiFor(session.getPlayer2Uuid());

        // Remove sessions
        activeSessions.remove(session.getPlayer1Uuid());
        activeSessions.remove(session.getPlayer2Uuid());
        openGuis.remove(session.getPlayer1Uuid());
        openGuis.remove(session.getPlayer2Uuid());

        // Fire job trade events (for PikaMug/Quests objective tracking)
        plugin.getServer().getPluginManager().callEvent(new JobTradeEvent(p1));
        plugin.getServer().getPluginManager().callEvent(new JobTradeEvent(p2));

        // Notify success
        plugin.getMessagesManager().send(p1, "trade-completed",
                Placeholder.unparsed("player", p2.getName()));
        plugin.getMessagesManager().send(p2, "trade-completed",
                Placeholder.unparsed("player", p1.getName()));
    }

    // -------------------------------------------------------------------------
    // GUI helpers
    // -------------------------------------------------------------------------

    public void updateBothGuis(TradeSession session) {
        TradeGUI gui1 = openGuis.get(session.getPlayer1Uuid());
        TradeGUI gui2 = openGuis.get(session.getPlayer2Uuid());
        if (gui1 != null) gui1.update();
        if (gui2 != null) gui2.update();
    }

    public TradeGUI getGui(UUID playerUuid) {
        return openGuis.get(playerUuid);
    }

    public void registerGui(UUID playerUuid, TradeGUI gui) {
        openGuis.put(playerUuid, gui);
    }

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    public TradeSession getSession(UUID playerUuid) {
        return activeSessions.get(playerUuid);
    }

    public boolean hasActiveSession(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    public boolean hasPendingRequest(UUID targetUuid) {
        return pendingRequests.containsKey(targetUuid);
    }

    public TradeRequest getPendingRequest(UUID targetUuid) {
        return pendingRequests.get(targetUuid);
    }

    public TradeConfig getTradeConfig() {
        return tradeConfig;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void returnItems(UUID playerUuid, ItemStack[] items) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;
        for (ItemStack item : items) {
            if (item != null) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                leftover.values().forEach(stack ->
                        player.getWorld().dropItemNaturally(player.getLocation(), stack));
            }
        }
    }

    private void closeGuiFor(UUID playerUuid) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            TradeGUI gui = openGuis.get(playerUuid);
            if (gui != null && player.getOpenInventory().getTopInventory().getHolder() instanceof TradeGUI) {
                player.closeInventory();
            }
        }
    }

    private void notifyIfOnline(UUID playerUuid, String messageKey) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            plugin.getMessagesManager().send(player, messageKey);
        }
    }

    private int countItems(ItemStack[] items) {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null) count++;
        }
        return count;
    }

    private boolean hasInventorySpace(Player player, ItemStack[] incomingItems, int itemCount) {
        if (itemCount == 0) return true;
        int freeSlots = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null) freeSlots++;
        }
        return freeSlots >= itemCount;
    }

    private void logTradeToDb(UUID p1Uuid, String p1Name, UUID p2Uuid, String p2Name,
                               ItemStack[] p1Items, double p1Money,
                               ItemStack[] p2Items, double p2Money) {
        StringBuilder p1ItemStr = new StringBuilder();
        for (ItemStack item : p1Items) {
            if (item != null) {
                if (p1ItemStr.length() > 0) p1ItemStr.append(",");
                p1ItemStr.append(item.getType().name()).append("x").append(item.getAmount());
            }
        }
        StringBuilder p2ItemStr = new StringBuilder();
        for (ItemStack item : p2Items) {
            if (item != null) {
                if (p2ItemStr.length() > 0) p2ItemStr.append(",");
                p2ItemStr.append(item.getType().name()).append("x").append(item.getAmount());
            }
        }

        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                     "INSERT INTO trade_log (player1_uuid, player1_name, player2_uuid, player2_name, " +
                     "player1_items, player1_money, player2_items, player2_money, completed_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, p1Uuid.toString());
            ps.setString(2, p1Name);
            ps.setString(3, p2Uuid.toString());
            ps.setString(4, p2Name);
            ps.setString(5, p1ItemStr.toString());
            ps.setDouble(6, p1Money);
            ps.setString(7, p2ItemStr.toString());
            ps.setDouble(8, p2Money);
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to log trade to DB", e);
        }
    }
}
