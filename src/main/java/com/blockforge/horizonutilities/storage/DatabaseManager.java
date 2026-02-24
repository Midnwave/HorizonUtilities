package com.blockforge.horizonutilities.storage;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final HorizonUtilitiesPlugin plugin;
    private Connection connection;

    public DatabaseManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), plugin.getConfigManager().getDatabaseFile());
            plugin.getDataFolder().mkdirs();
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            createTables();
            plugin.getLogger().info("SQLite database connected.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to SQLite: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("PRAGMA journal_mode=WAL");
            stmt.executeUpdate("PRAGMA foreign_keys=ON");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ah_listings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller_uuid TEXT NOT NULL,
                    seller_name TEXT NOT NULL,
                    item_data BLOB NOT NULL,
                    item_material TEXT NOT NULL,
                    item_display_name TEXT,
                    start_price REAL NOT NULL,
                    buyout_price REAL,
                    current_bid REAL NOT NULL DEFAULT 0,
                    current_bidder_uuid TEXT,
                    current_bidder_name TEXT,
                    category TEXT NOT NULL,
                    listed_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    listing_fee REAL NOT NULL DEFAULT 0
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_listings_status ON ah_listings(status)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_listings_seller ON ah_listings(seller_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_listings_category ON ah_listings(category)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_listings_expires ON ah_listings(expires_at)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ah_bids (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    listing_id INTEGER NOT NULL,
                    bidder_uuid TEXT NOT NULL,
                    bidder_name TEXT NOT NULL,
                    amount REAL NOT NULL,
                    bid_at INTEGER NOT NULL,
                    FOREIGN KEY (listing_id) REFERENCES ah_listings(id)
                )""");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ah_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    listing_id INTEGER NOT NULL,
                    seller_uuid TEXT NOT NULL,
                    buyer_uuid TEXT NOT NULL,
                    item_data BLOB NOT NULL,
                    item_material TEXT NOT NULL,
                    sale_price REAL NOT NULL,
                    sale_type TEXT NOT NULL,
                    tax_amount REAL NOT NULL DEFAULT 0,
                    fee_amount REAL NOT NULL DEFAULT 0,
                    completed_at INTEGER NOT NULL
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_seller ON ah_transactions(seller_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_buyer ON ah_transactions(buyer_uuid)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ah_collection (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    type TEXT NOT NULL,
                    item_data BLOB,
                    amount REAL NOT NULL DEFAULT 0,
                    reason TEXT,
                    created_at INTEGER NOT NULL
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_collection_player ON ah_collection(player_uuid)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ah_notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    message_key TEXT NOT NULL,
                    message_data TEXT,
                    created_at INTEGER NOT NULL
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notifications_player ON ah_notifications(player_uuid)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ah_price_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    material TEXT NOT NULL,
                    avg_price REAL NOT NULL,
                    min_price REAL NOT NULL,
                    max_price REAL NOT NULL,
                    sale_count INTEGER NOT NULL,
                    period_date TEXT NOT NULL
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_price_material ON ah_price_history(material)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ah_bans (
                    player_uuid TEXT PRIMARY KEY,
                    banned_by TEXT NOT NULL,
                    reason TEXT,
                    banned_at INTEGER NOT NULL
                )""");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS chatgames_stats (
                    player_uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    wins INTEGER NOT NULL DEFAULT 0,
                    current_streak INTEGER NOT NULL DEFAULT 0,
                    best_streak INTEGER NOT NULL DEFAULT 0,
                    fastest_time_ms INTEGER NOT NULL DEFAULT 0,
                    last_win INTEGER NOT NULL DEFAULT 0
                )""");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ah_escrow (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    listing_id INTEGER NOT NULL,
                    bidder_uuid TEXT NOT NULL,
                    amount REAL NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (listing_id) REFERENCES ah_listings(id)
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_escrow_listing ON ah_escrow(listing_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_escrow_bidder ON ah_escrow(bidder_uuid)");

            // Jobs tables
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS jobs_players (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "job_id TEXT NOT NULL," +
                "level INTEGER NOT NULL DEFAULT 1," +
                "xp REAL NOT NULL DEFAULT 0," +
                "prestige INTEGER NOT NULL DEFAULT 0," +
                "total_earned REAL NOT NULL DEFAULT 0," +
                "joined_at INTEGER NOT NULL," +
                "last_active INTEGER NOT NULL," +
                "UNIQUE(player_uuid, job_id))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_jobs_player ON jobs_players(player_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_jobs_job ON jobs_players(job_id)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS jobs_placed_blocks (" +
                "world TEXT NOT NULL," +
                "x INTEGER NOT NULL," +
                "y INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "placed_at INTEGER NOT NULL," +
                "PRIMARY KEY (world, x, y, z))");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS jobs_income_tracking (" +
                "player_uuid TEXT NOT NULL," +
                "job_id TEXT NOT NULL," +
                "hour_key TEXT NOT NULL," +
                "earned REAL NOT NULL DEFAULT 0," +
                "PRIMARY KEY (player_uuid, job_id, hour_key))");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS jobs_quests (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "quest_id TEXT NOT NULL," +
                "job_id TEXT NOT NULL," +
                "description TEXT NOT NULL," +
                "target_type TEXT NOT NULL," +
                "target_material TEXT," +
                "target_amount INTEGER NOT NULL," +
                "current_progress INTEGER NOT NULL DEFAULT 0," +
                "reward_money REAL NOT NULL DEFAULT 0," +
                "reward_xp REAL NOT NULL DEFAULT 0," +
                "assigned_date TEXT NOT NULL," +
                "completed INTEGER NOT NULL DEFAULT 0," +
                "completed_at INTEGER)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quests_player ON jobs_quests(player_uuid, assigned_date)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS jobs_quest_definitions (" +
                "quest_id TEXT PRIMARY KEY," +
                "job_id TEXT NOT NULL," +
                "description TEXT NOT NULL," +
                "action_type TEXT NOT NULL," +
                "target_material TEXT," +
                "target_amount INTEGER NOT NULL," +
                "reward_money REAL NOT NULL DEFAULT 0," +
                "reward_xp REAL NOT NULL DEFAULT 0," +
                "weight INTEGER NOT NULL DEFAULT 1," +
                "enabled INTEGER NOT NULL DEFAULT 1)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS jobs_boosts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "job_id TEXT," +
                "multiplier REAL NOT NULL," +
                "started_by TEXT," +
                "started_at INTEGER NOT NULL," +
                "ends_at INTEGER NOT NULL," +
                "reason TEXT)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS jobs_explored_chunks (" +
                "player_uuid TEXT NOT NULL," +
                "world TEXT NOT NULL," +
                "chunk_x INTEGER NOT NULL," +
                "chunk_z INTEGER NOT NULL," +
                "discovered_at INTEGER NOT NULL," +
                "PRIMARY KEY (player_uuid, world, chunk_x, chunk_z))");

            // Black Market
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS blackmarket_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "item_id TEXT NOT NULL," +
                "quantity INTEGER NOT NULL," +
                "price_each REAL NOT NULL," +
                "total_price REAL NOT NULL," +
                "purchased_at INTEGER NOT NULL)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bm_player ON blackmarket_log(player_uuid)");

            // Trade
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS trade_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player1_uuid TEXT NOT NULL," +
                "player1_name TEXT NOT NULL," +
                "player2_uuid TEXT NOT NULL," +
                "player2_name TEXT NOT NULL," +
                "player1_items TEXT," +
                "player1_money REAL NOT NULL DEFAULT 0," +
                "player2_items TEXT," +
                "player2_money REAL NOT NULL DEFAULT 0," +
                "completed_at INTEGER NOT NULL)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_trade_p1 ON trade_log(player1_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_trade_p2 ON trade_log(player2_uuid)");

            // Lottery
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS lottery_instances (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "tier_id TEXT NOT NULL," +
                "pot REAL NOT NULL," +
                "started_at INTEGER NOT NULL," +
                "draw_at INTEGER NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "winner_uuid TEXT," +
                "winner_name TEXT)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS lottery_tickets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "instance_id INTEGER NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "ticket_count INTEGER NOT NULL," +
                "total_paid REAL NOT NULL," +
                "purchased_at INTEGER NOT NULL," +
                "refunded INTEGER NOT NULL DEFAULT 0," +
                "FOREIGN KEY (instance_id) REFERENCES lottery_instances(id))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lottery_instance ON lottery_tickets(instance_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lottery_player ON lottery_tickets(player_uuid)");

            // Bounty
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS bounties (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "target_uuid TEXT NOT NULL," +
                "target_name TEXT NOT NULL," +
                "setter_uuid TEXT NOT NULL," +
                "setter_name TEXT NOT NULL," +
                "amount REAL NOT NULL," +
                "anonymous INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "expires_at INTEGER NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "claimed_by_uuid TEXT," +
                "claimed_by_name TEXT," +
                "claimed_at INTEGER)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bounty_target ON bounties(target_uuid, status)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bounty_status ON bounties(status)");

            // Economy audit
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS economy_audit_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "action_type TEXT NOT NULL," +
                "amount REAL NOT NULL," +
                "balance_after REAL," +
                "source TEXT," +
                "related_uuid TEXT," +
                "created_at INTEGER NOT NULL)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_player ON economy_audit_log(player_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_type ON economy_audit_log(action_type)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_time ON economy_audit_log(created_at)");
        }
    }

    public Connection getConnection() { return connection; }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to close database: " + e.getMessage());
        }
    }
}
