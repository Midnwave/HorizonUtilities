package com.blockforge.horizonutilities.hooks;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.JobLevelCalculator;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import com.blockforge.horizonutilities.lottery.LotteryInstance;
import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Registers %horizon_...% placeholders with PlaceholderAPI.
 *
 * Supported placeholders:
 *   %horizon_job_name%          – display name of first active job (or "None")
 *   %horizon_job_level%         – level of first active job
 *   %horizon_job_xp%            – current XP of first active job
 *   %horizon_job_xp_required%   – XP required for next level
 *   %horizon_job_progress%      – progress % to next level (e.g. "42%")
 *   %horizon_job_prestige%      – prestige of first active job
 *   %horizon_balance%           – formatted economy balance
 *   %horizon_bounty_count%      – number of active bounties on this player
 *   %horizon_bounty_value%      – total value of bounties on this player
 *   %horizon_lottery_pot_<id>%  – current pot for lottery tier <id>
 */
public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    private final HorizonUtilitiesPlugin plugin;

    public PlaceholderAPIExpansion(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "horizon";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BlockForge";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // stays registered through /papi reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // ---- job placeholders ----
        if (params.startsWith("job_")) {
            return handleJob(player, params.substring(4));
        }

        // ---- balance ----
        if (params.equals("balance")) {
            if (!plugin.getVaultHook().isAvailable()) return "0";
            return plugin.getVaultHook().format(plugin.getVaultHook().getBalance(player));
        }

        // ---- bounty ----
        if (params.equals("bounty_count")) {
            return String.valueOf(plugin.getBountyManager().getActiveBounties(player.getUniqueId()).size());
        }
        if (params.equals("bounty_value")) {
            double val = plugin.getBountyManager().getTotalBountyValue(player.getUniqueId());
            return plugin.getVaultHook().isAvailable()
                    ? plugin.getVaultHook().format(val)
                    : String.format("%.2f", val);
        }

        // ---- lottery pot: %horizon_lottery_pot_bronze% ----
        if (params.startsWith("lottery_pot_")) {
            String tierId = params.substring("lottery_pot_".length());
            LotteryInstance inst = plugin.getLotteryManager().getActiveInstance(tierId);
            if (inst == null) return "0";
            return plugin.getVaultHook().isAvailable()
                    ? plugin.getVaultHook().format(inst.getCurrentPot())
                    : String.format("%.2f", inst.getCurrentPot());
        }

        // ---- quest placeholders ----
        if (params.startsWith("quests_")) {
            return handleQuests(player, params.substring(7));
        }

        // ---- %horizon_playername% — small caps ----
        if (params.equals("playername")) {
            return toSmallCaps(player.getName() != null ? player.getName() : "");
        }

        // ---- %horizon_smallcaps_balance% — balance in small caps with commas ----
        if (params.equals("smallcaps_balance")) {
            if (!plugin.getVaultHook().isAvailable()) return toSmallCaps("0");
            double bal = plugin.getVaultHook().getBalance(player);
            String formatted = NumberFormat.getNumberInstance(Locale.US).format((long) bal);
            return toSmallCaps(formatted);
        }

        // ---- %horizon_smallcaps_claimblocks% — GriefPrevention claim blocks in small caps ----
        if (params.equals("smallcaps_claimblocks")) {
            return toSmallCaps(getClaimBlocks(player));
        }

        // ---- %horizon_ping% — color-coded ping ----
        if (params.equals("ping")) {
            Player onlinePlayer = player.isOnline() ? player.getPlayer() : null;
            if (onlinePlayer == null) return "N/A";
            int ping = onlinePlayer.getPing();
            String color;
            if (ping < 50) color = "\u00A7a";       // green
            else if (ping < 100) color = "\u00A7e";  // yellow
            else if (ping < 200) color = "\u00A76";  // gold
            else color = "\u00A7c";                   // red
            return color + ping + "ms";
        }

        return null; // unknown placeholder
    }

    private String handleQuests(OfflinePlayer player, String sub) {
        QuestManager qm = plugin.getQuestManager();
        if (qm == null) return "0";

        switch (sub) {
            case "available" -> {
                return String.valueOf(qm.getPlayerQuests(player.getUniqueId()).stream()
                    .filter(q -> !q.isCompleted()).count());
            }
            case "daily" -> {
                long count = qm.getPlayerQuests(player.getUniqueId()).stream()
                    .filter(q -> q.getCategory() == QuestCategory.GENERAL_DAILY && !q.isCompleted()).count();
                count += qm.getPlayerQuests(player.getUniqueId()).stream()
                    .filter(q -> q.getCategory() == QuestCategory.JOB_DAILY && !q.isCompleted()).count();
                return String.valueOf(count);
            }
            case "weekly" -> {
                return String.valueOf(qm.getPlayerQuests(player.getUniqueId()).stream()
                    .filter(q -> q.getCategory() == QuestCategory.WEEKLY && !q.isCompleted()).count());
            }
            case "challenges" -> {
                return String.valueOf(qm.getPlayerQuests(player.getUniqueId()).stream()
                    .filter(q -> q.getCategory() == QuestCategory.CHALLENGE && !q.isCompleted()).count());
            }
            case "completed" -> {
                return String.valueOf(qm.getTotalCompleted(player.getUniqueId()));
            }
            case "tier" -> {
                return String.format("%.1f", qm.getPlayerTier(player.getUniqueId()));
            }
            case "daily_reset" -> {
                return qm.getTimeUntilDailyReset();
            }
            case "weekly_reset" -> {
                return qm.getTimeUntilWeeklyReset();
            }
            default -> { return null; }
        }
    }

    private String getClaimBlocks(OfflinePlayer player) {
        try {
            // Use reflection to avoid hard dependency on GriefPrevention
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Object gpInstance = gpClass.getField("instance").get(null);
            Object dataStore = gpClass.getMethod("getDataStore").invoke(gpInstance);
            Object playerData = dataStore.getClass().getMethod("getPlayerData", java.util.UUID.class)
                .invoke(dataStore, player.getUniqueId());
            int remaining = (int) playerData.getClass().getMethod("getRemainingClaimBlocks").invoke(playerData);
            return NumberFormat.getNumberInstance(Locale.US).format(remaining);
        } catch (Exception e) {
            return "0";
        }
    }

    // ---- Small Caps Unicode conversion ----
    private static final String SMALL_CAPS_LOWER = "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

    private static String toSmallCaps(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                sb.append(SMALL_CAPS_LOWER.charAt(c - 'a'));
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(SMALL_CAPS_LOWER.charAt(c - 'A'));
            } else {
                sb.append(c); // digits, special chars stay as-is
            }
        }
        return sb.toString();
    }

    private String handleJob(OfflinePlayer player, String sub) {
        List<JobPlayer> jobs = plugin.getJobManager().getPlayerJobs(player.getUniqueId());
        if (jobs.isEmpty()) {
            return switch (sub) {
                case "name" -> "None";
                case "level", "prestige" -> "0";
                case "xp", "xp_required" -> "0";
                case "progress" -> "0%";
                default -> null;
            };
        }

        JobPlayer jp = jobs.get(0);
        Job job = plugin.getJobManager().getJob(jp.getJobId());
        double xpBase = plugin.getJobManager().getConfig().getXpBase();
        double xpExp  = plugin.getJobManager().getConfig().getXpExponent();

        return switch (sub) {
            case "name" -> job != null ? job.getDisplayName() : jp.getJobId();
            case "level" -> String.valueOf(jp.getLevel());
            case "prestige" -> String.valueOf(jp.getPrestige());
            case "xp" -> String.format("%.0f", jp.getXp());
            case "xp_required" -> {
                double req = JobLevelCalculator.getXpToNextLevel(jp.getXp(), jp.getLevel(), xpBase, xpExp);
                yield String.format("%.0f", req);
            }
            case "progress" -> {
                double xpForThisLevel = JobLevelCalculator.getXpRequired(jp.getLevel(), xpBase, xpExp);
                double xpForNextLevel = JobLevelCalculator.getXpRequired(jp.getLevel() + 1, xpBase, xpExp);
                double span = xpForNextLevel - xpForThisLevel;
                if (span <= 0) yield "100%";
                int pct = (int) Math.min(100, ((jp.getXp() - xpForThisLevel) / span) * 100);
                yield pct + "%";
            }
            default -> null;
        };
    }
}
