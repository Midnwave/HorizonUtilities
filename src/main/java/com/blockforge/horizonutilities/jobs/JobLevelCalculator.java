package com.blockforge.horizonutilities.jobs;

import java.util.Map;

/**
 * Pure-function utilities for computing job level, XP thresholds, and income
 * multipliers. All methods are stateless and safe to call from any thread.
 */
public final class JobLevelCalculator {

    private JobLevelCalculator() {}

    // -------------------------------------------------------------------------
    // XP / level calculations
    // -------------------------------------------------------------------------

    /**
     * Returns the total XP required to reach {@code level} from level 1.
     * Formula: {@code base * Math.pow(level, exponent)}
     *
     * @param level    the target level (1-based)
     * @param base     base XP constant from config (e.g. 100)
     * @param exponent growth exponent from config (e.g. 1.8)
     * @return XP required
     */
    public static double getXpRequired(int level, double base, double exponent) {
        if (level <= 1) return 0;
        return base * Math.pow(level, exponent);
    }

    /**
     * Returns the level a player has reached given accumulated {@code xp}.
     * Iterates upward until the next level's threshold would exceed xp.
     *
     * @param xp       accumulated XP (cumulative total)
     * @param base     base XP constant
     * @param exponent growth exponent
     * @return current level (minimum 1)
     */
    public static int getLevelForXp(double xp, double base, double exponent) {
        int level = 1;
        while (true) {
            double required = getXpRequired(level + 1, base, exponent);
            if (required > xp) break;
            level++;
        }
        return level;
    }

    /**
     * Computes how much XP remains until the next level, given current xp and
     * current level.
     *
     * @param currentXp    accumulated XP
     * @param currentLevel player's current level
     * @param base         XP base constant
     * @param exponent     XP exponent
     * @return XP still needed to reach {@code currentLevel + 1}
     */
    public static double getXpToNextLevel(double currentXp, int currentLevel, double base, double exponent) {
        double required = getXpRequired(currentLevel + 1, base, exponent);
        return Math.max(0, required - currentXp);
    }

    // -------------------------------------------------------------------------
    // Multiplier calculation
    // -------------------------------------------------------------------------

    /**
     * Returns the total income / XP multiplier for a player.
     * <p>
     * multiplier = 1.0 + (prestige * prestigeBonus) + sum of all milestone
     * bonuses where {@code level >= milestone}.
     *
     * @param level          player's current job level
     * @param prestige       player's prestige count
     * @param prestigeBonus  bonus per prestige (e.g. 0.10 = +10 % per prestige)
     * @param milestones     sorted map of {level -> bonus} perk milestones
     * @return multiplier (always >= 1.0)
     */
    public static double getIncomeMultiplier(int level,
                                             int prestige,
                                             double prestigeBonus,
                                             Map<Integer, Double> milestones) {
        double multiplier = 1.0;
        multiplier += prestige * prestigeBonus;

        if (milestones != null) {
            for (Map.Entry<Integer, Double> entry : milestones.entrySet()) {
                if (level >= entry.getKey()) {
                    multiplier += entry.getValue();
                }
            }
        }

        return Math.max(1.0, multiplier);
    }
}
