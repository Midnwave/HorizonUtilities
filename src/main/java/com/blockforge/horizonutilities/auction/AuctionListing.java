package com.blockforge.horizonutilities.auction;

import org.bukkit.inventory.ItemStack;

public class AuctionListing {

    private int id;
    private String sellerUuid;
    private String sellerName;
    private ItemStack item;
    private String itemMaterial;
    private String itemDisplayName;
    private double startPrice;
    private Double buyoutPrice;
    private double currentBid;
    private String currentBidderUuid;
    private String currentBidderName;
    private String category;
    private long listedAt;
    private long expiresAt;
    private String status;
    private double listingFee;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSellerUuid() { return sellerUuid; }
    public void setSellerUuid(String sellerUuid) { this.sellerUuid = sellerUuid; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public ItemStack getItem() { return item; }
    public void setItem(ItemStack item) { this.item = item; }
    public String getItemMaterial() { return itemMaterial; }
    public void setItemMaterial(String itemMaterial) { this.itemMaterial = itemMaterial; }
    public String getItemDisplayName() { return itemDisplayName; }
    public void setItemDisplayName(String itemDisplayName) { this.itemDisplayName = itemDisplayName; }
    public double getStartPrice() { return startPrice; }
    public void setStartPrice(double startPrice) { this.startPrice = startPrice; }
    public Double getBuyoutPrice() { return buyoutPrice; }
    public void setBuyoutPrice(Double buyoutPrice) { this.buyoutPrice = buyoutPrice; }
    public double getCurrentBid() { return currentBid; }
    public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }
    public String getCurrentBidderUuid() { return currentBidderUuid; }
    public void setCurrentBidderUuid(String currentBidderUuid) { this.currentBidderUuid = currentBidderUuid; }
    public String getCurrentBidderName() { return currentBidderName; }
    public void setCurrentBidderName(String currentBidderName) { this.currentBidderName = currentBidderName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public long getListedAt() { return listedAt; }
    public void setListedAt(long listedAt) { this.listedAt = listedAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getListingFee() { return listingFee; }
    public void setListingFee(double listingFee) { this.listingFee = listingFee; }

    public long getTimeLeftSeconds() {
        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
    }

    public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    public boolean hasBuyout() { return buyoutPrice != null && buyoutPrice > 0; }
    public boolean hasBids() { return currentBid > 0 && currentBidderUuid != null; }
}
