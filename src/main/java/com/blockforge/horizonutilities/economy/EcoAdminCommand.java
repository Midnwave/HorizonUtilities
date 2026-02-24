package com.blockforge.horizonutilities.economy;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /eco command for economy administration.
 *
 * Sub-commands (all require horizonutilities.eco.admin):
 *   /eco admin audit <player> [type] [page]  — query audit log
 *   /eco admin stats                         — economy summary
 *   /eco admin reset <player> <feature>      — clear player data for a feature
 */
public class EcoAdminCommand implements CommandExecutor {

    private static final int PAGE_SIZE = 10;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final HorizonUtilitiesPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public EcoAdminCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("horizonutilities.eco.admin")) {
            plugin.getMessagesManager().send(sender, "no-permission");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("admin")) {
            sendUsage(sender);
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "audit" -> handleAudit(sender, args);
            case "stats" -> handleStats(sender);
            case "reset" -> handleReset(sender, args);
            default      -> sendUsage(sender);
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // /eco admin audit <player> [type] [page]
    // -------------------------------------------------------------------------

    private void handleAudit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Usage: /eco admin audit <player> [type] [page]"));
            return;
        }

        String playerName = args[2];
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(playerName);
        UUID uuid = target.getUniqueId();

        String typeFilter = (args.length >= 4) ? args[3].toUpperCase() : null;
        int page = 1;
        if (args.length >= 5) {
            try { page = Math.max(1, Integer.parseInt(args[4])); }
            catch (NumberFormatException ignored) {}
        }

        int offset = (page - 1) * PAGE_SIZE;

        String sql;
        PreparedStatement ps = null;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (typeFilter != null) {
                sql = "SELECT action_type, amount, balance_after, source, created_at " +
                      "FROM economy_audit_log WHERE player_uuid = ? AND action_type = ? " +
                      "ORDER BY created_at DESC LIMIT ? OFFSET ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, uuid.toString());
                ps.setString(2, typeFilter);
                ps.setInt(3, PAGE_SIZE);
                ps.setInt(4, offset);
            } else {
                sql = "SELECT action_type, amount, balance_after, source, created_at " +
                      "FROM economy_audit_log WHERE player_uuid = ? " +
                      "ORDER BY created_at DESC LIMIT ? OFFSET ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, uuid.toString());
                ps.setInt(2, PAGE_SIZE);
                ps.setInt(3, offset);
            }

            try (ResultSet rs = ps.executeQuery()) {
                sender.sendMessage(mm.deserialize(
                        "<gold><bold>=== Audit Log: " + playerName +
                        (typeFilter != null ? " [" + typeFilter + "]" : "") +
                        " (page " + page + ") ===</bold></gold>"));

                boolean any = false;
                while (rs.next()) {
                    any = true;
                    String type      = rs.getString("action_type");
                    double amount    = rs.getDouble("amount");
                    double balAfter  = rs.getDouble("balance_after");
                    String source    = rs.getString("source");
                    long ts          = rs.getLong("created_at");
                    boolean balNull  = rs.wasNull();

                    String amountStr = (amount >= 0 ? "<green>+" : "<red>") +
                            plugin.getVaultHook().format(amount) +
                            (amount >= 0 ? "</green>" : "</red>");
                    String balStr = balNull ? "<gray>N/A" :
                            "<yellow>" + plugin.getVaultHook().format(balAfter);
                    String timeStr = DATE_FMT.format(new Date(ts));

                    sender.sendMessage(mm.deserialize(
                            "<gray>[" + timeStr + "] <white>" + type +
                            " " + amountStr + " <gray>bal=" + balStr +
                            " <gray>src=<white>" + (source != null ? source : "?")));
                }

                if (!any) {
                    sender.sendMessage(mm.deserialize("<yellow>No audit entries found."));
                }
            } finally {
                if (ps != null) try { ps.close(); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to query audit log", e);
            sender.sendMessage(mm.deserialize("<red>Database error — check console."));
        }
    }

    // -------------------------------------------------------------------------
    // /eco admin stats
    // -------------------------------------------------------------------------

    private void handleStats(CommandSender sender) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // Total number of audit entries
            long totalEntries = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM economy_audit_log");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalEntries = rs.getLong(1);
            }

            // Total money deposited (positive amounts only)
            double totalDeposited = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(amount),0) FROM economy_audit_log WHERE amount > 0");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalDeposited = rs.getDouble(1);
            }

            // Total money withdrawn (negative amounts — displayed as positive)
            double totalWithdrawn = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(amount),0) FROM economy_audit_log WHERE amount < 0");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalWithdrawn = rs.getDouble(1);
            }

            // Breakdown by action type
            sender.sendMessage(mm.deserialize("<gold><bold>=== Economy Stats ===</bold></gold>"));
            sender.sendMessage(mm.deserialize(
                    "<gray>Total audit entries: <white>" + totalEntries));
            sender.sendMessage(mm.deserialize(
                    "<gray>Total deposited (all time): <green>" +
                    plugin.getVaultHook().format(totalDeposited)));
            sender.sendMessage(mm.deserialize(
                    "<gray>Total withdrawn (all time): <red>" +
                    plugin.getVaultHook().format(Math.abs(totalWithdrawn))));

            // Per-type summary
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT action_type, COUNT(*) as cnt, COALESCE(SUM(amount),0) as total " +
                    "FROM economy_audit_log GROUP BY action_type ORDER BY cnt DESC");
                 ResultSet rs = ps.executeQuery()) {
                sender.sendMessage(mm.deserialize("<gold>--- By Type ---</gold>"));
                while (rs.next()) {
                    String type   = rs.getString("action_type");
                    long   cnt    = rs.getLong("cnt");
                    double total  = rs.getDouble("total");
                    String color  = total >= 0 ? "<green>" : "<red>";
                    sender.sendMessage(mm.deserialize(
                            "<gray>" + type + ": <white>" + cnt + " entries, " +
                            color + plugin.getVaultHook().format(total)));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to compute eco stats", e);
            sender.sendMessage(mm.deserialize("<red>Database error — check console."));
        }
    }

    // -------------------------------------------------------------------------
    // /eco admin reset <player> <feature>
    // -------------------------------------------------------------------------

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(mm.deserialize(
                    "<red>Usage: /eco admin reset <player> <jobs|bounties|lottery>"));
            return;
        }

        String playerName = args[2];
        String feature    = args[3].toLowerCase();
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(playerName);
        UUID uuid = target.getUniqueId();

        String table;
        String whereColumn;

        switch (feature) {
            case "jobs" -> {
                table       = "jobs_players";
                whereColumn = "player_uuid";
            }
            case "bounties" -> {
                // For bounties, clear bounties SET BY this player (not on them)
                table       = "bounties";
                whereColumn = "setter_uuid";
            }
            case "lottery" -> {
                table       = "lottery_tickets";
                whereColumn = "player_uuid";
            }
            default -> {
                sender.sendMessage(mm.deserialize(
                        "<red>Unknown feature. Valid: jobs, bounties, lottery"));
                return;
            }
        }

        String sql = "DELETE FROM " + table + " WHERE " + whereColumn + " = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            int rows = ps.executeUpdate();
            sender.sendMessage(mm.deserialize(
                    "<green>Cleared " + rows + " " + feature + " records for <red>" + playerName + "</red>."));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to reset player data", e);
            sender.sendMessage(mm.deserialize("<red>Database error — check console."));
        }
    }

    // -------------------------------------------------------------------------
    // Usage
    // -------------------------------------------------------------------------

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<gold><bold>Economy Admin Commands:</bold></gold>"));
        sender.sendMessage(mm.deserialize("<gray>/eco admin audit <player> [type] [page]"));
        sender.sendMessage(mm.deserialize("<gray>/eco admin stats"));
        sender.sendMessage(mm.deserialize("<gray>/eco admin reset <player> <jobs|bounties|lottery>"));
    }
}
