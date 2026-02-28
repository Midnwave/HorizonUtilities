package com.blockforge.horizonutilities.jobs.quests.daily;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.JobAction;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the daily quest lifecycle: assignment, progress tracking,
 * completion, and reward distribution.
 *
 * Dynamic difficulty scaling: quest amounts and rewards scale based on
 * the player's job level, prestige, and (optionally) AuraSkills level.
 */
public class DailyQuestManager {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HorizonUtilitiesPlugin plugin;
    private final DailyQuestConfig config;
    private final DailyQuestStorage storage;

    /** In-memory cache: player UUID -> today's active quests */
    private final Map<UUID, List<ActiveQuest>> playerQuests = new ConcurrentHashMap<>();

    public DailyQuestManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.config = new DailyQuestConfig(plugin);
        this.storage = new DailyQuestStorage(plugin);
        config.load();

        // Schedule daily purge of old quests (keep 30 days)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> storage.purgeOldQuests(30), 20L * 60, 20L * 60 * 60 * 6); // 6 hours
    }

    /**
     * Called when a player joins — load or assign today's quests.
     */
    public void onPlayerJoin(UUID uuid) {
        if (!config.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String today = todayDate();
            List<ActiveQuest> loaded = storage.loadPlayerQuests(uuid, today);

            List<ActiveQuest> finalQuests = loaded.isEmpty()
                    ? assignDailyQuests(uuid, today)
                    : loaded;

            playerQuests.put(uuid, finalQuests);

            // Notify player of their quests
            if (config.isNotifyOnAssign()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> notifyQuestsAssigned(player, finalQuests));
                }
            }
        });
    }

    /**
     * Called when a player quits — flush from cache.
     */
    public void onPlayerQuit(UUID uuid) {
        playerQuests.remove(uuid);
    }

    /**
     * Called from JobManager.processAction() to track quest progress.
     */
    public void trackAction(Player player, JobAction action, String material, String jobId) {
        if (!config.isEnabled()) return;

        List<ActiveQuest> quests = playerQuests.get(player.getUniqueId());
        if (quests == null || quests.isEmpty()) return;

        for (ActiveQuest quest : quests) {
            // Must match the job and the action/material
            if (!quest.getJobId().equalsIgnoreCase(jobId)) continue;
            if (!quest.matches(action, material)) continue;

            boolean justCompleted = quest.addProgress(1);

            // Save progress async
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    storage.updateProgress(quest.getDbId(), quest.getCurrentProgress(),
                            quest.isCompleted(), quest.getCompletedAt()));

            if (justCompleted) {
                onQuestCompleted(player, quest);
            }
        }
    }

    /**
     * Get a player's active quests for today.
     */
    public List<ActiveQuest> getPlayerQuests(UUID uuid) {
        List<ActiveQuest> quests = playerQuests.get(uuid);
        return quests != null ? Collections.unmodifiableList(quests) : Collections.emptyList();
    }

    public DailyQuestConfig getConfig() { return config; }

    public void reload() {
        config.load();
        // Re-assign quests for all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            onPlayerJoin(p.getUniqueId());
        }
    }

    public int getTotalCompleted(UUID uuid) {
        return storage.getTotalCompleted(uuid);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Assign daily quests to a player based on their active jobs.
     * Uses weighted random selection with dynamic difficulty scaling.
     */
    private List<ActiveQuest> assignDailyQuests(UUID uuid, String today) {
        List<ActiveQuest> assigned = new ArrayList<>();
        List<JobPlayer> playerJobs = plugin.getJobManager().getPlayerJobs(uuid);

        if (playerJobs.isEmpty()) return assigned;

        List<QuestDefinition> pool = config.getDefinitions().stream()
                .filter(QuestDefinition::isEnabled)
                .toList();

        if (pool.isEmpty()) return assigned;

        int questsToAssign = config.getQuestsPerDay();
        Random rng = new Random();

        // Try to assign quests spread across the player's jobs
        List<QuestDefinition> eligible = new ArrayList<>();
        for (QuestDefinition def : pool) {
            for (JobPlayer jp : playerJobs) {
                if (def.getJobId().equalsIgnoreCase(jp.getJobId())) {
                    eligible.add(def);
                    break;
                }
            }
        }

        // Fallback: if no job-specific quests, use all quests
        if (eligible.isEmpty()) eligible = new ArrayList<>(pool);

        // Weighted random selection
        Set<String> usedIds = new HashSet<>();
        for (int i = 0; i < questsToAssign && !eligible.isEmpty(); i++) {
            QuestDefinition def = weightedRandom(eligible, rng);
            if (def == null) break;
            if (usedIds.contains(def.getQuestId())) {
                // Try again, up to 5 times
                boolean found = false;
                for (int retry = 0; retry < 5; retry++) {
                    def = weightedRandom(eligible, rng);
                    if (def != null && !usedIds.contains(def.getQuestId())) {
                        found = true;
                        break;
                    }
                }
                if (!found) continue;
            }
            usedIds.add(def.getQuestId());

            // Scale difficulty based on player level/prestige
            JobPlayer jp = findJobPlayer(playerJobs, def.getJobId());
            int scaledAmount = scaleAmount(def.getBaseAmount(), jp);
            double scaledMoney = scaleReward(def.getBaseMoneyReward(), jp);
            double scaledXp = scaleReward(def.getBaseXpReward(), jp);

            ActiveQuest quest = new ActiveQuest(
                    -1, uuid, def.getQuestId(), def.getJobId(),
                    def.getDescription(), def.getActionType(), def.getTargetMaterial(),
                    scaledAmount, 0, scaledMoney, scaledXp,
                    today, false, null
            );

            int dbId = storage.insertQuest(quest);
            if (dbId > 0) {
                // Re-create with the actual DB id
                quest = new ActiveQuest(
                        dbId, uuid, def.getQuestId(), def.getJobId(),
                        def.getDescription(), def.getActionType(), def.getTargetMaterial(),
                        scaledAmount, 0, scaledMoney, scaledXp,
                        today, false, null
                );
                assigned.add(quest);
            }
        }

        return assigned;
    }

    /**
     * Scale the quest target amount up based on player level and prestige.
     * Higher level = harder quests (more to do), but also more rewarding.
     */
    private int scaleAmount(int baseAmount, JobPlayer jp) {
        if (jp == null) return baseAmount;
        double levelFactor = 1.0 + (jp.getLevel() * config.getLevelScalingFactor());
        double prestigeFactor = 1.0 + (jp.getPrestige() * config.getPrestigeScalingFactor());
        return Math.max(1, (int) Math.round(baseAmount * levelFactor * prestigeFactor));
    }

    /**
     * Scale the reward up based on player level and prestige.
     * Rewards scale slightly more than difficulty so higher levels feel rewarding.
     */
    private double scaleReward(double baseReward, JobPlayer jp) {
        if (jp == null) return baseReward;
        double levelFactor = 1.0 + (jp.getLevel() * config.getLevelScalingFactor() * 1.5);
        double prestigeFactor = 1.0 + (jp.getPrestige() * config.getPrestigeScalingFactor() * 1.5);
        return Math.round(baseReward * levelFactor * prestigeFactor * 100.0) / 100.0;
    }

    private JobPlayer findJobPlayer(List<JobPlayer> playerJobs, String jobId) {
        for (JobPlayer jp : playerJobs) {
            if (jp.getJobId().equalsIgnoreCase(jobId)) return jp;
        }
        return null;
    }

    private QuestDefinition weightedRandom(List<QuestDefinition> pool, Random rng) {
        int totalWeight = pool.stream().mapToInt(QuestDefinition::getWeight).sum();
        if (totalWeight <= 0) return pool.isEmpty() ? null : pool.get(0);

        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (QuestDefinition def : pool) {
            cumulative += def.getWeight();
            if (roll < cumulative) return def;
        }
        return pool.get(pool.size() - 1);
    }

    private void onQuestCompleted(Player player, ActiveQuest quest) {
        if (config.isAutoClaimRewards()) {
            // Grant rewards
            if (quest.getRewardMoney() > 0 && plugin.getVaultHook().isAvailable()) {
                plugin.getVaultHook().deposit(player, quest.getRewardMoney());
            }
            if (quest.getRewardXp() > 0) {
                plugin.getJobManager().grantQuestXp(player, quest.getJobId(), quest.getRewardXp());
            }
        }

        if (config.isNotifyOnComplete()) {
            player.sendMessage(Component.text("[Quests] ", NamedTextColor.GOLD)
                    .append(Component.text("Quest completed: ", NamedTextColor.GREEN))
                    .append(Component.text(quest.getDescription(), NamedTextColor.WHITE)));

            String moneyStr = plugin.getVaultHook().isAvailable()
                    ? plugin.getVaultHook().format(quest.getRewardMoney())
                    : String.format("$%.2f", quest.getRewardMoney());
            player.sendMessage(Component.text("[Quests] ", NamedTextColor.GOLD)
                    .append(Component.text("Rewards: ", NamedTextColor.GRAY))
                    .append(Component.text(moneyStr, NamedTextColor.GREEN))
                    .append(Component.text(" + ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.0f XP", quest.getRewardXp()), NamedTextColor.AQUA)));

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            player.showTitle(Title.title(
                    Component.text("Quest Complete!", NamedTextColor.GOLD),
                    Component.text(quest.getDescription(), NamedTextColor.GREEN),
                    Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofSeconds(2),
                            java.time.Duration.ofMillis(500)
                    )
            ));
        }

        // Chance to earn gems on quest completion
        if (plugin.getGemsManager() != null) {
            plugin.getGemsManager().onQuestCompleted(player);
        }

        // Check if all quests for today are done
        List<ActiveQuest> allQuests = playerQuests.get(player.getUniqueId());
        if (allQuests != null && allQuests.stream().allMatch(ActiveQuest::isCompleted)) {
            player.sendMessage(Component.text("[Quests] ", NamedTextColor.GOLD)
                    .append(Component.text("All daily quests completed! Great work!", NamedTextColor.YELLOW)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    private void notifyQuestsAssigned(Player player, List<ActiveQuest> quests) {
        if (quests.isEmpty()) return;

        long incomplete = quests.stream().filter(q -> !q.isCompleted()).count();
        if (incomplete == 0) return; // All done already

        player.sendMessage(Component.text("[Quests] ", NamedTextColor.GOLD)
                .append(Component.text("You have " + incomplete + " daily quest" +
                        (incomplete > 1 ? "s" : "") + "! Use ", NamedTextColor.YELLOW))
                .append(Component.text("/jobs quests", NamedTextColor.AQUA))
                .append(Component.text(" to view them.", NamedTextColor.YELLOW)));
    }

    private String todayDate() {
        return LocalDate.now().format(DATE_FMT);
    }
}
