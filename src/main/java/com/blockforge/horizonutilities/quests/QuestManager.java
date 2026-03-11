package com.blockforge.horizonutilities.quests;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.config.MessagesManager;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import com.blockforge.horizonutilities.quests.antiexploit.QuestAntiExploit;
import com.blockforge.horizonutilities.quests.generation.QuestGenerator;
import com.blockforge.horizonutilities.quests.generation.TierCalculator;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import com.blockforge.horizonutilities.quests.rewards.QuestRewardHandler;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;
import com.blockforge.horizonutilities.quests.tracking.QuestTracker;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central coordinator for the quest system.
 * Manages lifecycle, generation, tracking, rewards, and reset scheduling.
 */
public class QuestManager {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HorizonUtilitiesPlugin plugin;
    private final QuestConfig config;
    private final QuestStorage storage;
    private final QuestAntiExploit antiExploit;
    private final QuestRewardHandler rewardHandler;
    private final QuestTracker tracker;
    private final QuestBossBarManager bossBarManager;
    private final Logger logger;

    /** In-memory cache: player UUID -> all active quests */
    private final Map<UUID, List<ActiveQuest>> playerQuests = new ConcurrentHashMap<>();

    /** Cached tiers: player UUID -> tier value */
    private final Map<UUID, Double> playerTiers = new ConcurrentHashMap<>();

    private int resetTaskId = -1;

    public QuestManager(HorizonUtilitiesPlugin plugin, QuestConfig config, QuestStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.logger = plugin.getLogger();
        this.antiExploit = new QuestAntiExploit(config, storage);
        this.rewardHandler = new QuestRewardHandler(plugin, storage, antiExploit);
        this.tracker = new QuestTracker(this);
        this.bossBarManager = new QuestBossBarManager(plugin, config);

        scheduleResetCheck();
        schedulePurge();
    }

    // ========== LIFECYCLE ==========

