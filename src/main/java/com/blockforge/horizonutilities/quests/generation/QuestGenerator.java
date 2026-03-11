package com.blockforge.horizonutilities.quests.generation;

import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestConfig;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seed-based quest generation engine using fully procedural building.
 * Same player + same day + same category + same index, same quest every time.
 * Quests are generated on-the-fly from Bukkit enums, no templates are needed.
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

        List<ActiveQuest> result = new ArrayList<>();

        for (int i = 0; i < questCount; i++) {
            long seed = SeedUtil.questSeed(playerUuid, periodKey, category, i);

            // Check if this seed's synthetic ID was recently used (for challenges)
            String syntheticId = "proc_" + category.name().toLowerCase() + "_"
                    + Long.toHexString(seed).substring(0, Math.min(8, Long.toHexString(seed).length()));
            if (excludeTemplates.contains(syntheticId)) {
                // Use an offset seed to avoid the excluded quest
                seed = SeedUtil.questSeed(playerUuid, periodKey + "_alt", category, i);
            }

            ActiveQuest quest = ProceduralQuestBuilder.build(
                playerUuid, periodKey, category, seed, tier, jobId, config
            );
            if (quest != null) {
                result.add(quest);
            }
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

        return ProceduralQuestBuilder.build(
            playerUuid, periodKey, category, seed, difficulty, null, config
        );
    }
}
