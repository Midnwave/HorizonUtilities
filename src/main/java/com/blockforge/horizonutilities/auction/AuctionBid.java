package com.blockforge.horizonutilities.auction;

public record AuctionBid(int id, int listingId, String bidderUuid, String bidderName, double amount, long bidAt) {
}