    /**
     * Called when a player joins — load or generate quests.
     */
    public void onPlayerJoin(UUID uuid) {
        if (!config.isEnabled()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Load all existing quests
            List<ActiveQuest> loaded = storage.loadAllQuests(uuid);

            // Calculate tier
            Player player = Bukkit.getPlayer(uuid);
            double tier;
            if (player != null) {
                tier = TierCalculator.calculateTier(player, plugin, config);
                storage.saveCachedTier(uuid, tier);
            } else {
                tier = Math.max(0, storage.getCachedTier(uuid));
            }
            playerTiers.put(uuid, tier);

            // Generate missing categories for current period
            String dailyKey = getDailyPeriodKey();
            String weeklyKey = getWeeklyPeriodKey();
            List<String> recentChallenges = storage.getRecentChallenges(uuid, config.getChallengeRepeatBlockDays());

            List<ActiveQuest> newQuests = new ArrayList<>();

            // Job dailies — one set per active job
            if (player != null) {
                List<JobPlayer> jobs = plugin.getJobManager().getPlayerJobs(uuid);
                for (JobPlayer jp : jobs) {
                    if (!hasQuestsInCache(uuid, loaded, QuestCategory.JOB_DAILY, dailyKey, jp.getJobId())) {
                        List<ActiveQuest> generated = QuestGenerator.generateQuests(
                            uuid, dailyKey, QuestCategory.JOB_DAILY, tier, config,
                            jp.getJobId(), List.of());
                        newQuests.addAll(generated);
                    }
                }
            }

            // General dailies
            if (!hasQuestsInCache(uuid, loaded, QuestCategory.GENERAL_DAILY, dailyKey, null)) {
                newQuests.addAll(QuestGenerator.generateQuests(
                    uuid, dailyKey, QuestCategory.GENERAL_DAILY, tier, config, null, List.of()));
            }

            // Weeklies
            if (!hasQuestsInCache(uuid, loaded, QuestCategory.WEEKLY, weeklyKey, null)) {
                newQuests.addAll(QuestGenerator.generateQuests(
                    uuid, weeklyKey, QuestCategory.WEEKLY, tier, config, null, List.of()));
            }

            // Challenges
            if (!hasQuestsInCache(uuid, loaded, QuestCategory.CHALLENGE, weeklyKey, null)) {
                newQuests.addAll(QuestGenerator.generateQuests(
                    uuid, weeklyKey, QuestCategory.CHALLENGE, tier, config, null, recentChallenges));
            }

            // Save new quests to DB
            for (ActiveQuest quest : newQuests) {
                int dbId = storage.saveQuest(quest);
                if (dbId > 0) {
                    // Reload from DB to get proper IDs
                }
            }

            // Reload everything from DB now that new quests are saved
            List<ActiveQuest> allQuests = storage.loadAllQuests(uuid);
            playerQuests.put(uuid, allQuests);

            // Notify player
            if (config.isNotifyOnAssign() && player != null && player.isOnline()) {
                long incomplete = allQuests.stream().filter(q -> !q.isCompleted()).count();
                if (incomplete > 0 || !newQuests.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> notifyQuestsAssigned(player, allQuests));
                }
            }
        });
    }

    /**
     * Called when a player quits — flush from cache.
     */
    public void onPlayerQuit(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) bossBarManager.removeAll(player);
        playerQuests.remove(uuid);
        playerTiers.remove(uuid);
    }

    // ========== TRACKING ==========

    /**
     * Track a quest action from any source (jobs, bukkit events).
     */
    public void trackAction(Player player, QuestActionType actionType, String material) {
        if (!config.isEnabled()) return;
        tracker.trackAction(player, actionType, material);
    }

    /**
     * Track a quest action with a custom amount (for batch operations like shift-click crafting).
     */
    public void trackAction(Player player, QuestActionType actionType, String material, int amount) {
        if (!config.isEnabled()) return;
        tracker.trackAction(player, actionType, material, amount);
    }

    /**
     * Called by QuestTracker when progress is made on a quest.
     * Handles saving and completion.
     */
    public void onQuestProgress(Player player, ActiveQuest quest, boolean justCompleted) {
        // Save progress async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            storage.updateQuestProgress(quest));

        // Update boss bar
        if (justCompleted) {
            bossBarManager.showCompleted(player, quest);
        } else {
            bossBarManager.showProgress(player, quest);
        }

        if (justCompleted) {
            onQuestCompleted(player, quest);
        } else if (config.isNotifyOnStepComplete()) {
            ActiveQuest.ActiveStep step = quest.getCurrentStep();
            if (step != null && quest.getCurrentStepIndex() > 0) {
                ActiveQuest.ActiveStep prevStep = quest.getSteps().get(quest.getCurrentStepIndex() - 1);
                if (prevStep != null && prevStep.isCompleted()) {
                    MessagesManager msg = plugin.getMessagesManager();
                    msg.send(player, "quest-step-complete",
                        Placeholder.parsed("description", step.getDescription()));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                }
            }
        }
    }

    // ========== QUEST ACCESS ==========

    /**
     * Get a player's cached active quests.
     */
    public List<ActiveQuest> getPlayerQuests(UUID uuid) {
        List<ActiveQuest> quests = playerQuests.get(uuid);
        return quests != null ? Collections.unmodifiableList(quests) : Collections.emptyList();
    }

    /**
     * Get quests filtered by category.
     */
    public List<ActiveQuest> getPlayerQuests(UUID uuid, QuestCategory category) {
        return getPlayerQuests(uuid).stream()
            .filter(q -> q.getCategory() == category)
            .toList();
    }

    /**
     * Get the player's tier (cached).
     */
    public double getPlayerTier(UUID uuid) {
        return playerTiers.getOrDefault(uuid, 0.0);
    }

    /**
     * Get total completed quests all-time.
     */
    public int getTotalCompleted(UUID uuid) {
        return storage.getTotalCompleted(uuid);
    }

    public QuestConfig getConfig() { return config; }
    public QuestStorage getStorage() { return storage; }
    public HorizonUtilitiesPlugin getPlugin() { return plugin; }

    // ========== ADMIN ==========

    /**
     * Reset all quests for a player and regenerate.
     */
    public void adminResetPlayer(UUID uuid) {
        storage.deleteAllQuests(uuid);
        playerQuests.remove(uuid);
        playerTiers.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            onPlayerJoin(uuid);
        }
    }

    /**
     * Override a player's tier (debug).
     */
    public void adminSetTier(UUID uuid, double tier) {
        playerTiers.put(uuid, tier);
        storage.saveCachedTier(uuid, tier);
    }

    /**
     * Generate a debug quest with custom difficulty.
     */
    public ActiveQuest adminGenerateQuest(UUID uuid, QuestCategory category, double difficulty) {
        ActiveQuest quest = QuestGenerator.generateDebugQuest(uuid, category, difficulty, config);
        if (quest == null) return null;

        int dbId = storage.saveQuest(quest);
        if (dbId > 0) {
            // Reload from DB
            List<ActiveQuest> all = storage.loadAllQuests(uuid);
            playerQuests.put(uuid, all);

            // Find the one we just created
            return all.stream()
                .filter(q -> q.getDbId() == dbId)
                .findFirst()
                .orElse(quest);
        }
        return null;
    }

    /**
     * Reload configuration and regenerate for all online players.
     */
    public void reload() {
        config.load();
        playerQuests.clear();
        playerTiers.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            onPlayerJoin(p.getUniqueId());
        }
    }

    // ========== INTERNAL ==========

    private void onQuestCompleted(Player player, ActiveQuest quest) {
        MessagesManager msg = plugin.getMessagesManager();

        // Record challenge completion
        if (quest.getCategory() == QuestCategory.CHALLENGE) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                storage.recordChallengeCompletion(player.getUniqueId(), quest.getTemplateId()));
        }

        // Grant rewards
        if (config.isAutoClaimRewards()) {
            rewardHandler.grantRewards(player, quest);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                storage.updateQuestProgress(quest));
        }

        // Notifications
        if (config.isNotifyOnComplete()) {
            String questName = quest.getQuestName();
            msg.send(player, "quest-completed", Placeholder.parsed("quest", questName));

            RewardDefinition rewards = quest.getScaledRewards();
            if (rewards != null && rewards.hasAnyReward()) {
                StringBuilder rewardStr = new StringBuilder();
                if (rewards.getMoney() > 0) {
                    String moneyStr = plugin.getVaultHook().isAvailable()
                        ? plugin.getVaultHook().format(rewards.getMoney())
                        : String.format("$%.2f", rewards.getMoney());
                    rewardStr.append(moneyStr);
                }
                if (rewards.getXpLevels() > 0) {
                    if (!rewardStr.isEmpty()) rewardStr.append(", ");
                    rewardStr.append(rewards.getXpLevels()).append(" XP levels");
                }
                if (rewards.getGems() > 0) {
                    if (!rewardStr.isEmpty()) rewardStr.append(", ");
                    rewardStr.append(rewards.getGems()).append(" gems");
                }
                if (rewards.getJobXp() > 0) {
                    if (!rewardStr.isEmpty()) rewardStr.append(", ");
                    rewardStr.append(String.format("%.0f", rewards.getJobXp())).append(" job XP");
                }

                msg.send(player, "quest-rewards", Placeholder.parsed("rewards", rewardStr.toString()));
            }

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            MiniMessage mm = MiniMessage.miniMessage();
            player.showTitle(Title.title(
                mm.deserialize(msg.getRaw("quest-completed-title")),
                mm.deserialize(msg.getRaw("quest-completed-subtitle")
                    .replace("<quest>", questName)),
                Title.Times.times(
                    java.time.Duration.ofMillis(200),
                    java.time.Duration.ofSeconds(2),
                    java.time.Duration.ofMillis(500)
                )
            ));
        }

        // Check if all quests in the category are done
        List<ActiveQuest> catQuests = getPlayerQuests(player.getUniqueId(), quest.getCategory());
        if (catQuests.stream().allMatch(ActiveQuest::isCompleted)) {
            String catDoneKey = quest.getCategory().isDaily() ? "quest-all-daily-done" : "quest-all-weekly-done";
            if (quest.getCategory() == QuestCategory.CHALLENGE) catDoneKey = "quest-all-challenges-done";
            msg.send(player, catDoneKey);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    private void notifyQuestsAssigned(Player player, List<ActiveQuest> quests) {
        long incomplete = quests.stream().filter(q -> !q.isCompleted()).count();
        if (incomplete == 0) return;

        MessagesManager msg = plugin.getMessagesManager();
        msg.send(player, "quest-assigned",
            Placeholder.parsed("count", String.valueOf(incomplete)),
            Placeholder.parsed("plural", incomplete > 1 ? "s" : ""));
    }

    private boolean hasQuestsInCache(UUID playerUuid, List<ActiveQuest> loaded, QuestCategory category,
                                      String periodKey, String jobId) {
        for (ActiveQuest q : loaded) {
            if (q.getCategory() == category && q.getPeriodKey().equals(periodKey)) {
                if (jobId == null || jobId.equalsIgnoreCase(q.getJobId())) {
                    return true;
                }
            }
        }
        // Also check DB directly for accuracy
        return storage.hasQuestsForPeriod(playerUuid, category, periodKey);
    }

    // ========== PERIOD KEYS ==========

    public String getDailyPeriodKey() {
        LocalDate date = LocalDate.now();
        // If before reset time, use yesterday's key
        String[] parts = config.getDailyResetTime().split(":");
        int hour = parts.length >= 1 ? Integer.parseInt(parts[0]) : 0;
        int minute = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
        if (LocalTime.now().isBefore(LocalTime.of(hour, minute))) {
            date = date.minusDays(1);
        }
        return date.format(DATE_FMT);
    }

    public String getWeeklyPeriodKey() {
        LocalDate date = LocalDate.now();
        DayOfWeek resetDay;
        try {
            resetDay = DayOfWeek.valueOf(config.getWeeklyResetDay().toUpperCase());
        } catch (IllegalArgumentException e) {
            resetDay = DayOfWeek.MONDAY;
        }

        // If today is before reset day this week, use last week
        if (date.getDayOfWeek().getValue() < resetDay.getValue()) {
            date = date.minusWeeks(1);
        }

        int weekNum = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = date.get(WeekFields.ISO.weekBasedYear());
        return year + "-W" + String.format("%02d", weekNum);
    }

    public String getTimeUntilDailyReset() {
        String[] parts = config.getDailyResetTime().split(":");
        int hour = parts.length >= 1 ? Integer.parseInt(parts[0]) : 0;
        int minute = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
        LocalTime resetTime = LocalTime.of(hour, minute);
        LocalTime now = LocalTime.now();

        long seconds;
        if (now.isBefore(resetTime)) {
            seconds = java.time.Duration.between(now, resetTime).getSeconds();
        } else {
            seconds = java.time.Duration.between(now, resetTime).getSeconds() + 86400;
        }

        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }

    public String getTimeUntilWeeklyReset() {
        DayOfWeek resetDay;
        try {
            resetDay = DayOfWeek.valueOf(config.getWeeklyResetDay().toUpperCase());
        } catch (IllegalArgumentException e) {
            resetDay = DayOfWeek.MONDAY;
        }

        LocalDate today = LocalDate.now();
        int daysUntil = resetDay.getValue() - today.getDayOfWeek().getValue();
        if (daysUntil <= 0) daysUntil += 7;

        return daysUntil + "d";
    }

    // ========== SCHEDULING ==========

    private void scheduleResetCheck() {
        // Check every minute if we need to reset
        resetTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // The period key approach means quests auto-expire.
            // On the next join after reset, new quests are generated.
            // For online players, we regenerate when the period key changes.
            String newDailyKey = getDailyPeriodKey();
            String newWeeklyKey = getWeeklyPeriodKey();

            for (UUID uuid : playerQuests.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;

                List<ActiveQuest> quests = playerQuests.get(uuid);
                if (quests == null) continue;

                boolean needsRefresh = false;
                for (ActiveQuest q : quests) {
                    if (q.getCategory().isDaily() && !q.getPeriodKey().equals(newDailyKey)) {
                        needsRefresh = true;
                        break;
                    }
                    if (q.getCategory().isWeekly() && !q.getPeriodKey().equals(newWeeklyKey)) {
                        needsRefresh = true;
                        break;
                    }
                }

                if (needsRefresh) {
                    Bukkit.getScheduler().runTask(plugin, () -> onPlayerJoin(uuid));
                }
            }
        }, 20L * 60, 20L * 60).getTaskId(); // every minute
    }

    private void schedulePurge() {
        // Purge old completed quests every 6 hours
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
            () -> storage.purgeOldQuests(30),
            20L * 60, 20L * 60 * 60 * 6);
    }

    public void shutdown() {
        if (resetTaskId != -1) {
            Bukkit.getScheduler().cancelTask(resetTaskId);
        }
        bossBarManager.shutdown();
        playerQuests.clear();
        playerTiers.clear();
    }
}
