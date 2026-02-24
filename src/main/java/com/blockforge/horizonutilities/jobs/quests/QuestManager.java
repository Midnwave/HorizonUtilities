package com.blockforge.horizonutilities.jobs.quests;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.economy.EconomyAuditLog;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.JobAction;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * High-level quest logic: assignment, progress tracking, and completion.
 * <p>
 * To avoid a circular compile-time dependency with {@code JobManager}
 * (which creates this class), the two callbacks that would otherwise require
 * importing {@code JobManager} are injected as lambdas via
 * {@link #setJobManagerCallbacks}.  {@code JobManager} calls that setter
 * immediately after constructing {@code QuestManager}.
 */
public class QuestManager {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // -------------------------------------------------------------------------
    // Functional callbacks injected by JobManager to break the circular dep
    // -------------------------------------------------------------------------

    /** Returns the list of JobPlayer records for a UUID. */
    private Function<UUID, List<JobPlayer>> playerJobsSupplier;

    /** Returns a Job by id (nullable). */
    private Function<String, Job> jobSupplier;

    /** Returns the configured daily quest count. */
    private java.util.function.IntSupplier questDailyCountSupplier;

    /** Grants quest XP to a player's job: (player, jobId, xpAmount). */
    private TriConsumer<Player, String, Double> questXpGranter;

    // -------------------------------------------------------------------------

    private final HorizonUtilitiesPlugin plugin;
    private final QuestStorageManager storage;
    private final EconomyAuditLog auditLog;

    public QuestManager(HorizonUtilitiesPlugin plugin, QuestStorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.auditLog = new EconomyAuditLog(plugin);
    }

    /**
     * Called by {@code JobManager} immediately after constructing this instance.
     * All four lambdas delegate back into JobManager without importing it.
     */
    public void setJobManagerCallbacks(
            Function<UUID, List<JobPlayer>> playerJobsSupplier,
            Function<String, Job> jobSupplier,
            java.util.function.IntSupplier questDailyCountSupplier,
            TriConsumer<Player, String, Double> questXpGranter) {
        this.playerJobsSupplier = playerJobsSupplier;
        this.jobSupplier = jobSupplier;
        this.questDailyCountSupplier = questDailyCountSupplier;
        this.questXpGranter = questXpGranter;
    }

    /** Simple three-argument consumer functional interface. */
    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns today's quests for the player, assigning new ones if none exist
     * yet. This is the primary entry point for {@code /jobs quests}.
     */
    public List<DailyQuest> getOrAssignQuests(Player player) {
        String today = today();
        List<DailyQuest> existing = getDailyQuests(player.getUniqueId(), today);
        if (!existing.isEmpty()) return existing;

        List<Job> playerJobs = resolvePlayerJobs(player.getUniqueId());
        if (playerJobs.isEmpty()) return Collections.emptyList();

        assignDailyQuests(player.getUniqueId(), today, playerJobs);
        return getDailyQuests(player.getUniqueId(), today);
    }

    /**
     * Loads persisted quests for the given player and date.
     */
    public List<DailyQuest> getDailyQuests(UUID playerUuid, String date) {
        return storage.getActiveQuests(playerUuid, date);
    }

    /**
     * Randomly selects quests from each player job's pool (weighted) and
     * inserts them into the DB.
     *
     * @param playerUuid  the player to assign quests to
     * @param date        the date string (yyyy-MM-dd)
     * @param playerJobs  the jobs the player is enrolled in
     */
    public void assignDailyQuests(UUID playerUuid, String date, List<Job> playerJobs) {
        int dailyCount = questDailyCountSupplier != null
                ? questDailyCountSupplier.getAsInt()
                : 3;

        List<QuestDefinition> pool = new ArrayList<>();
        for (Job job : playerJobs) {
            pool.addAll(job.getQuestPool().stream()
                    .filter(QuestDefinition::isEnabled)
                    .collect(Collectors.toList()));
            // Also load DB-stored definitions for this job
            pool.addAll(storage.getAllQuestDefinitions(job.getId()).stream()
                    .filter(QuestDefinition::isEnabled)
                    .collect(Collectors.toList()));
        }

        if (pool.isEmpty()) return;

        List<QuestDefinition> selected = weightedSample(pool, dailyCount);

        for (QuestDefinition def : selected) {
            DailyQuest quest = new DailyQuest();
            quest.setPlayerUuid(playerUuid);
            quest.setQuestId(def.getQuestId());
            quest.setJobId(def.getJobId());
            quest.setDescription(def.getDescription());
            quest.setTargetType(def.getActionType());
            quest.setTargetMaterial(def.getTargetMaterial());
            quest.setTargetAmount(def.getTargetAmount());
            quest.setRewardMoney(def.getRewardMoney());
            quest.setRewardXp(def.getRewardXp());
            quest.setAssignedDate(date);
            quest.setCompleted(false);
            int id = storage.insertQuest(quest);
            quest.setId(id);
        }
    }

    /**
     * Increments progress on any active quest matching the given action and
     * material. Completes quests that reach their target.
     *
     * @param playerUuid player's UUID
     * @param action     job action performed
     * @param material   the material/entity involved
     * @param amount     how much to add to progress
     */
    public void updateProgress(UUID playerUuid, JobAction action, String material, int amount) {
        String today = today();
        Player player = plugin.getServer().getPlayer(playerUuid);
        List<DailyQuest> quests = getDailyQuests(playerUuid, today);
        for (DailyQuest quest : quests) {
            if (quest.isCompleted()) continue;
            if (quest.getTargetType() != action) continue;
            // null targetMaterial on a quest means "any material"
            if (quest.getTargetMaterial() != null
                    && !quest.getTargetMaterial().equalsIgnoreCase(material)) continue;

            int newProgress = Math.min(quest.getCurrentProgress() + amount, quest.getTargetAmount());
            quest.setCurrentProgress(newProgress);
            storage.updateProgress(quest.getId(), newProgress);

            if (newProgress >= quest.getTargetAmount() && player != null) {
                completeQuest(quest, player);
            }
        }
    }

    /**
     * Pays rewards and marks a quest as complete.
     */
    public void completeQuest(DailyQuest quest, Player player) {
        long now = System.currentTimeMillis();
        storage.completeQuest(quest.getId(), now);
        quest.setCompleted(true);
        quest.setCompletedAt(now);

        // Pay money reward
        if (quest.getRewardMoney() > 0 && plugin.getVaultHook().isAvailable()) {
            plugin.getVaultHook().deposit(player, quest.getRewardMoney());
            auditLog.log(player.getUniqueId(), player.getName(),
                    EconomyAuditLog.JOB_INCOME, quest.getRewardMoney(),
                    plugin.getVaultHook().getBalance(player),
                    "quest:" + quest.getQuestId(), null);
        }

        // Grant XP to the job via the injected callback
        if (quest.getRewardXp() > 0 && questXpGranter != null) {
            questXpGranter.accept(player, quest.getJobId(), quest.getRewardXp());
        }

        // Notify player
        player.sendMessage(Component.text("[Jobs] Quest Complete! ", NamedTextColor.GOLD)
                .append(Component.text(quest.getDescription(), NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("  Reward: ", NamedTextColor.GRAY))
                .append(Component.text(plugin.getVaultHook().format(quest.getRewardMoney()), NamedTextColor.GREEN))
                .append(Component.text(" + ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.0f XP", quest.getRewardXp()), NamedTextColor.AQUA)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Job> resolvePlayerJobs(UUID playerUuid) {
        if (playerJobsSupplier == null || jobSupplier == null) return Collections.emptyList();
        return playerJobsSupplier.apply(playerUuid).stream()
                .map(jp -> jobSupplier.apply(jp.getJobId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }

    /** Weighted random sampling without replacement. */
    private List<QuestDefinition> weightedSample(List<QuestDefinition> pool, int count) {
        List<QuestDefinition> remaining = new ArrayList<>(pool);
        List<QuestDefinition> selected = new ArrayList<>();
        Random rng = new Random();

        while (selected.size() < count && !remaining.isEmpty()) {
            int totalWeight = remaining.stream().mapToInt(QuestDefinition::getWeight).sum();
            int roll = rng.nextInt(Math.max(1, totalWeight));
            int cumulative = 0;
            QuestDefinition chosen = null;
            for (QuestDefinition def : remaining) {
                cumulative += def.getWeight();
                if (roll < cumulative) {
                    chosen = def;
                    break;
                }
            }
            if (chosen == null) chosen = remaining.get(0);
            selected.add(chosen);
            remaining.remove(chosen);
        }
        return selected;
    }
}
