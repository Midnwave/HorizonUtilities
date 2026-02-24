package com.blockforge.horizonutilities.lottery;

/**
 * Immutable configuration for one lottery tier, loaded from lottery.yml.
 */
public class LotteryTierConfig {

    private final String tierId;
    private final String displayName;
    private final String schedule;       // "DAILY" or "WEEKLY"
    private final String drawTime;       // "20:00"
    private final String drawDay;        // null for DAILY, e.g. "SATURDAY" for WEEKLY
    private final double startingPot;
    private final double ticketPrice;
    private final int maxTicketsPerPlayer;
    private final int minPlayers;
    private final boolean broadcastBuy;
    private final boolean broadcastDraw;

    public LotteryTierConfig(String tierId, String displayName, String schedule,
                              String drawTime, String drawDay, double startingPot,
                              double ticketPrice, int maxTicketsPerPlayer,
                              int minPlayers, boolean broadcastBuy, boolean broadcastDraw) {
        this.tierId = tierId;
        this.displayName = displayName;
        this.schedule = schedule;
        this.drawTime = drawTime;
        this.drawDay = drawDay;
        this.startingPot = startingPot;
        this.ticketPrice = ticketPrice;
        this.maxTicketsPerPlayer = maxTicketsPerPlayer;
        this.minPlayers = minPlayers;
        this.broadcastBuy = broadcastBuy;
        this.broadcastDraw = broadcastDraw;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getTierId() { return tierId; }
    public String getDisplayName() { return displayName; }
    public String getSchedule() { return schedule; }
    public String getDrawTime() { return drawTime; }
    /** Returns null for DAILY schedules. */
    public String getDrawDay() { return drawDay; }
    public double getStartingPot() { return startingPot; }
    public double getTicketPrice() { return ticketPrice; }
    public int getMaxTicketsPerPlayer() { return maxTicketsPerPlayer; }
    public int getMinPlayers() { return minPlayers; }
    public boolean isBroadcastBuy() { return broadcastBuy; }
    public boolean isBroadcastDraw() { return broadcastDraw; }

    public boolean isDaily()  { return "DAILY".equalsIgnoreCase(schedule); }
    public boolean isWeekly() { return "WEEKLY".equalsIgnoreCase(schedule); }
}
