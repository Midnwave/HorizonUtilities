package com.blockforge.horizonutilities.auction;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PriceHistoryManager {

    private final HorizonUtilitiesPlugin plugin;

    public record PricePoint(String date, double avgPrice, double minPrice, double maxPrice, int saleCount) {}

    public PriceHistoryManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void recordSale(String material, double price) {
        String today = LocalDate.now().toString();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            PreparedStatement check = conn.prepareStatement(
                    "SELECT id, avg_price, min_price, max_price, sale_count FROM ah_price_history WHERE material = ? AND period_date = ?");
            check.setString(1, material);
            check.setString(2, today);
            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                int count = rs.getInt("sale_count") + 1;
                double oldAvg = rs.getDouble("avg_price");
                double newAvg = ((oldAvg * (count - 1)) + price) / count;
                double min = Math.min(rs.getDouble("min_price"), price);
                double max = Math.max(rs.getDouble("max_price"), price);

                PreparedStatement update = conn.prepareStatement(
                        "UPDATE ah_price_history SET avg_price = ?, min_price = ?, max_price = ?, sale_count = ? WHERE id = ?");
                update.setDouble(1, newAvg);
                update.setDouble(2, min);
                update.setDouble(3, max);
                update.setInt(4, count);
                update.setInt(5, rs.getInt("id"));
                update.executeUpdate();
            } else {
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO ah_price_history (material, avg_price, min_price, max_price, sale_count, period_date) VALUES (?, ?, ?, ?, 1, ?)");
                insert.setString(1, material);
                insert.setDouble(2, price);
                insert.setDouble(3, price);
                insert.setDouble(4, price);
                insert.setString(5, today);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to record price history: " + e.getMessage());
        }
    }

    public List<PricePoint> getHistory(String material, int days) {
        List<PricePoint> points = new ArrayList<>();
        String cutoff = LocalDate.now().minusDays(days).toString();
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM ah_price_history WHERE material = ? AND period_date >= ? ORDER BY period_date ASC");
            stmt.setString(1, material);
            stmt.setString(2, cutoff);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                points.add(new PricePoint(
                        rs.getString("period_date"),
                        rs.getDouble("avg_price"),
                        rs.getDouble("min_price"),
                        rs.getDouble("max_price"),
                        rs.getInt("sale_count")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get price history: " + e.getMessage());
        }
        return points;
    }
}
