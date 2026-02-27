package com.blockforge.horizonutilities.hooks;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.JobLevelCalculator;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import com.blockforge.horizonutilities.lottery.LotteryInstance;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

        return null; // unknown placeholder
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
