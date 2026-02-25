package com.blockforge.horizonutilities.economy;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class EconomyAuditLog {

    public static final String JOB_INCOME = "JOB_INCOME";
    public static final String TAX_PAID = "TAX_PAID";
    public static final String TAX_RECEIVED = "TAX_RECEIVED";
    public static final String BM_PURCHASE = "BM_PURCHASE";
    public static final String BOUNTY_SET = "BOUNTY_SET";
    public static final String BOUNTY_CLAIM = "BOUNTY_CLAIM";
    public static final String LOTTERY_TICKET = "LOTTERY_TICKET";
    public static final String LOTTERY_WIN = "LOTTERY_WIN";
    public static final String TRADE_MONEY = "TRADE_MONEY";
    public static final String AH_SALE = "AH_SALE";
    public static final String AH_PURCHASE = "AH_PURCHASE";
    public static final String AH_FEE = "AH_FEE";
    public static final String AH_TAX = "AH_TAX";

    private final HorizonUtilitiesPlugin plugin;

    public EconomyAuditLog(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void log(UUID playerUuid, String playerName, String actionType,
                    double amount, Double balanceAfter, String source, UUID relatedUuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                     "INSERT INTO economy_audit_log (player_uuid, player_name, action_type, amount, balance_after, source, related_uuid, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, actionType);
                ps.setDouble(4, amount);
                if (balanceAfter != null) ps.setDouble(5, balanceAfter); else ps.setNull(5, Types.REAL);
                ps.setString(6, source);
                ps.setString(7, relatedUuid != null ? relatedUuid.toString() : null);
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to write audit log", e);
            }
        });
    }
}
