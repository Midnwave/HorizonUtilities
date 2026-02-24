package com.blockforge.horizonutilities.auction;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuctionManager {

    private final HorizonUtilitiesPlugin plugin;

    public AuctionManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public int createListing(Player seller, ItemStack item, double startPrice, Double buyoutPrice, int durationHours) {
        var cfg = plugin.getAuctionHouseConfig();
        double fee = startPrice * (cfg.getListingFeePercent() / 100.0);
        if (!seller.hasPermission("horizonutilities.ah.bypass.fee") && fee > 0) {
            if (!plugin.getVaultHook().withdraw(seller, fee)) return -1;
        } else {
            fee = 0;
        }

        long now = System.currentTimeMillis();
        long expiresAt = now + (durationHours * 3600000L);
        String category = AuctionCategory.detect(item);
        String displayName = item.getType().name().toLowerCase().replace('_', ' ');

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO ah_listings (seller_uuid, seller_name, item_data, item_material, item_display_name, start_price, buyout_price, current_bid, category, listed_at, expires_at, status, listing_fee) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 'ACTIVE', ?)",
                    Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, seller.getUniqueId().toString());
            stmt.setString(2, seller.getName());
            stmt.setBytes(3, ItemSerializer.serialize(item));
            stmt.setString(4, item.getType().name());
            stmt.setString(5, displayName);
            stmt.setDouble(6, startPrice);
            if (buyoutPrice != null && buyoutPrice > 0) {
                stmt.setDouble(7, buyoutPrice);
            } else {
                stmt.setNull(7, Types.REAL);
            }
            stmt.setString(8, category);
            stmt.setLong(9, now);
            stmt.setLong(10, expiresAt);
            stmt.setDouble(11, fee);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create listing: " + e.getMessage());
            // refund fee
            if (fee > 0) plugin.getVaultHook().deposit(seller, fee);
        }
        return -1;
    }

    public List<AuctionListing> getActiveListings(String category, int page, int perPage) {
        List<AuctionListing> listings = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            String sql = "SELECT * FROM ah_listings WHERE status = 'ACTIVE'";
            if (category != null && !category.equals("All")) {
                sql += " AND category = ?";
            }
            sql += " ORDER BY listed_at DESC LIMIT ? OFFSET ?";

            PreparedStatement stmt = conn.prepareStatement(sql);
            int idx = 1;
            if (category != null && !category.equals("All")) {
                stmt.setString(idx++, category);
            }
            stmt.setInt(idx++, perPage);
            stmt.setInt(idx, page * perPage);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                listings.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch listings: " + e.getMessage());
        }
        return listings;
    }

    public int countActiveListings(String category) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            String sql = "SELECT COUNT(*) FROM ah_listings WHERE status = 'ACTIVE'";
            if (category != null && !category.equals("All")) {
                sql += " AND category = ?";
            }
            PreparedStatement stmt = conn.prepareStatement(sql);
            if (category != null && !category.equals("All")) {
                stmt.setString(1, category);
            }
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to count listings: " + e.getMessage());
        }
        return 0;
    }

    public List<AuctionListing> searchListings(String query, int page, int perPage) {
        List<AuctionListing> listings = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM ah_listings WHERE status = 'ACTIVE' AND (item_material LIKE ? OR item_display_name LIKE ?) ORDER BY listed_at DESC LIMIT ? OFFSET ?");
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setInt(3, perPage);
            stmt.setInt(4, page * perPage);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                listings.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to search listings: " + e.getMessage());
        }
        return listings;
    }

    public AuctionListing getListing(int id) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM ah_listings WHERE id = ?");
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return fromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get listing: " + e.getMessage());
        }
        return null;
    }

    public int countPlayerListings(UUID playerUuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ah_listings WHERE seller_uuid = ? AND status = 'ACTIVE'");
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to count player listings: " + e.getMessage());
        }
        return 0;
    }

    public boolean placeBid(Player bidder, AuctionListing listing, double amount) {
        var cfg = plugin.getAuctionHouseConfig();

        // escrow: deduct from bidder
        if (!plugin.getVaultHook().withdraw(bidder, amount)) return false;

        // return previous bidder's escrow
        if (listing.hasBids() && cfg.isEscrowEnabled()) {
            returnEscrow(listing.getId(), listing.getCurrentBidderUuid());
        }

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            // update listing
            PreparedStatement update = conn.prepareStatement(
                    "UPDATE ah_listings SET current_bid = ?, current_bidder_uuid = ?, current_bidder_name = ? WHERE id = ?");
            update.setDouble(1, amount);
            update.setString(2, bidder.getUniqueId().toString());
            update.setString(3, bidder.getName());
            update.setInt(4, listing.getId());
            update.executeUpdate();

            // record bid
            PreparedStatement bid = conn.prepareStatement(
                    "INSERT INTO ah_bids (listing_id, bidder_uuid, bidder_name, amount, bid_at) VALUES (?, ?, ?, ?, ?)");
            bid.setInt(1, listing.getId());
            bid.setString(2, bidder.getUniqueId().toString());
            bid.setString(3, bidder.getName());
            bid.setDouble(4, amount);
            bid.setLong(5, System.currentTimeMillis());
            bid.executeUpdate();

            // store escrow
            if (cfg.isEscrowEnabled()) {
                PreparedStatement escrow = conn.prepareStatement(
                        "INSERT INTO ah_escrow (listing_id, bidder_uuid, amount, created_at) VALUES (?, ?, ?, ?)");
                escrow.setInt(1, listing.getId());
                escrow.setString(2, bidder.getUniqueId().toString());
                escrow.setDouble(3, amount);
                escrow.setLong(4, System.currentTimeMillis());
                escrow.executeUpdate();
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to place bid: " + e.getMessage());
            plugin.getVaultHook().deposit(bidder, amount);
            return false;
        }
    }

    public boolean buyNow(Player buyer, AuctionListing listing) {
        double price = listing.getBuyoutPrice();
        double tax = price * (plugin.getAuctionHouseConfig().getSalesTaxPercent() / 100.0);
        if (buyer.hasPermission("horizonutilities.ah.bypass.tax")) tax = 0;

        if (!plugin.getVaultHook().withdraw(buyer, price)) return false;

        // return any existing escrow for this listing
        returnAllEscrow(listing.getId());

        double sellerAmount = price - tax;
        addToCollection(listing.getSellerUuid(), "MONEY", null, sellerAmount, "Item sold: " + listing.getItemDisplayName());

        // record transaction
        recordTransaction(listing, buyer.getUniqueId().toString(), price, "BUYOUT", tax, listing.getListingFee());

        // mark listing as sold
        updateListingStatus(listing.getId(), "SOLD");

        // add item to buyer's collection
        addToCollection(buyer.getUniqueId().toString(), "ITEM", listing.getItem(), 0, "Purchased: " + listing.getItemDisplayName());

        // notify seller
        plugin.getNotificationManager().notify(listing.getSellerUuid(), "ah-item-sold",
                "item", listing.getItemDisplayName(),
                "price", plugin.getVaultHook().format(price),
                "tax", plugin.getVaultHook().format(tax));

        return true;
    }

    public void completeBidWin(AuctionListing listing) {
        double price = listing.getCurrentBid();
        double tax = price * (plugin.getAuctionHouseConfig().getSalesTaxPercent() / 100.0);
        double sellerAmount = price - tax;

        // escrow already held, just delete it
        deleteEscrow(listing.getId(), listing.getCurrentBidderUuid());

        addToCollection(listing.getSellerUuid(), "MONEY", null, sellerAmount, "Auction won: " + listing.getItemDisplayName());
        addToCollection(listing.getCurrentBidderUuid(), "ITEM", listing.getItem(), 0, "Won auction: " + listing.getItemDisplayName());

        recordTransaction(listing, listing.getCurrentBidderUuid(), price, "AUCTION", tax, listing.getListingFee());
        updateListingStatus(listing.getId(), "SOLD");

        plugin.getNotificationManager().notify(listing.getSellerUuid(), "ah-item-sold",
                "item", listing.getItemDisplayName(), "price", plugin.getVaultHook().format(price),
                "tax", plugin.getVaultHook().format(tax));
        plugin.getNotificationManager().notify(listing.getCurrentBidderUuid(), "ah-bid-won",
                "item", listing.getItemDisplayName());
    }

    public void expireListing(AuctionListing listing) {
        returnAllEscrow(listing.getId());
        addToCollection(listing.getSellerUuid(), "ITEM", listing.getItem(), 0, "Listing expired: " + listing.getItemDisplayName());
        updateListingStatus(listing.getId(), "EXPIRED");
        plugin.getNotificationManager().notify(listing.getSellerUuid(), "ah-listing-expired",
                "item", listing.getItemDisplayName());
    }

    public void cancelListing(int listingId) {
        AuctionListing listing = getListing(listingId);
        if (listing == null) return;
        returnAllEscrow(listingId);
        addToCollection(listing.getSellerUuid(), "ITEM", listing.getItem(), 0, "Listing cancelled");
        updateListingStatus(listingId, "CANCELLED");
    }

    public void addToCollection(String playerUuid, String type, ItemStack item, double amount, String reason) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO ah_collection (player_uuid, type, item_data, amount, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)");
            stmt.setString(1, playerUuid);
            stmt.setString(2, type);
            stmt.setBytes(3, item != null ? ItemSerializer.serialize(item) : null);
            stmt.setDouble(4, amount);
            stmt.setString(5, reason);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add to collection: " + e.getMessage());
        }
    }

    public List<CollectionEntry> getCollection(UUID playerUuid) {
        List<CollectionEntry> entries = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM ah_collection WHERE player_uuid = ? ORDER BY created_at DESC");
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                entries.add(new CollectionEntry(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getBytes("item_data") != null ? ItemSerializer.deserialize(rs.getBytes("item_data")) : null,
                        rs.getDouble("amount"),
                        rs.getString("reason")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get collection: " + e.getMessage());
        }
        return entries;
    }

    public void removeCollectionEntry(int id) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM ah_collection WHERE id = ?");
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to remove collection entry: " + e.getMessage());
        }
    }

    public boolean isBanned(UUID playerUuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM ah_bans WHERE player_uuid = ?");
            stmt.setString(1, playerUuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public void ban(UUID playerUuid, String bannedBy, String reason) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO ah_bans (player_uuid, banned_by, reason, banned_at) VALUES (?, ?, ?, ?)");
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, bannedBy);
            stmt.setString(3, reason);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to ban player: " + e.getMessage());
        }
    }

    public void unban(UUID playerUuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM ah_bans WHERE player_uuid = ?");
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to unban player: " + e.getMessage());
        }
    }

    public int getMaxListings(Player player) {
        for (int n = 100; n >= 1; n--) {
            if (player.hasPermission("horizonutilities.ah.listings." + n)) return n;
        }
        return plugin.getAuctionHouseConfig().getMaxListingsDefault();
    }

    private void returnEscrow(int listingId, String bidderUuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT amount FROM ah_escrow WHERE listing_id = ? AND bidder_uuid = ?");
            stmt.setInt(1, listingId);
            stmt.setString(2, bidderUuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                double amount = rs.getDouble("amount");
                var offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(UUID.fromString(bidderUuid));
                plugin.getVaultHook().deposit(offlinePlayer, amount);
                deleteEscrow(listingId, bidderUuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to return escrow: " + e.getMessage());
        }
    }

    private void returnAllEscrow(int listingId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT bidder_uuid, amount FROM ah_escrow WHERE listing_id = ?");
            stmt.setInt(1, listingId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                var offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("bidder_uuid")));
                plugin.getVaultHook().deposit(offlinePlayer, rs.getDouble("amount"));
            }
            PreparedStatement delete = conn.prepareStatement("DELETE FROM ah_escrow WHERE listing_id = ?");
            delete.setInt(1, listingId);
            delete.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to return all escrow: " + e.getMessage());
        }
    }

    private void deleteEscrow(int listingId, String bidderUuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM ah_escrow WHERE listing_id = ? AND bidder_uuid = ?");
            stmt.setInt(1, listingId);
            stmt.setString(2, bidderUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete escrow: " + e.getMessage());
        }
    }

    private void recordTransaction(AuctionListing listing, String buyerUuid, double price, String type, double tax, double fee) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO ah_transactions (listing_id, seller_uuid, buyer_uuid, item_data, item_material, sale_price, sale_type, tax_amount, fee_amount, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            stmt.setInt(1, listing.getId());
            stmt.setString(2, listing.getSellerUuid());
            stmt.setString(3, buyerUuid);
            stmt.setBytes(4, ItemSerializer.serialize(listing.getItem()));
            stmt.setString(5, listing.getItemMaterial());
            stmt.setDouble(6, price);
            stmt.setString(7, type);
            stmt.setDouble(8, tax);
            stmt.setDouble(9, fee);
            stmt.setLong(10, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to record transaction: " + e.getMessage());
        }

        plugin.getPriceHistoryManager().recordSale(listing.getItemMaterial(), price);
    }

    private void updateListingStatus(int id, String status) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE ah_listings SET status = ? WHERE id = ?");
            stmt.setString(1, status);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to update listing status: " + e.getMessage());
        }
    }

    public int countSearchListings(String query) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ah_listings WHERE status = 'ACTIVE' AND (item_material LIKE ? OR item_display_name LIKE ?)");
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to count search listings: " + e.getMessage());
        }
        return 0;
    }

    public List<AuctionListing> getPlayerActiveListings(UUID playerUuid, int page, int perPage) {
        List<AuctionListing> listings = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM ah_listings WHERE seller_uuid = ? AND status = 'ACTIVE' ORDER BY listed_at DESC LIMIT ? OFFSET ?");
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, perPage);
            stmt.setInt(3, page * perPage);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                listings.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch player listings: " + e.getMessage());
        }
        return listings;
    }

    public List<AuctionTransaction> getTransactions(String playerUuid, int page, int perPage) {
        List<AuctionTransaction> transactions = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM ah_transactions WHERE seller_uuid = ? OR buyer_uuid = ? ORDER BY completed_at DESC LIMIT ? OFFSET ?");
            stmt.setString(1, playerUuid);
            stmt.setString(2, playerUuid);
            stmt.setInt(3, perPage);
            stmt.setInt(4, page * perPage);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                transactions.add(new AuctionTransaction(
                        rs.getInt("id"), rs.getInt("listing_id"),
                        rs.getString("seller_uuid"), rs.getString("buyer_uuid"),
                        ItemSerializer.deserialize(rs.getBytes("item_data")),
                        rs.getString("item_material"), rs.getDouble("sale_price"),
                        rs.getString("sale_type"), rs.getDouble("tax_amount"),
                        rs.getDouble("fee_amount"), rs.getLong("completed_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch transactions: " + e.getMessage());
        }
        return transactions;
    }

    public int countTransactions(String playerUuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM ah_transactions WHERE seller_uuid = ? OR buyer_uuid = ?");
            stmt.setString(1, playerUuid);
            stmt.setString(2, playerUuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to count transactions: " + e.getMessage());
        }
        return 0;
    }

    public List<AuctionListing> getExpiredListings() {
        List<AuctionListing> listings = new ArrayList<>();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM ah_listings WHERE status = 'ACTIVE' AND expires_at <= ?");
            stmt.setLong(1, System.currentTimeMillis());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                listings.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fetch expired listings: " + e.getMessage());
        }
        return listings;
    }

    private AuctionListing fromResultSet(ResultSet rs) throws SQLException {
        AuctionListing listing = new AuctionListing();
        listing.setId(rs.getInt("id"));
        listing.setSellerUuid(rs.getString("seller_uuid"));
        listing.setSellerName(rs.getString("seller_name"));
        listing.setItem(ItemSerializer.deserialize(rs.getBytes("item_data")));
        listing.setItemMaterial(rs.getString("item_material"));
        listing.setItemDisplayName(rs.getString("item_display_name"));
        listing.setStartPrice(rs.getDouble("start_price"));
        double buyout = rs.getDouble("buyout_price");
        listing.setBuyoutPrice(rs.wasNull() ? null : buyout);
        listing.setCurrentBid(rs.getDouble("current_bid"));
        listing.setCurrentBidderUuid(rs.getString("current_bidder_uuid"));
        listing.setCurrentBidderName(rs.getString("current_bidder_name"));
        listing.setCategory(rs.getString("category"));
        listing.setListedAt(rs.getLong("listed_at"));
        listing.setExpiresAt(rs.getLong("expires_at"));
        listing.setStatus(rs.getString("status"));
        listing.setListingFee(rs.getDouble("listing_fee"));
        return listing;
    }

    public record CollectionEntry(int id, String type, ItemStack item, double amount, String reason) {}
}
