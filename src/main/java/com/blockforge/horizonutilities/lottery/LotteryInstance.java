package com.blockforge.horizonutilities.lottery;

import com.blockforge.horizonutilities.util.TimeUtil;

import java.util.UUID;

/**
 * Runtime state of a single active (or recently drawn) lottery instance.
 */
public class LotteryInstance {

    private final int id;
    private final String tierId;
    private double currentPot;
    private final long startedAt;
    private final long drawAt;
    private String status;           // "ACTIVE", "DRAWN", "CANCELLED"
    private UUID winnerUuid;         // null until drawn
    private String winnerName;       // null until drawn
    private final LotteryTierConfig tierConfig;

    public LotteryInstance(int id, String tierId, double currentPot,
                           long startedAt, long drawAt, String status,
                           UUID winnerUuid, String winnerName,
                           LotteryTierConfig tierConfig) {
        this.id = id;
        this.tierId = tierId;
        this.currentPot = currentPot;
        this.startedAt = startedAt;
        this.drawAt = drawAt;
        this.status = status;
        this.winnerUuid = winnerUuid;
        this.winnerName = winnerName;
        this.tierConfig = tierConfig;
    }

    // -------------------------------------------------------------------------
    // Mutators
    // -------------------------------------------------------------------------

    /** Add the cost of newly purchased ticket(s) to the pot. */
    public void addTicketPurchase(double amount) {
        this.currentPot += amount;
    }

    /** Mark this instance as drawn with the given winner details. */
    public void markDrawn(UUID winnerUuid, String winnerName) {
        this.status = "DRAWN";
        this.winnerUuid = winnerUuid;
        this.winnerName = winnerName;
    }

    /** Mark this instance as cancelled. */
    public void markCancelled() {
        this.status = "CANCELLED";
    }

    // -------------------------------------------------------------------------
    // Derived helpers
    // -------------------------------------------------------------------------

    /** Human-readable time until the draw (e.g. "2h 30m"). */
    public String getTimeUntilDraw() {
        long seconds = (drawAt - System.currentTimeMillis()) / 1000L;
        return TimeUtil.formatDuration(seconds);
    }

    public boolean isActive() { return "ACTIVE".equalsIgnoreCase(status); }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int getId() { return id; }
    public String getTierId() { return tierId; }
    public double getCurrentPot() { return currentPot; }
    public void setCurrentPot(double pot) { this.currentPot = pot; }
    public long getStartedAt() { return startedAt; }
    public long getDrawAt() { return drawAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getWinnerUuid() { return winnerUuid; }
    public String getWinnerName() { return winnerName; }
    public LotteryTierConfig getTierConfig() { return tierConfig; }
}
