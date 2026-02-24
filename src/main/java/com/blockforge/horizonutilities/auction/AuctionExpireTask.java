package com.blockforge.horizonutilities.auction;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuctionExpireTask {

    private final HorizonUtilitiesPlugin plugin;

    public AuctionExpireTask(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // run every second
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        var cfg = plugin.getAuctionHouseConfig();
        var expired = plugin.getAuctionManager().getExpiredListings();

        for (AuctionListing listing : expired) {
            // anti-snipe check
            if (cfg.isAntiSnipeEnabled() && listing.hasBids()) {
                long lastBidTime = getLastBidTime(listing.getId());
                long timeSinceLastBid = (listing.getExpiresAt() - lastBidTime) / 1000;

                if (timeSinceLastBid <= cfg.getAntiSnipeTriggerSeconds()) {
                    int extensions = getExtensionCount(listing.getId());
                    if (extensions < cfg.getAntiSnipeMaxExtensions()) {
                        extendListing(listing.getId(), cfg.getAntiSnipeExtensionSeconds());
                        incrementExtensions(listing.getId());
                        continue;
                    }
                }
            }

            // process expired listing
            if (listing.hasBids()) {
                plugin.getAuctionManager().completeBidWin(listing);
            } else {
                plugin.getAuctionManager().expireListing(listing);
            }
        }
    }

    private long getLastBidTime(int listingId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT MAX(bid_at) as last_bid FROM ah_bids WHERE listing_id = ?");
            stmt.setInt(1, listingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("last_bid");
        } catch (SQLException ignored) {}
        return 0;
    }

    private void extendListing(int listingId, int seconds) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE ah_listings SET expires_at = expires_at + ? WHERE id = ?");
            stmt.setLong(1, seconds * 1000L);
            stmt.setInt(2, listingId);
            stmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // simple extension counter using a transient approach (counts bids near expiry)
    private int getExtensionCount(int listingId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ah_bids WHERE listing_id = ? AND bid_at > (SELECT expires_at - ? FROM ah_listings WHERE id = ?)");
            stmt.setInt(1, listingId);
            stmt.setLong(2, plugin.getAuctionHouseConfig().getAntiSnipeTriggerSeconds() * 1000L);
            stmt.setInt(3, listingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return 0;
    }

    private void incrementExtensions(int listingId) {
        // tracked implicitly by bid count near expiry
    }
}
