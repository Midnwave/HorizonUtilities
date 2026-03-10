package com.blockforge.horizonutilities.quests.generation;

import com.blockforge.horizonutilities.quests.QuestActionType;
import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestConfig;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import com.blockforge.horizonutilities.quests.model.QuestStep;
import com.blockforge.horizonutilities.quests.model.QuestTemplate;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Seed-based quest selection and parameterization engine.
 * Same player + same day + same category + same index = same quest every time.
 */
public final class QuestGenerator {

    private QuestGenerator() {}

    /**
     * Generate quests for a player in a given category and period.
     *
     * @param playerUuid  Player's UUID
     * @param periodKey   Date key ("2026-03-10" for daily, "2026-W11" for weekly)
     * @param category    Quest category
     * @param tier        Player's calculated tier (0.0 - 10.0)
     * @param config      Quest configuration
     * @param jobId       Job ID for JOB_DAILY category (null otherwise)
     * @param excludeTemplates Template IDs to exclude (e.g. recent challenges)
     * @return List of generated ActiveQuest instances (dbId will be -1 until saved)
     */
    public static List<ActiveQuest> generateQuests(UUID playerUuid, String periodKey,
                                                    QuestCategory category, double tier,
                                                    QuestConfig config, String jobId,
                                                    List<String> excludeTemplates) {
        QuestConfig.QuestCountConfig countConfig = config.getQuestCount(category);
        int questCount = countConfig.calculateCount(tier);
        if (questCount <= 0) return List.of();

        List<QuestTemplate> pool = config.getTemplatesForCategory(category, jobId);

        // Filter out excluded templates and those above player's tier
        List<QuestTemplate> eligible = new ArrayList<>();
        for (QuestTemplate t : pool) {
            if (tier >= t.getMinTier() && !excludeTemplates.contains(t.getTemplateId())) {
                eligible.add(t);
            }
        }

        if (eligible.isEmpty()) return List.of();

        // Don't generate more quests than we have templates
        questCount = Math.min(questCount, eligible.size());

        List<ActiveQuest> result = new ArrayList<>();
        List<String> usedTemplateIds = new ArrayList<>();

        for (int i = 0; i < questCount; i++) {
            long seed = SeedUtil.questSeed(playerUuid, periodKey, category, i);
            Random rng = new Random(seed);

            // Pick template (weighted, no repeats within same generation)
            QuestTemplate template = pickTemplate(eligible, usedTemplateIds, rng);
            if (template == null) break;
            usedTemplateIds.add(template.getTemplateId());

            ActiveQuest quest = buildQuest(playerUuid, periodKey, category, seed, tier, template, config, rng);
            result.add(quest);
        }

        return result;
    }

    /**
     * Generate a single random quest for debug/admin purposes with custom difficulty.
     */
    public static ActiveQuest generateDebugQuest(UUID playerUuid, QuestCategory category,
                                                  double difficulty, QuestConfig config) {
        String periodKey = "debug-" + System.currentTimeMillis();
        long seed = System.nanoTime();
        Random rng = new Random(seed);

        List<QuestTemplate> pool = config.getTemplatesForCategory(category, null);
        if (pool.isEmpty()) return null;

        // Pick a random template
        QuestTemplate template = pool.get(rng.nextInt(pool.size()));

        return buildQuest(playerUuid, periodKey, category, seed, difficulty, template, config, rng);
    }

    /**
     * Build an ActiveQuest from a template, resolving targets and scaling amounts.
     */
    private static ActiveQuest buildQuest(UUID playerUuid, String periodKey, QuestCategory category,
                                           long seed, double tier, QuestTemplate template,
                                           QuestConfig config, Random rng) {
        List<ActiveQuest.ActiveStep> activeSteps = new ArrayList<>();

        for (int s = 0; s < template.getSteps().size(); s++) {
            QuestStep stepDef = template.getSteps().get(s);

            // Resolve target from the targets list using a sub-seed
            long stepSeed = SeedUtil.stepSeed(seed, s);
            Random stepRng = new Random(stepSeed);
            String resolvedTarget = resolveTarget(stepDef, stepRng);

            // Scale amount by tier
            int scaledAmount = TargetScaler.scaleAmount(
                stepDef.getBaseAmount(), tier, config.getDifficultyScale(),
                stepDef.getMinAmount(), stepDef.getMaxAmount()
            );

            String description = stepDef.formatDescription(resolvedTarget, scaledAmount);

            activeSteps.add(new ActiveQuest.ActiveStep(
                -1, s, stepDef.getActionType(),
                resolvedTarget, scaledAmount,
                0, false, description
            ));
        }

        // Scale rewards by tier
        double rewardMultiplier = 1.0 + tier * config.getRewardScale();
        RewardDefinition scaledRewards = template.getRewards().scaled(rewardMultiplier);

        return new ActiveQuest(
            -1, playerUuid, template.getTemplateId(), category,
            periodKey, seed, activeSteps, scaledRewards,
            0, false, null, false, System.currentTimeMillis()
        );
    }

    /**
     * Pick a template using weighted random selection, excluding already-used ones.
     */
    private static QuestTemplate pickTemplate(List<QuestTemplate> eligible,
                                               List<String> usedIds, Random rng) {
        // Build filtered pool
        List<QuestTemplate> available = new ArrayList<>();
        int totalWeight = 0;
        for (QuestTemplate t : eligible) {
            if (!usedIds.contains(t.getTemplateId())) {
                available.add(t);
                totalWeight += t.getWeight();
            }
        }
        if (available.isEmpty() || totalWeight <= 0) return null;

        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (QuestTemplate t : available) {
            cumulative += t.getWeight();
            if (roll < cumulative) return t;
        }
        return available.get(available.size() - 1);
    }

    /**
     * Resolve a target material from the step's target list.
     * If the list is empty or null, returns null (any target).
     */
    private static String resolveTarget(QuestStep step, Random rng) {
        List<String> targets = step.getTargets();
        if (targets == null || targets.isEmpty()) return null;
        if (targets.size() == 1) return targets.get(0);
        return targets.get(rng.nextInt(targets.size()));
    }
}
