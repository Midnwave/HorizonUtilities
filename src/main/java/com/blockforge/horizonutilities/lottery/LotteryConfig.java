package com.blockforge.horizonutilities.lottery;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads lottery.yml and exposes all configured tiers.
 */
public class LotteryConfig {

    private final HorizonUtilitiesPlugin plugin;
    private final List<LotteryTierConfig> tiers = new ArrayList<>();

    public LotteryConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        tiers.clear();
        File file = new File(plugin.getDataFolder(), "lottery.yml");
        if (!file.exists()) {
            plugin.saveResource("lottery.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection tiersSection = cfg.getConfigurationSection("tiers");
        if (tiersSection == null) {
            plugin.getLogger().warning("[Lottery] No 'tiers' section found in lottery.yml");
            return;
        }

        for (String tierId : tiersSection.getKeys(false)) {
            ConfigurationSection s = tiersSection.getConfigurationSection(tierId);
            if (s == null) continue;

            String displayName    = s.getString("display-name", tierId);
            String schedule       = s.getString("schedule", "DAILY").toUpperCase();
            String drawTime       = s.getString("draw-time", "20:00");
            String drawDay        = s.getString("draw-day", null);
            double startingPot    = s.getDouble("starting-pot", 1000.0);
            double ticketPrice    = s.getDouble("ticket-price", 100.0);
            int maxTickets        = s.getInt("max-tickets-per-player", 10);
            int minPlayers        = s.getInt("min-players", 2);
            boolean broadcastBuy  = s.getBoolean("broadcast-buy", false);
            boolean broadcastDraw = s.getBoolean("broadcast-draw", true);

            tiers.add(new LotteryTierConfig(tierId, displayName, schedule, drawTime,
                    drawDay, startingPot, ticketPrice, maxTickets,
                    minPlayers, broadcastBuy, broadcastDraw));
        }

        plugin.getLogger().info("[Lottery] Loaded " + tiers.size() + " tier(s) from lottery.yml.");
    }

    /** Returns an unmodifiable view of all configured tiers. */
    public List<LotteryTierConfig> getAllTiers() {
        return java.util.Collections.unmodifiableList(tiers);
    }
}
