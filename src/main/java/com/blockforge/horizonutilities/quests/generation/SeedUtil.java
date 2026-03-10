package com.blockforge.horizonutilities.quests.generation;

import com.blockforge.horizonutilities.quests.QuestCategory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Deterministic seed generation for quest assignment.
 * Same player + same day + same category + same index = same quest every time.
 */
public final class SeedUtil {

    private SeedUtil() {}

    /**
     * Generate a deterministic seed for quest selection.
     *
     * @param playerUuid Player's UUID
     * @param periodKey  Date key ("2026-03-10" for daily, "2026-W11" for weekly)
     * @param category   Quest category
     * @param index      Quest index within category (0, 1, 2...)
     * @return A deterministic long seed
     */
    public static long questSeed(UUID playerUuid, String periodKey, QuestCategory category, int index) {
        String input = playerUuid.toString() + "|" + periodKey + "|" + category.name() + "|" + index;
        return hashToLong(input);
    }

    /**
     * Generate a seed for step target selection within a quest.
     */
    public static long stepSeed(long questSeed, int stepIndex) {
        return hashToLong(questSeed + "|step|" + stepIndex);
    }

    private static long hashToLong(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash, 0, 8).getLong();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
