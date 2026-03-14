package com.blockforge.horizonutilities.wither.ai;

import com.blockforge.horizonutilities.wither.WitherBossConfig;
import com.blockforge.horizonutilities.wither.WitherBossEntity;

/**
 * Manages phase transitions and the enrage timer for the Wither Sovereign boss fight.
 * Called every tick from the boss entity's main loop.
 */
public class WitherPhaseManager {

    private final WitherBossEntity boss;
    private final WitherBossConfig config;

    private int currentPhase = 1;
    private boolean resurrectionUsed = false;

    public WitherPhaseManager(WitherBossEntity boss, WitherBossConfig config) {
        this.boss = boss;
        this.config = config;
    }

    /**
     * Called every tick. Checks wither health percentage against phase thresholds
     * and triggers phase transitions. Also manages the enrage timer.
     */
    public void update() {
        if (boss.isDead() || !boss.getWither().isValid()) {
            return;
        }

        // Check enrage timer
        if (!boss.isEnraged()) {
            long fightSeconds = boss.getFightDurationSeconds();
            if (fightSeconds >= config.getEnrageTimerSeconds()) {
                boss.setEnraged(true);
            }
        }

        // Calculate health percentage
        double healthPercent = boss.getWither().getHealth() / boss.getMaxHealth();

        // Determine what phase we should be in based on health thresholds
        // Phase 1: 100-75%, Phase 2: 75-50%, Phase 3: 50-25%, Phase 4: 25-0%
        int newPhase = determinePhase(healthPercent);

        // Trigger phase transition if phase changed
        if (newPhase > currentPhase) {
            currentPhase = newPhase;
            boss.onPhaseChange(newPhase);
            boss.getAttackScheduler().triggerPhaseTransition();
        }
    }

    /**
     * Determines the current phase based on health percentage.
     * Uses configurable thresholds from WitherBossConfig.
     */
    private int determinePhase(double healthPercent) {
        // Phase thresholds: phase1=0.75, phase2=0.50, phase3=0.25
        // If health is above phase1 threshold -> phase 1
        // If health is between phase1 and phase2 -> phase 2
        // If health is between phase2 and phase3 -> phase 3
        // If health is below phase3 -> phase 4
        if (healthPercent > config.getPhaseThreshold(1)) {
            return 1;
        } else if (healthPercent > config.getPhaseThreshold(2)) {
            return 2;
        } else if (healthPercent > config.getPhaseThreshold(3)) {
            return 3;
        } else {
            return 4;
        }
    }

    public int getCurrentPhase() {
        return currentPhase;
    }

    public boolean isResurrectionUsed() {
        return resurrectionUsed;
    }

    public void setResurrectionUsed(boolean resurrectionUsed) {
        this.resurrectionUsed = resurrectionUsed;
    }
}
