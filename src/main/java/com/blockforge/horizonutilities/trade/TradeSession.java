package com.blockforge.horizonutilities.trade;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Holds the live state of an active trade between two players.
 * Player 1 is always the one who sent the trade request.
 * Player 2 is the one who accepted it.
 */
public class TradeSession {

    public enum TradeStatus {
        WAITING,
        PLAYER1_CONFIRMED,
        PLAYER2_CONFIRMED,
        BOTH_CONFIRMED,
        COMPLETED,
        CANCELLED
    }

    private static final int MAX_ITEMS = 16;

    private final UUID player1Uuid;
    private final UUID player2Uuid;

    private final ItemStack[] player1Items;
    private final ItemStack[] player2Items;

    private double player1Money;
    private double player2Money;

    private TradeStatus status;
    private boolean player1Confirmed;
    private boolean player2Confirmed;

    public TradeSession(UUID player1Uuid, UUID player2Uuid) {
        this.player1Uuid = player1Uuid;
        this.player2Uuid = player2Uuid;
        this.player1Items = new ItemStack[MAX_ITEMS];
        this.player2Items = new ItemStack[MAX_ITEMS];
        this.player1Money = 0.0;
        this.player2Money = 0.0;
        this.status = TradeStatus.WAITING;
        this.player1Confirmed = false;
        this.player2Confirmed = false;
    }

    // -------------------------------------------------------------------------
    // Confirmation logic
    // -------------------------------------------------------------------------

    /**
     * Sets the confirmed state for the given player.
     * If either player changes their offer, both confirmations must be reset
     * (this is handled by callers before calling setConfirmed again).
     */
    public void setConfirmed(UUID playerUuid, boolean confirmed) {
        if (playerUuid.equals(player1Uuid)) {
            player1Confirmed = confirmed;
        } else if (playerUuid.equals(player2Uuid)) {
            player2Confirmed = confirmed;
        }
        recalculateStatus();
    }

    public boolean isFullyConfirmed() {
        return player1Confirmed && player2Confirmed;
    }

    /** Reset both confirmations when an offer changes. */
    public void resetConfirmations() {
        player1Confirmed = false;
        player2Confirmed = false;
        recalculateStatus();
    }

    private void recalculateStatus() {
        if (status == TradeStatus.COMPLETED || status == TradeStatus.CANCELLED) return;
        if (player1Confirmed && player2Confirmed) {
            status = TradeStatus.BOTH_CONFIRMED;
        } else if (player1Confirmed) {
            status = TradeStatus.PLAYER1_CONFIRMED;
        } else if (player2Confirmed) {
            status = TradeStatus.PLAYER2_CONFIRMED;
        } else {
            status = TradeStatus.WAITING;
        }
    }

    // -------------------------------------------------------------------------
    // Player helpers
    // -------------------------------------------------------------------------

    public UUID getOtherPlayerUuid(UUID uuid) {
        if (uuid.equals(player1Uuid)) return player2Uuid;
        if (uuid.equals(player2Uuid)) return player1Uuid;
        return null;
    }

    public boolean isParticipant(UUID uuid) {
        return uuid.equals(player1Uuid) || uuid.equals(player2Uuid);
    }

    public boolean isPlayer1(UUID uuid) {
        return uuid.equals(player1Uuid);
    }

    // -------------------------------------------------------------------------
    // Item management
    // -------------------------------------------------------------------------

    /**
     * Add an item to the offering player's trade slots.
     * Returns false if all 16 slots are full.
     */
    public boolean addItem(UUID playerUuid, ItemStack item) {
        ItemStack[] slots = getItemsFor(playerUuid);
        if (slots == null) return false;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = item.clone();
                resetConfirmations();
                return true;
            }
        }
        return false;
    }

    /**
     * Remove an item from the given slot index of the given player's offer.
     * Returns the removed ItemStack, or null if slot was empty.
     */
    public ItemStack removeItem(UUID playerUuid, int slot) {
        ItemStack[] slots = getItemsFor(playerUuid);
        if (slots == null || slot < 0 || slot >= slots.length) return null;
        ItemStack item = slots[slot];
        if (item != null) {
            slots[slot] = null;
            resetConfirmations();
        }
        return item;
    }

    // -------------------------------------------------------------------------
    // Money management
    // -------------------------------------------------------------------------

    public void setMoney(UUID playerUuid, double amount) {
        if (playerUuid.equals(player1Uuid)) {
            player1Money = Math.max(0.0, amount);
        } else if (playerUuid.equals(player2Uuid)) {
            player2Money = Math.max(0.0, amount);
        }
        resetConfirmations();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private ItemStack[] getItemsFor(UUID playerUuid) {
        if (playerUuid.equals(player1Uuid)) return player1Items;
        if (playerUuid.equals(player2Uuid)) return player2Items;
        return null;
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public UUID getPlayer1Uuid() { return player1Uuid; }
    public UUID getPlayer2Uuid() { return player2Uuid; }

    public ItemStack[] getPlayer1Items() { return player1Items; }
    public ItemStack[] getPlayer2Items() { return player2Items; }

    public ItemStack[] getItemsForPlayer(UUID playerUuid) {
        return getItemsFor(playerUuid);
    }

    public double getPlayer1Money() { return player1Money; }
    public double getPlayer2Money() { return player2Money; }

    public double getMoneyFor(UUID playerUuid) {
        if (playerUuid.equals(player1Uuid)) return player1Money;
        if (playerUuid.equals(player2Uuid)) return player2Money;
        return 0.0;
    }

    public TradeStatus getStatus() { return status; }

    public void setStatus(TradeStatus status) { this.status = status; }

    public boolean isPlayer1Confirmed() { return player1Confirmed; }
    public boolean isPlayer2Confirmed() { return player2Confirmed; }

    public boolean isConfirmedFor(UUID playerUuid) {
        if (playerUuid.equals(player1Uuid)) return player1Confirmed;
        if (playerUuid.equals(player2Uuid)) return player2Confirmed;
        return false;
    }

    public int getMaxItems() { return MAX_ITEMS; }
}
