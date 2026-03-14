package com.blockforge.horizonutilities.wither.attacks;

import com.blockforge.horizonutilities.wither.WitherBossEntity;

import java.util.Set;

/**
 * Base interface for all custom Wither Sovereign attacks.
 */
public interface WitherAttack {

    /** Unique identifier, e.g. "wither_barrage" */
    String getId();

    /** Display name for boss bar / messages */
    String getDisplayName();

    /** Which phases this attack is available in (1-4) */
    Set<Integer> getActivePhases();

    /** Per-attack cooldown in seconds */
    double getCooldownSeconds();

    /** Selection weight — higher = more likely to be chosen by scheduler */
    double getWeight(int phase);

    /** Check conditions (target alive, range, etc.) */
    boolean canExecute(WitherBossEntity boss);

    /** Execute the attack */
    void execute(WitherBossEntity boss);

    /** Clean up lingering effects (display entities, scheduled tasks, etc.) */
    void cleanup();
}
