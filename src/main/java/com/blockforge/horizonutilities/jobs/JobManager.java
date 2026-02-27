package com.blockforge.horizonutilities.jobs;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.economy.EconomyAuditLog;
import com.blockforge.horizonutilities.hooks.GPFRHook;
import com.blockforge.horizonutilities.jobs.antiexploit.*;
import com.blockforge.horizonutilities.jobs.boost.BoostManager;
import com.blockforge.horizonutilities.jobs.config.JobConfigLoader;
import com.blockforge.horizonutilities.jobs.config.JobsConfig;
import com.blockforge.horizonutilities.jobs.leaderboard.JobLeaderboard;
import com.blockforge.horizonutilities.jobs.ui.JobBossBarManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Central coordinator for the jobs system. Wires together all subsystems:
 * config, storage, anti-exploit, boosts, quests, and leaderboard.
 * <p>
 * Obtain via {@link HorizonUtilitiesPlugin#getJobManager()}.
 */
public class JobManager {

    private final HorizonUtilitiesPlugin plugin;

    // Config & storage
    private final JobsConfig config;
    private final JobStorageManager storage;

    // Job definitions (loaded from YAML)
    private Map<String, Job> jobDefinitions = new LinkedHashMap<>();

    // Per-player in-memory cache: UUID -> list of JobPlayer records
    private final Map<UUID, List<JobPlayer>> playerJobs = new ConcurrentHashMap<>();

    // Subsystems
    private final BlockTracker blockTracker;
    private final SpawnerTracker spawnerTracker;
    private final AreaFarmingDetector areaFarmingDetector;
    private final CooldownManager cooldownManager;
    private final IncomeCapManager incomeCapManager;
    private final BoostManager boostManager;
    private final JobLeaderboard leaderboard;
    private final EconomyAuditLog auditLog;
    private final JobBossBarManager bossBarManager;

    // Action bar accumulation: [totalMoney, totalXp], resets 3s after last action
    private final Map<UUID, double[]> actionBarAccum = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> actionBarResetTasks = new ConcurrentHashMap<>();
    private static final long ACTION_BAR_RESET_TICKS = 60L; // 3 seconds

    public JobManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;

        // Config
        config = new JobsConfig(plugin);
        config.load();

        // Storage
        storage = new JobStorageManager(plugin);

        // Anti-exploit
        blockTracker = new BlockTracker(plugin);
        spawnerTracker = new SpawnerTracker(plugin);
        areaFarmingDetector = new AreaFarmingDetector(
                config.getAreaFarmingMaxActions(),
                config.getAreaFarmingRadius(),
                config.getAreaFarmingTimeframeMs());
        cooldownManager = new CooldownManager();
        incomeCapManager = new IncomeCapManager(storage);

        // Boosts
        boostManager = new BoostManager(plugin);
        boostManager.loadFromDb();

        // Leaderboard
        leaderboard = new JobLeaderboard(storage);

        // Audit log
        auditLog = new EconomyAuditLog(plugin);

        // Boss bar
        bossBarManager = new JobBossBarManager(plugin);

        // Load job YAML definitions
        loadJobDefinitions();
    }

    // -------------------------------------------------------------------------
    // Startup / reload
    // -------------------------------------------------------------------------

    /**
     * Scans the {@code jobs/} folder and loads every job YAML.
     */
    public void loadJobDefinitions() {
        jobDefinitions = new LinkedHashMap<>(JobConfigLoader.loadAll(plugin));
        plugin.getLogger().info("[Jobs] Loaded " + jobDefinitions.size() + " job definition(s).");
    }

    // -------------------------------------------------------------------------
    // Player data lifecycle
    // -------------------------------------------------------------------------

    /** Loads (async-safe) all job data for a player into the cache. */
    public void loadPlayerData(UUID uuid) {
        List<JobPlayer> jobs = storage.loadPlayerJobs(uuid);
        playerJobs.put(uuid, new ArrayList<>(jobs));
    }

    /** Saves all cached job data for a player to the DB and removes from cache. */
    public void savePlayerData(UUID uuid) {
        List<JobPlayer> jobs = playerJobs.remove(uuid);
        if (jobs == null) return;
        for (JobPlayer jp : jobs) {
            storage.savePlayerJob(jp);
        }
        // Clean up action bar accumulator and boss bar
        actionBarAccum.remove(uuid);
        BukkitTask task = actionBarResetTasks.remove(uuid);
        if (task != null) task.cancel();
        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.getUniqueId().equals(uuid))
                .findFirst()
                .ifPresent(bossBarManager::cleanup);
    }

    /** Saves without removing from cache (for periodic flush). */
    public void flushPlayerData(UUID uuid) {
        List<JobPlayer> jobs = playerJobs.get(uuid);
        if (jobs == null) return;
        for (JobPlayer jp : jobs) {
            storage.savePlayerJob(jp);
        }
    }

    // -------------------------------------------------------------------------
    // Join / leave
    // -------------------------------------------------------------------------

    /**
     * Attempts to enroll a player in a job.
     *
     * @return true if successful, false if already in job / at capacity / missing permission
     */
    public boolean joinJob(Player player, String jobId) {
        Job job = jobDefinitions.get(jobId.toLowerCase(Locale.ROOT));
        if (job == null) {
            player.sendMessage(Component.text("[Jobs] Unknown job: " + jobId, NamedTextColor.RED));
            return false;
        }

        List<JobPlayer> current = getPlayerJobs(player.getUniqueId());

        // Already enrolled?
        boolean alreadyIn = current.stream()
                .anyMatch(jp -> jp.getJobId().equalsIgnoreCase(jobId));
        if (alreadyIn) {
            player.sendMessage(Component.text("[Jobs] You are already in the " + job.getDisplayName() + " job.", NamedTextColor.RED));
            return false;
        }

        // Capacity check
        if (current.size() >= config.getMaxConcurrentJobs()) {
            player.sendMessage(Component.text(
                    "[Jobs] You can only be in " + config.getMaxConcurrentJobs() + " job(s) at once.", NamedTextColor.RED));
            return false;
        }

        // Permission check (optional per-job node)
        if (!player.hasPermission("horizonutilities.jobs.join")
                && !player.hasPermission("horizonutilities.jobs." + jobId + ".join")) {
            player.sendMessage(Component.text("[Jobs] You don't have permission to join this job.", NamedTextColor.RED));
            return false;
        }

        long now = System.currentTimeMillis();
        JobPlayer jp = new JobPlayer(
                player.getUniqueId(), player.getName(), jobId.toLowerCase(Locale.ROOT),
                1, 0, 0, 0, now, now);
        current.add(jp);
        playerJobs.put(player.getUniqueId(), current);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> storage.savePlayerJob(jp));

        player.sendMessage(Component.text("[Jobs] You joined the ", NamedTextColor.GREEN)
                .append(Component.text(job.getDisplayName(), NamedTextColor.GOLD))
                .append(Component.text(" job!", NamedTextColor.GREEN)));
        return true;
    }

    /**
     * Removes a player from a job.
     *
     * @return true if removed, false if not enrolled
     */
    public boolean leaveJob(Player player, String jobId) {
        List<JobPlayer> current = getPlayerJobs(player.getUniqueId());
        Optional<JobPlayer> match = current.stream()
                .filter(jp -> jp.getJobId().equalsIgnoreCase(jobId))
                .findFirst();

        if (match.isEmpty()) {
            player.sendMessage(Component.text("[Jobs] You are not in the " + jobId + " job.", NamedTextColor.RED));
            return false;
        }

        current.remove(match.get());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> storage.deletePlayerJob(player.getUniqueId(), jobId));

        Job job = jobDefinitions.get(jobId.toLowerCase(Locale.ROOT));
        String name = job != null ? job.getDisplayName() : jobId;
        player.sendMessage(Component.text("[Jobs] You left the ", NamedTextColor.YELLOW)
                .append(Component.text(name, NamedTextColor.GOLD))
                .append(Component.text(" job.", NamedTextColor.YELLOW)));
        return true;
    }

    // -------------------------------------------------------------------------
    // Core earning logic
    // -------------------------------------------------------------------------

    /**
     * Called by every job listener when a player performs a tracked action.
     * Iterates over all of the player's enrolled jobs, finds matching entries,
     * applies multipliers, anti-exploit checks, and pays out.
     *
     * @param player   the acting player
     * @param action   the job action type
     * @param material the material or entity name (upper-case)
     */
    public void processAction(Player player, JobAction action, String material) {
        if (action == JobAction.PLACE) return; // placement never pays

        List<JobPlayer> jobs = getPlayerJobs(player.getUniqueId());
        if (jobs.isEmpty()) return;

        // Anti-exploit: cooldown check (if configured)
        if (config.getActionCooldownMs() > 0
                && cooldownManager.isOnCooldown(player.getUniqueId(), action, config.getActionCooldownMs())) {
            return;
        }

        // Anti-exploit: area farming
        Location loc = player.getLocation();
        if (config.isBlockTracking()
                && areaFarmingDetector.isExploiting(player.getUniqueId(), loc, action)) {
            return;
        }

        double sessionMoney = 0;
        double sessionXp    = 0;
        Job    lastJob      = null;
        JobPlayer lastJp    = null;

        for (JobPlayer jp : jobs) {
            Job job = jobDefinitions.get(jp.getJobId());
            if (job == null) continue;

            JobActionEntry entry = job.getEntry(action, material);
            if (entry == null) continue;

            double baseMoney = entry.getMoney();
            double baseXp    = entry.getXp();
            if (baseMoney <= 0 && baseXp <= 0) continue;

            // Level multiplier
            double levelMult = JobLevelCalculator.getIncomeMultiplier(
                    jp.getLevel(), jp.getPrestige(),
                    config.getPrestigeMultiplier(),
                    job.getPerks());

            // Boost multiplier
            double boostMult = boostManager.getActiveMultiplier(jp.getJobId());

            double totalMult = levelMult * boostMult;

            double moneyEarned = baseMoney * totalMult;
            double xpEarned    = baseXp   * totalMult;

            // Income cap check
            double cap = job.getHourlyIncomeCap() >= 0
                    ? job.getHourlyIncomeCap()
                    : (config.isIncomeCapEnabled() ? config.getIncomeCapDefault() : -1);

            if (cap >= 0) {
                double remaining = incomeCapManager.getRemainingCap(player.getUniqueId(), jp.getJobId(), cap);
                if (remaining <= 0) continue;
                moneyEarned = Math.min(moneyEarned, remaining);
            }

            // Tax via GPFR
            double taxTaken = 0;
            GPFRHook gpfr = plugin.getGpfrHook();
            if (config.isTaxEnabled() && gpfr != null && gpfr.isAvailable()
                    && gpfr.isJobTaxEnabled(loc)
                    && !player.hasPermission(config.getTaxExemptPermission())) {
                double rate = gpfr.getJobTaxRate(loc);
                taxTaken = moneyEarned * rate;
                moneyEarned -= taxTaken;
            }

            // Pay
            if (moneyEarned > 0 && plugin.getVaultHook().isAvailable()) {
                plugin.getVaultHook().deposit(player, moneyEarned);
                incomeCapManager.trackEarning(player.getUniqueId(), jp.getJobId(), moneyEarned);
                jp.addEarned(moneyEarned);
                sessionMoney += moneyEarned;

                // Audit log
                auditLog.log(player.getUniqueId(), player.getName(),
                        EconomyAuditLog.JOB_INCOME, moneyEarned,
                        plugin.getVaultHook().getBalance(player),
                        jp.getJobId() + ":" + action.name() + ":" + material, null);

                if (taxTaken > 0) {
                    auditLog.log(player.getUniqueId(), player.getName(),
                            EconomyAuditLog.TAX_PAID, -taxTaken, null,
                            "job-tax:" + jp.getJobId(), null);
                    plugin.getTaxManager().depositTaxToClaimOwner(loc, taxTaken, player, jp.getJobId());
                    if (config.isTaxNotifyPlayer()) {
                        player.sendMessage(Component.text(
                                "[Jobs] Tax: " + plugin.getVaultHook().format(taxTaken) + " deducted.",
                                NamedTextColor.GRAY));
                    }
                }
            }

            // Grant XP and handle level-up
            if (xpEarned > 0) {
                jp.addXp(xpEarned);
                sessionXp += xpEarned;
                checkLevelUp(jp, player, job);
                // Sync to AuraSkills
                if (plugin.getAuraSkillsManager() != null) {
                    plugin.getAuraSkillsManager().syncXp(player, jp.getJobId(), xpEarned);
                }
            }

            lastJob = job;
            lastJp  = jp;
            jp.touch();

        }

        // Update accumulated action bar display and boss bar
        if ((sessionMoney > 0 || sessionXp > 0) && lastJob != null) {
            double[] accum = actionBarAccum.computeIfAbsent(player.getUniqueId(), k -> new double[2]);
            accum[0] += sessionMoney;
            accum[1] += sessionXp;
            final double displayMoney = accum[0];
            final double displayXp    = accum[1];
            final Job displayJob      = lastJob;
            final JobPlayer displayJp = lastJp;
            player.sendActionBar(buildJobActionBar(displayMoney, displayXp, displayJob, displayJp));
            scheduleActionBarReset(player.getUniqueId());
            // Update XP progress boss bar
            if (sessionXp > 0) {
                bossBarManager.showProgress(player, displayJp, displayJob);
            }
        }

        // Record for area farming detector
        areaFarmingDetector.recordAction(player.getUniqueId(), loc, action);

        // Record cooldown
        if (config.getActionCooldownMs() > 0) {
            cooldownManager.recordAction(player.getUniqueId(), action);
        }
    }

    // -------------------------------------------------------------------------
    // Level-up / prestige
    // -------------------------------------------------------------------------

    private void checkLevelUp(JobPlayer jp, Player player, Job job) {
        int newLevel = JobLevelCalculator.getLevelForXp(jp.getXp(), config.getXpBase(), config.getXpExponent());
        int maxLevel = job.getMaxLevel() > 0 ? job.getMaxLevel() : config.getMaxLevel();
        newLevel = Math.min(newLevel, maxLevel);
        if (newLevel > jp.getLevel()) {
            jp.setLevel(newLevel);
            levelUp(jp, player);
        }
    }

    /** Notifies the player of a level-up. */
    public void levelUp(JobPlayer jp, Player player) {
        player.sendMessage(Component.text("[Jobs] ", NamedTextColor.GOLD)
                .append(Component.text("Level up! ", NamedTextColor.YELLOW))
                .append(Component.text(getJobDisplayName(jp.getJobId()), NamedTextColor.AQUA))
                .append(Component.text(" level " + jp.getLevel(), NamedTextColor.GREEN)));

        // AuraSkills milestone rewards
        if (plugin.getAuraSkillsManager() != null) {
            plugin.getAuraSkillsManager().checkMilestone(player, jp.getLevel());
        }

        int maxLevel = getMaxLevelForJob(jp.getJobId());
        if (jp.getLevel() >= maxLevel && jp.getPrestige() < config.getMaxPrestige()) {
            player.sendMessage(Component.text(
                    "[Jobs] You've reached max level! Use /jobs prestige " + jp.getJobId() + " to prestige.",
                    NamedTextColor.GOLD));
        }
    }

    /**
     * Resets a player's job level to 1, increments prestige, and notifies them.
     *
     * @return true if prestiged, false if conditions not met
     */
    public boolean prestige(Player player, String jobId) {
        List<JobPlayer> jobs = getPlayerJobs(player.getUniqueId());
        Optional<JobPlayer> match = jobs.stream()
                .filter(jp -> jp.getJobId().equalsIgnoreCase(jobId))
                .findFirst();

        if (match.isEmpty()) {
            player.sendMessage(Component.text("[Jobs] You are not in the " + jobId + " job.", NamedTextColor.RED));
            return false;
        }

        JobPlayer jp = match.get();
        int maxLevel = getMaxLevelForJob(jobId);

        if (jp.getLevel() < maxLevel) {
            player.sendMessage(Component.text(
                    "[Jobs] You must reach level " + maxLevel + " before prestiging.", NamedTextColor.RED));
            return false;
        }

        if (jp.getPrestige() >= config.getMaxPrestige()) {
            player.sendMessage(Component.text("[Jobs] You have already reached max prestige.", NamedTextColor.RED));
            return false;
        }

        jp.setPrestige(jp.getPrestige() + 1);
        jp.setLevel(1);
        jp.setXp(0);
        jp.touch();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> storage.savePlayerJob(jp));

        player.sendMessage(Component.text("[Jobs] Prestiged! ", NamedTextColor.GOLD)
                .append(Component.text(getJobDisplayName(jobId), NamedTextColor.AQUA))
                .append(Component.text(" prestige " + jp.getPrestige(), NamedTextColor.LIGHT_PURPLE)));
        return true;
    }

    // -------------------------------------------------------------------------
    // Quest XP helper (called via lambda from QuestManager)
    // -------------------------------------------------------------------------

    /**
     * Grants bonus XP from a completed quest directly to the player's job record
     * and triggers a level-up check. Called via the QuestManager callback.
     */
    public void grantQuestXp(Player player, String jobId, double xp) {
        getPlayerJobs(player.getUniqueId()).stream()
                .filter(jp -> jp.getJobId().equalsIgnoreCase(jobId))
                .findFirst()
                .ifPresent(jp -> {
                    jp.addXp(xp);
                    Job job = jobDefinitions.get(jp.getJobId());
                    if (job != null) checkLevelUp(jp, player, job);
                    jp.touch();
                });
    }

    // -------------------------------------------------------------------------
    // Action bar accumulation helpers
    // -------------------------------------------------------------------------

    private Component buildJobActionBar(double totalMoney, double totalXp, Job job, JobPlayer jp) {
        return Component.text(job.getDisplayName() + " ", NamedTextColor.GOLD)
                .append(Component.text("Lv." + jp.getLevel() + " ", NamedTextColor.AQUA))
                .append(Component.text("+" + plugin.getVaultHook().format(totalMoney), NamedTextColor.GREEN))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("+" + String.format("%.0f", totalXp) + "xp", NamedTextColor.YELLOW));
    }

    private void scheduleActionBarReset(UUID uuid) {
        BukkitTask old = actionBarResetTasks.remove(uuid);
        if (old != null) old.cancel();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            actionBarAccum.remove(uuid);
            actionBarResetTasks.remove(uuid);
        }, ACTION_BAR_RESET_TICKS);
        actionBarResetTasks.put(uuid, task);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public List<JobPlayer> getPlayerJobs(UUID uuid) {
        return playerJobs.getOrDefault(uuid, Collections.emptyList());
    }

    public Job getJob(String jobId) {
        return jobDefinitions.get(jobId.toLowerCase(Locale.ROOT));
    }

    public Collection<Job> getAllJobs() {
        return Collections.unmodifiableCollection(jobDefinitions.values());
    }

    // -------------------------------------------------------------------------
    // Subsystem getters
    // -------------------------------------------------------------------------

    public JobsConfig getConfig()                   { return config; }
    public JobStorageManager getStorage()           { return storage; }
    public JobBossBarManager getBossBarManager()    { return bossBarManager; }
    public BlockTracker getBlockTracker()           { return blockTracker; }
    public SpawnerTracker getSpawnerTracker()       { return spawnerTracker; }
    public AreaFarmingDetector getAreaFarmingDetector() { return areaFarmingDetector; }
    public CooldownManager getCooldownManager()     { return cooldownManager; }
    public IncomeCapManager getIncomeCapManager()   { return incomeCapManager; }
    public BoostManager getBoostManager()           { return boostManager; }
    public JobLeaderboard getLeaderboard()          { return leaderboard; }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String getJobDisplayName(String jobId) {
        Job job = jobDefinitions.get(jobId.toLowerCase(Locale.ROOT));
        return job != null ? job.getDisplayName() : jobId;
    }

    private int getMaxLevelForJob(String jobId) {
        Job job = jobDefinitions.get(jobId.toLowerCase(Locale.ROOT));
        if (job != null && job.getMaxLevel() > 0) return job.getMaxLevel();
        return config.getMaxLevel();
    }
}
