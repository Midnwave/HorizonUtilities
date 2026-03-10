package com.blockforge.horizonutilities.quests.generation;

/**
 * Scales quest amounts and rewards based on player tier.
 */
public final class TargetScaler {

    private TargetScaler() {}

    /**
     * Scale a base amount by tier and difficulty scale, clamped to [min, max].
     *
     * @param baseAmount      The template's base amount
     * @param tier            Player tier (0.0 - 10.0)
     * @param difficultyScale Config multiplier per tier unit
     * @param minAmount       Minimum allowed amount
     * @param maxAmount       Maximum allowed amount
     * @return Scaled amount, clamped
     */
    public static int scaleAmount(int baseAmount, double tier, double difficultyScale,
                                  int minAmount, int maxAmount) {
        double scaled = baseAmount * (1.0 + tier * difficultyScale);
        int result = (int) Math.round(scaled);
        return Math.max(minAmount, Math.min(maxAmount, result));
    }

    /**
     * Scale a reward value by tier and reward scale.
     *
     * @param baseReward  The template's base reward value
     * @param tier        Player tier (0.0 - 10.0)
     * @param rewardScale Config multiplier per tier unit
     * @return Scaled reward
     */
    public static double scaleReward(double baseReward, double tier, double rewardScale) {
        return baseReward * (1.0 + tier * rewardScale);
    }
}
