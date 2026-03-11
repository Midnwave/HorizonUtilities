package com.blockforge.horizonutilities.quests.generation;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.auraskills.AuraSkillsHook;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import com.blockforge.horizonutilities.quests.QuestConfig;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Calculates a player's activity tier (0.0 - 10.0) using real engagement metrics.
 * An AFK player with high playtime will score low because per-hour metrics are tiny.
 * All weights and thresholds are configurable via QuestConfig.
 */
public final class TierCalculator {

    private TierCalculator() {}

    public static double calculateTier(Player player, HorizonUtilitiesPlugin plugin, QuestConfig config) {
        // --- Gather raw stats from Bukkit API ---
        double playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        double playTimeHours = Math.max(playTimeTicks / 20.0 / 3600.0, 0.1); // min 0.1 to avoid /0

        int totalBlocksMined = sumBlocksMined(player);
        int totalMobsKilled = sumMobsKilled(player);
        long walkCm = player.getStatistic(Statistic.WALK_ONE_CM);
        long sprintCm = player.getStatistic(Statistic.SPRINT_ONE_CM);
        int craftingInteractions = player.getStatistic(Statistic.CRAFTING_TABLE_INTERACTION);
        int deathCount = player.getStatistic(Statistic.DEATHS);
        int fishCaught = player.getStatistic(Statistic.FISH_CAUGHT);
        int animalsBred = player.getStatistic(Statistic.ANIMALS_BRED);

        // --- Job stats ---
        List<JobPlayer> jobs = plugin.getJobManager().getPlayerJobs(player.getUniqueId());
        double avgJobLevel = 0;
        int totalPrestige = 0;
        if (!jobs.isEmpty()) {
            double levelSum = 0;
            for (JobPlayer jp : jobs) {
                levelSum += jp.getLevel();
                totalPrestige += jp.getPrestige();
            }
            avgJobLevel = levelSum / jobs.size();
        }

        // --- Exploration (from DB) ---
        int uniqueChunks = getExploredChunkCount(plugin, player.getUniqueId().toString());

        // --- Calculate tier score ---
        QuestConfig.TierWeights w = config.getTierWeights();

        double score = 0;

        // Time-based (low weight to prevent AFK inflation)
        score += playTimeHours * w.playHoursWeight();

        // Mining activity per hour (normalizes AFK)
        double miningPerHour = totalBlocksMined / playTimeHours;
        score += Math.min(miningPerHour, w.miningPerHourCap()) * w.miningWeight();

        // Combat activity per hour
        double killsPerHour = totalMobsKilled / playTimeHours;
        score += Math.min(killsPerHour, w.killsPerHourCap()) * w.combatWeight();

        // Distance traveled (walking + sprinting only, in km)
        double distanceKm = (walkCm + sprintCm) / 100_000.0;
        score += Math.min(distanceKm, w.distanceCapKm()) * w.distanceWeight();

        // Crafting engagement per hour
        double craftingPerHour = craftingInteractions / playTimeHours;
        score += Math.min(craftingPerHour, w.craftingPerHourCap()) * w.craftingWeight();

        // Death ratio (low deaths relative to kills = skilled)
        if (totalMobsKilled > 0) {
            double survivalRatio = Math.max(0, 1.0 - ((double) deathCount / totalMobsKilled));
            score += survivalRatio * w.survivalWeight();
        }

        // Job progression
        score += avgJobLevel * w.jobLevelWeight();
        score += totalPrestige * w.prestigeWeight();

        // Exploration
        score += Math.min((double) uniqueChunks / w.explorationDivisor(), 1.0) * w.explorationWeight();

        // AuraSkills average level (soft-depends, gracefully 0 if absent)
        AuraSkillsHook auraHook = plugin.getAuraSkillsManager() != null
                ? plugin.getAuraSkillsManager().getHook() : null;
        double avgSkillLevel = (auraHook != null && auraHook.isAvailable())
                ? auraHook.getAverageSkillLevel(player) : 0;
        score += Math.min(avgSkillLevel / w.auraSkillsDivisor(), 1.0) * w.auraSkillsWeight();

        // Diversity bonus: how many activity categories are non-trivial
        int diverseCount = 0;
        int diverseTotal = 8;
        if (totalBlocksMined > w.diversityBlocksThreshold()) diverseCount++;
        if (totalMobsKilled > w.diversityKillsThreshold()) diverseCount++;
        if (distanceKm > w.diversityDistanceThreshold()) diverseCount++;
        if (craftingInteractions > w.diversityCraftingThreshold()) diverseCount++;
        if (fishCaught > w.diversityFishingThreshold()) diverseCount++;
        if (animalsBred > w.diversityBreedingThreshold()) diverseCount++;
        if (avgJobLevel > w.diversityJobLevelThreshold()) diverseCount++;
        if (avgSkillLevel > w.diversityAuraSkillsThreshold()) diverseCount++;

        double diversityBonus = (double) diverseCount / diverseTotal;
        score += diversityBonus * w.diversityWeight();

        return Math.min(score, config.getMaxTier());
    }

    /**
     * Sum all blocks mined across common materials.
     */
    private static int sumBlocksMined(Player player) {
        int total = 0;
        for (Material mat : Material.values()) {
            if (!mat.isBlock() || mat.isAir()) continue;
            try {
                total += player.getStatistic(Statistic.MINE_BLOCK, mat);
            } catch (IllegalArgumentException ignored) {}
        }
        return total;
    }

    /**
     * Sum all mobs killed across all entity types.
     */
    private static int sumMobsKilled(Player player) {
        int total = 0;
        for (EntityType type : EntityType.values()) {
            if (!type.isAlive()) continue;
            try {
                total += player.getStatistic(Statistic.KILL_ENTITY, type);
            } catch (IllegalArgumentException ignored) {}
        }
        return total;
    }

    /**
     * Query the jobs_explored_chunks table for the count of unique chunks explored.
     */
    private static int getExploredChunkCount(HorizonUtilitiesPlugin plugin, String uuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM jobs_explored_chunks WHERE player_uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
