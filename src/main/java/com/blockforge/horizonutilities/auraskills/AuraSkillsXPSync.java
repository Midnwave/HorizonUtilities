package com.blockforge.horizonutilities.auraskills;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Converts job XP earnings into AuraSkills skill XP.
 * Called by JobManager after each XP grant.
 */
public class AuraSkillsXPSync {

    private final AuraSkillsHook hook;
    private final AuraSkillsIntegrationConfig config;

    public AuraSkillsXPSync(AuraSkillsHook hook, AuraSkillsIntegrationConfig config) {
        this.hook = hook;
        this.config = config;
    }

    /**
     * Grants AuraSkills XP equivalent of the given job XP to the player.
     *
     * @param player  the player
     * @param jobId   the job id (e.g. "miner")
     * @param jobXp   the amount of job XP earned
     */
    public void syncXp(Player player, String jobId, double jobXp) {
        if (!hook.isAvailable() || !config.isEnabled() || !config.isXpSyncEnabled()) return;
        if (jobXp <= 0) return;

        Map<String, Double> ratios = config.getXpSyncRatios().get(jobId.toLowerCase(java.util.Locale.ROOT));
        if (ratios == null || ratios.isEmpty()) return;

        for (Map.Entry<String, Double> entry : ratios.entrySet()) {
            String skill = entry.getKey();
            double ratio = entry.getValue();
            if (ratio <= 0) continue;
            hook.addSkillXp(player, skill, jobXp * ratio);
        }
    }
}
