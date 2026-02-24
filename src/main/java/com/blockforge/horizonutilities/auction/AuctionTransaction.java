package com.blockforge.horizonutilities.auction;

import org.bukkit.inventory.ItemStack;

public record AuctionTransaction(int id, int listingId, String sellerUuid, String buyerUuid,
                                  ItemStack item, String itemMaterial, double salePrice,
                                  String saleType, double taxAmount, double feeAmount, long completedAt) {
}
