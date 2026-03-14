package com.blockforge.horizonutilities.wither.attacks;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.wither.WitherBossConfig;
import com.blockforge.horizonutilities.wither.WitherBossEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Schedules and executes attacks for the Wither Sovereign.
 * Manages per-attack cooldowns, global cooldown, phase filtering,
 * and weighted random selection. Called every tick from WitherBossEntity.
 */
public class AttackScheduler {

    private final WitherBossEntity boss;
    private final WitherBossConfig config;
    private final HorizonUtilitiesPlugin plugin;

    private final List<WitherAttack> attacks = new ArrayList<>();
    private final Map<String, Long> lastUsedTimestamps = new HashMap<>();
    private long lastAnyAttackTime = 0;

    /** Skull Storm attack ID for forced phase transitions */
    private static final String SKULL_STORM_ID = "skull_storm";

    public AttackScheduler(WitherBossEntity boss, WitherBossConfig config, HorizonUtilitiesPlugin plugin) {
        this.boss = boss;
        this.config = config;
        this.plugin = plugin;
    }

    /**
     * Called every tick from the boss entity's main loop.
     * Attempts to select and execute an attack if cooldowns allow.
     */
    public void tick() {
        if (boss.isDead() || !boss.getWither().isValid()) {
            return;
        }

        selectAndExecute();
    }

    /**
     * Registers an attack with this scheduler.
     */
    public void registerAttack(WitherAttack attack) {
        attacks.add(attack);
    }

    /**
     * Filters attacks by current phase, checks cooldowns, picks a weighted random attack,
     * and executes it.
     */
    private void selectAndExecute() {
        long now = System.currentTimeMillis();

        // Check global cooldown
        double effectiveGlobalCooldown = config.getGlobalCooldown();
        double phaseSpeedMultiplier = config.getPhaseAttackSpeed(boss.getCurrentPhase());
        if (boss.isEnraged()) {
            phaseSpeedMultiplier *= 2.0;
        }
        // Higher attack speed = shorter cooldowns
        double adjustedGlobalCooldown = effectiveGlobalCooldown / phaseSpeedMultiplier;
        long globalCooldownMs = (long) (adjustedGlobalCooldown * 1000);

        if (now - lastAnyAttackTime < globalCooldownMs) {
            return;
        }

        int currentPhase = boss.getCurrentPhase();

        // Build list of available attacks
        List<WitherAttack> available = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        for (WitherAttack attack : attacks) {
            // Phase filter
            if (!attack.getActivePhases().contains(currentPhase)) {
                continue;
            }

            // Per-attack cooldown check
            Long lastUsed = lastUsedTimestamps.get(attack.getId());
            if (lastUsed != null) {
                double attackCooldown = attack.getCooldownSeconds() / phaseSpeedMultiplier;
                long attackCooldownMs = (long) (attackCooldown * 1000);
                if (now - lastUsed < attackCooldownMs) {
                    continue;
                }
            }

            // Condition check
            if (!attack.canExecute(boss)) {
                continue;
            }

            double weight = attack.getWeight(currentPhase);
            if (weight > 0) {
                available.add(attack);
                weights.add(weight);
            }
        }

        if (available.isEmpty()) {
            return;
        }

        // Weighted random selection
        WitherAttack selected = selectWeightedRandom(available, weights);
        if (selected != null) {
            executeAttack(selected, now);
        }
    }

    /**
     * Selects an attack using weighted random selection.
     */
    private WitherAttack selectWeightedRandom(List<WitherAttack> available, List<Double> weights) {
        double totalWeight = 0;
        for (double w : weights) {
            totalWeight += w;
        }

        if (totalWeight <= 0) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;

        for (int i = 0; i < available.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) {
                return available.get(i);
            }
        }

        // Fallback to last attack (shouldn't happen due to floating point)
        return available.get(available.size() - 1);
    }

    /**
     * Executes the given attack and records timestamps.
     */
    private void executeAttack(WitherAttack attack, long now) {
        lastUsedTimestamps.put(attack.getId(), now);
        lastAnyAttackTime = now;
        attack.execute(boss);
    }

    /**
     * Forces execution of the Skull Storm attack during phase transitions.
     * If Skull Storm isn't registered, does nothing.
     */
    public void triggerPhaseTransition() {
        long now = System.currentTimeMillis();
        for (WitherAttack attack : attacks) {
            if (SKULL_STORM_ID.equals(attack.getId())) {
                executeAttack(attack, now);
                return;
            }
        }
    }

    /**
     * Calls cleanup() on all registered attacks to dispose of lingering effects.
     */
    public void cleanup() {
        for (WitherAttack attack : attacks) {
            attack.cleanup();
        }
        attacks.clear();
        lastUsedTimestamps.clear();
    }

    public List<WitherAttack> getAttacks() {
        return attacks;
    }

    public HorizonUtilitiesPlugin getPlugin() {
        return plugin;
    }
}
