package com.blockforge.horizonutilities.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Utility for detecting Bedrock/Geyser players.
 * Checks Floodgate API via reflection first, then falls back to UUID prefix detection.
 */
public final class BedrockUtil {

    private static Boolean floodgateAvailable;
    private static Method floodgateIsPlayer;
    private static Object floodgateInstance;

    private BedrockUtil() {}

    /** Returns true if the player is connecting via Geyser/Floodgate (Bedrock client). */
    public static boolean isBedrock(Player player) {
        // Try Floodgate API via reflection
        if (isFloodgateAvailable()) {
            try {
                return (boolean) floodgateIsPlayer.invoke(floodgateInstance, player.getUniqueId());
            } catch (Exception ignored) {}
        }

        // Fallback: Floodgate assigns UUIDs starting with 00000000-0000-0000-0009-
        String uuid = player.getUniqueId().toString();
        return uuid.startsWith("00000000-0000-0000-0009-");
    }

    private static boolean isFloodgateAvailable() {
        if (floodgateAvailable == null) {
            floodgateAvailable = false;
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                try {
                    Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    Method getInstance = apiClass.getMethod("getInstance");
                    floodgateInstance = getInstance.invoke(null);
                    floodgateIsPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                    floodgateAvailable = true;
                } catch (Exception ignored) {}
            }
        }
        return floodgateAvailable;
    }
}
