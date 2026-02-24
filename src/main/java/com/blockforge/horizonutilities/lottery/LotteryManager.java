package com.blockforge.horizonutilities.lottery;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.economy.EconomyAuditLog;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Central manager for all lottery tiers.
 */
public class LotteryManager {

    private final HorizonUtilitiesPlugin plugin;
    private final LotteryConfig lotteryConfig;
    private final LotteryStorageManager storage;
    private final EconomyAuditLog auditLog;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** tierId -> tier configuration */
    private final Map<String, LotteryTierConfig> tierConfigs = new LinkedHashMap<>();

    /** tierId -> active instance (in-memory cache, stays in sync with DB) */
    private final Map<String, LotteryInstance> activeInstances = new HashMap<>();

    public LotteryManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.lotteryConfig = new LotteryConfig(plugin);
        this.storage = new LotteryStorageManager(plugin);
        this.auditLog = new EconomyAuditLog(plugin);
    }

    // -------------------------------------------------------------------------
    // Startup
    // -------------------------------------------------------------------------

    public void loadConfig() {
        lotteryConfig.load();
        tierConfigs.clear();
        for (LotteryTierConfig cfg : lotteryConfig.getAllTiers()) {
            tierConfigs.put(cfg.getTierId(), cfg);
        }
    }

    public void loadActiveInstances() {
        activeInstances.clear();
        List<LotteryInstance> instances = storage.getAllActiveInstances(tierConfigs);
        for (LotteryInstance inst : instances) {
            activeInstances.put(inst.getTierId(), inst);
        }
        plugin.getLogger().info("[Lottery] Loaded " + instances.size() + " active instance(s) from DB.");
    }

    public void reload() {
        loadConfig();
        loadActiveInstances();
    }

    // -------------------------------------------------------------------------
    // Instance management
    // -------------------------------------------------------------------------

    /**
     * Creates a new DB instance for the given tier and caches it.
     */
    public LotteryInstance startNewInstance(String tierId) {
        LotteryTierConfig config = tierConfigs.get(tierId);
        if (config == null) {
            plugin.getLogger().warning("[Lottery] Unknown tier: " + tierId);
            return null;
        }

        long now = System.currentTimeMillis();
        long drawAt = calculateDrawTime(config);

        int id = storage.createInstance(tierId, config.getStartingPot(), now, drawAt);
        if (id < 0) {
            plugin.getLogger().warning("[Lottery] Failed to create instance for tier: " + tierId);
            return null;
        }

        LotteryInstance inst = new LotteryInstance(
                id, tierId, config.getStartingPot(), now, drawAt, "ACTIVE",
                null, null, config);
        activeInstances.put(tierId, inst);
        return inst;
    }

    /** Ensures every configured tier has an active instance, starting one if needed. */
    public void ensureAllTiersActive() {
        for (String tierId : tierConfigs.keySet()) {
            if (!activeInstances.containsKey(tierId)) {
                startNewInstance(tierId);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Ticket purchasing
    // -------------------------------------------------------------------------

    /**
     * Attempts to purchase {@code count} tickets for the given tier.
     * Returns true on success.
     */
    public boolean buyTickets(Player player, String tierId, int count) {
        LotteryTierConfig config = tierConfigs.get(tierId);
        if (config == null) {
            player.sendMessage(miniMessage.deserialize("<red>Unknown lottery tier: <white>" + tierId));
            return false;
        }

        LotteryInstance inst = activeInstances.get(tierId);
        if (inst == null || !inst.isActive()) {
            player.sendMessage(miniMessage.deserialize("<red>There is no active lottery for that tier right now."));
            return false;
        }

        // Check max tickets per player
        int alreadyOwned = storage.getTicketCount(inst.getId(), player.getUniqueId());
        if (alreadyOwned + count > config.getMaxTicketsPerPlayer()) {
            int remaining = config.getMaxTicketsPerPlayer() - alreadyOwned;
            player.sendMessage(miniMessage.deserialize(
                    "<red>You can only buy <white>" + remaining +
                    "</white> more ticket(s) for this lottery (max <white>" +
                    config.getMaxTicketsPerPlayer() + "</white> per player)."));
            return false;
        }

        double totalCost = config.getTicketPrice() * count;
        if (!plugin.getVaultHook().has(player, totalCost)) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>You don't have enough money. " + count + " ticket(s) cost <yellow>" +
                    plugin.getVaultHook().format(totalCost) + "</yellow>."));
            return false;
        }

        if (!plugin.getVaultHook().withdraw(player, totalCost)) {
            player.sendMessage(miniMessage.deserialize("<red>Payment failed — please try again."));
            return false;
        }

        double balanceAfter = plugin.getVaultHook().getBalance(player);

        // Persist ticket
        storage.addTicket(inst.getId(), player.getUniqueId(), player.getName(), count, totalCost);

        // Update pot in memory and DB
        inst.addTicketPurchase(totalCost);
        storage.updateInstancePot(inst.getId(), inst.getCurrentPot());

        // Audit log
        auditLog.log(player.getUniqueId(), player.getName(),
                EconomyAuditLog.LOTTERY_TICKET,
                -totalCost, balanceAfter,
                "lottery:" + tierId, null);

        // Feedback
        player.sendMessage(miniMessage.deserialize(
                "<green>You bought <white>" + count + "</white> ticket(s) for <gold>" +
                config.getDisplayName() + "</gold>! Current pot: <yellow>" +
                plugin.getVaultHook().format(inst.getCurrentPot()) + "</yellow>."));

        // Broadcast
        if (config.isBroadcastBuy()) {
            Bukkit.broadcast(miniMessage.deserialize(
                    "<gold>[Lottery] <white>" + player.getName() + "</white> bought <white>" +
                    count + "</white> ticket(s) for <gold>" + config.getDisplayName() + "</gold>!"));
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    /**
     * Performs the draw for the given tier. Should be called from the main thread
     * (or a synchronized Bukkit task) so that Vault deposits are safe.
     */
    public void drawLottery(String tierId) {
        LotteryInstance inst = activeInstances.get(tierId);
        if (inst == null || !inst.isActive()) return;

        LotteryTierConfig config = inst.getTierConfig();
        int distinctPlayers = storage.getDistinctPlayerCount(inst.getId());

        // Check minimum players
        if (distinctPlayers < config.getMinPlayers()) {
            // Refund and announce cancellation
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                    () -> storage.refundTickets(inst.getId()));
            storage.updateInstanceStatus(inst.getId(), "CANCELLED", null, null);
            inst.markCancelled();
            activeInstances.remove(tierId);

            Bukkit.broadcast(miniMessage.deserialize(
                    "<gold>[Lottery] <red>" + config.getDisplayName() +
                    "</red> was cancelled — not enough players. All tickets have been refunded."));

            // Start fresh
            startNewInstance(tierId);
            return;
        }

        // Weighted random draw
        List<LotteryStorageManager.TicketEntry> tickets = storage.getAllTickets(inst.getId());
        UUID winnerUuid = weightedRandom(tickets);
        if (winnerUuid == null) {
            plugin.getLogger().warning("[Lottery] Draw produced no winner for tier " + tierId);
            return;
        }

        String winnerName = tickets.stream()
                .filter(t -> t.uuid().equals(winnerUuid))
                .findFirst()
                .map(LotteryStorageManager.TicketEntry::playerName)
                .orElse("Unknown");

        double pot = inst.getCurrentPot();

        // Pay the winner
        plugin.getVaultHook().deposit(Bukkit.getOfflinePlayer(winnerUuid), pot);
        double balanceAfter = plugin.getVaultHook().getBalance(Bukkit.getOfflinePlayer(winnerUuid));

        // Audit
        auditLog.log(winnerUuid, winnerName, EconomyAuditLog.LOTTERY_WIN,
                pot, balanceAfter, "lottery:" + tierId, null);

        // Persist
        storage.updateInstanceStatus(inst.getId(), "DRAWN", winnerUuid, winnerName);
        inst.markDrawn(winnerUuid, winnerName);
        activeInstances.remove(tierId);

        // Broadcast
        if (config.isBroadcastDraw()) {
            Bukkit.broadcast(miniMessage.deserialize(
                    "<gold>[Lottery] <green>" + winnerName + "</green> won the <gold>" +
                    config.getDisplayName() + "</gold> lottery and took home <yellow>" +
                    plugin.getVaultHook().format(pot) + "</yellow>! Congratulations!"));
        }

        // Message the winner directly if online
        Player onlineWinner = Bukkit.getPlayer(winnerUuid);
        if (onlineWinner != null) {
            onlineWinner.sendMessage(miniMessage.deserialize(
                    "<gold>[Lottery] <green>You won the <gold>" + config.getDisplayName() +
                    "</gold> lottery! <yellow>" + plugin.getVaultHook().format(pot) +
                    "</yellow> has been deposited into your account!"));
        }

        // Start the next instance
        startNewInstance(tierId);
    }

    // -------------------------------------------------------------------------
    // Admin helpers
    // -------------------------------------------------------------------------

    public void cancelLottery(String tierId) {
        LotteryInstance inst = activeInstances.get(tierId);
        if (inst == null) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> storage.refundTickets(inst.getId()));
        storage.updateInstanceStatus(inst.getId(), "CANCELLED", null, null);
        inst.markCancelled();
        activeInstances.remove(tierId);
        Bukkit.broadcast(miniMessage.deserialize(
                "<gold>[Lottery] <red>" + inst.getTierConfig().getDisplayName() +
                "</red> has been cancelled by an admin. All tickets have been refunded."));
        startNewInstance(tierId);
    }

    public void setPot(String tierId, double amount) {
        LotteryInstance inst = activeInstances.get(tierId);
        if (inst == null) return;
        inst.setCurrentPot(amount);
        storage.updateInstancePot(inst.getId(), amount);
    }

    // -------------------------------------------------------------------------
    // Info / history
    // -------------------------------------------------------------------------

    public String getLotteryInfo(String tierId) {
        LotteryTierConfig config = tierConfigs.get(tierId);
        if (config == null) return "Unknown tier: " + tierId;

        LotteryInstance inst = activeInstances.get(tierId);
        if (inst == null) return "No active lottery for tier: " + tierId;

        int totalTickets = storage.getTotalTickets(inst.getId());
        int players = storage.getDistinctPlayerCount(inst.getId());

        return String.format(
                "[%s] Pot: %s | Ticket price: %s | Your draw in: %s | Players: %d | Tickets sold: %d",
                miniMessage.stripTags(config.getDisplayName()),
                plugin.getVaultHook().format(inst.getCurrentPot()),
                plugin.getVaultHook().format(config.getTicketPrice()),
                inst.getTimeUntilDraw(),
                players, totalTickets);
    }

    public List<LotteryStorageManager.WinnerRecord> getRecentHistory(int count) {
        return storage.getRecentWinners(count, tierConfigs);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Map<String, LotteryTierConfig> getTierConfigs() {
        return Collections.unmodifiableMap(tierConfigs);
    }

    public Map<String, LotteryInstance> getActiveInstances() {
        return Collections.unmodifiableMap(activeInstances);
    }

    public LotteryInstance getActiveInstance(String tierId) {
        return activeInstances.get(tierId);
    }

    public LotteryStorageManager getStorage() { return storage; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Performs a weighted random selection: players with more tickets have
     * proportionally higher odds.
     */
    private UUID weightedRandom(List<LotteryStorageManager.TicketEntry> tickets) {
        if (tickets.isEmpty()) return null;
        int total = tickets.stream().mapToInt(LotteryStorageManager.TicketEntry::ticketCount).sum();
        if (total <= 0) return null;
        int pick = new Random().nextInt(total);
        int cumulative = 0;
        for (LotteryStorageManager.TicketEntry entry : tickets) {
            cumulative += entry.ticketCount();
            if (pick < cumulative) return entry.uuid();
        }
        return tickets.get(tickets.size() - 1).uuid();
    }

    /**
     * Calculates the Unix epoch millisecond timestamp for the next draw of a tier.
     */
    private long calculateDrawTime(LotteryTierConfig config) {
        ZoneId zone = ZoneId.systemDefault();
        LocalTime drawTime = LocalTime.parse(config.getDrawTime(),
                DateTimeFormatter.ofPattern("H:mm"));

        if (config.isDaily()) {
            ZonedDateTime today = ZonedDateTime.now(zone).with(drawTime);
            if (!today.isAfter(ZonedDateTime.now(zone))) {
                today = today.plusDays(1);
            }
            return today.toInstant().toEpochMilli();
        } else {
            // Weekly
            DayOfWeek targetDay;
            try {
                targetDay = DayOfWeek.valueOf(
                        config.getDrawDay() != null ? config.getDrawDay().toUpperCase() : "SATURDAY");
            } catch (IllegalArgumentException e) {
                targetDay = DayOfWeek.SATURDAY;
            }
            ZonedDateTime next = ZonedDateTime.now(zone)
                    .with(TemporalAdjusters.nextOrSame(targetDay))
                    .with(drawTime);
            if (!next.isAfter(ZonedDateTime.now(zone))) {
                next = next.with(TemporalAdjusters.next(targetDay));
            }
            return next.toInstant().toEpochMilli();
        }
    }
}
