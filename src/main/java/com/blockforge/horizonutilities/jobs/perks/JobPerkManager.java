package com.blockforge.horizonutilities.jobs.perks;

import com.blockforge.horizonutilities.jobs.JobPlayer;

import java.util.Map;

/**
 * Stateless helper that computes the perk (milestone) bonus multiplier for a
 * given player's job progress. All milestone bonuses whose key level is less
 * than or equal to the player's current level are summed.
 */
public final class JobPerkManager {

    private JobPerkManager() {}

    /**
     * Returns the total perk multiplier contributed purely by level milestones
     * (i.e. the sum of all milestone bonuses the player has unlocked).
     * <p>
     * This value is added on top of the base multiplier of 1.0 and the prestige
     * bonus — see {@link com.blockforge.horizonutilities.jobs.JobLevelCalculator#getIncomeMultiplier}.
     *
     * @param jp         the player's job data
     * @param milestones level -&gt; bonus fraction map (e.g. {@code {10: 0.05, 25: 0.10}})
     * @return sum of unlocked milestone bonuses (0.0 if none unlocked)
     */
    public static double getPerkMultiplier(JobPlayer jp, Map<Integer, Double> milestones) {
        if (milestones == null || milestones.isEmpty()) return 0.0;
        double total = 0.0;
        for (Map.Entry<Integer, Double> entry : milestones.entrySet()) {
            if (jp.getLevel() >= entry.getKey()) {
                total += entry.getValue();
            }
        }
        return total;
    }
}
