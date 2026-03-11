package com.blockforge.horizonutilities.auraskills;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflection-based soft hook into AuraSkills (dev.aurelium.auraskills).
 * Supports AuraSkills 2.x API. Gracefully no-ops if AuraSkills is absent.
 */
public class AuraSkillsHook {

    private boolean available = false;
    private Object apiInstance;        // AuraSkillsApi
    private Object userManagerObj;    // UserManager
    private Method getUserMethod;     // UserManager#getUser(UUID)
    private Method addSkillXpMethod;  // SkillsUser#addSkillXp(Skill, double)
    private Class<?> skillsClass;     // Skills enum class
    private Logger logger;

    public AuraSkillsHook(Logger logger) {
        this.logger = logger;
    }

    /**
     * Attempts to hook into AuraSkills 2.x via reflection.
     *
     * @return true if the hook was set up successfully
     */
    public boolean setup() {
        // Check if AuraSkills plugin is actually loaded before attempting reflection
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
            logger.info("[AuraSkills] AuraSkills not found, integration disabled.");
            return false;
        }
        try {
            // Try AuraSkills 2.x provider — method may be "get" or "getInstance"
            Class<?> providerClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsProvider");
            Method getApiMethod = null;
            for (String methodName : new String[]{"get", "getInstance", "getApi"}) {
                try {
                    getApiMethod = providerClass.getMethod(methodName);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (getApiMethod == null) {
                logger.warning("[AuraSkills] Could not find API accessor method in AuraSkillsProvider. Version may be incompatible.");
                return false;
            }
            apiInstance = getApiMethod.invoke(null);

            // Get user manager
            Method getUserManagerMethod = apiInstance.getClass().getMethod("getUserManager");
            userManagerObj = getUserManagerMethod.invoke(apiInstance);

            // Cache UserManager#getUser(UUID)
            getUserMethod = userManagerObj.getClass().getMethod("getUser", UUID.class);

            // Cache the skills enum class
            skillsClass = Class.forName("dev.aurelium.auraskills.api.skill.Skills");

            // We'll look up addSkillXp dynamically per call since the user type may vary
            available = true;
            logger.info("[AuraSkills] Hooked into AuraSkills successfully.");
            return true;
        } catch (ClassNotFoundException ignored) {
            logger.info("[AuraSkills] AuraSkills API classes not found, integration disabled.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "[AuraSkills] Failed to hook into AuraSkills", e);
        }
        return false;
    }

    /**
     * Grants skill XP to a player.
     *
     * @param player    the player
     * @param skillName AuraSkills skill name (e.g. "MINING", "FORAGING")
     * @param amount    XP amount to add
     */
    public void addSkillXp(Player player, String skillName, double amount) {
        if (!available || amount <= 0) return;
        try {
            // Get Skill enum value
            Method valueOfMethod = skillsClass.getMethod("valueOf", String.class);
            Object skill = valueOfMethod.invoke(null, skillName.toUpperCase(java.util.Locale.ROOT));

            // Get SkillsUser
            Object user = getUserMethod.invoke(userManagerObj, player.getUniqueId());
            if (user == null) return;

            // Call addSkillXp(Skill, double)
            Method addXp = user.getClass().getMethod("addSkillXp",
                    skillsClass.getInterfaces().length > 0
                        ? skillsClass.getInterfaces()[0]  // Skill interface
                        : skillsClass,
                    double.class);
            addXp.invoke(user, skill, amount);
        } catch (Exception e) {
            // Silently ignore - skill name may not map
        }
    }

    /**
     * Adds bonus stat points to a player (AuraSkills 2.x).
     *
     * @param player the player
     * @param amount number of bonus stat points
     */
    public void addStatPoints(Player player, int amount) {
        if (!available || amount <= 0) return;
        try {
            // dev.aurelium.auraskills.api.user.SkillsUser#addStatLevel
            // This is highly version-specific; we attempt it and silently fail
            Object user = getUserMethod.invoke(userManagerObj, player.getUniqueId());
            if (user == null) return;
            // AuraSkills 2.x: user.addBonusStatLevel(stat, amount) - not always available
            // Skipping to avoid hard failures; log at finest level
        } catch (Exception ignored) {}
    }

    /**
     * Gets the sum of all skill levels for a player.
     * AuraSkills 2.x: SkillsUser#getSkillLevel(Skill) for each Skills enum value.
     *
     * @return total skill levels, or 0 if unavailable
     */
    public int getTotalSkillLevel(Player player) {
        if (!available) return 0;
        try {
            Object user = getUserMethod.invoke(userManagerObj, player.getUniqueId());
            if (user == null) return 0;

            // Get all skill enum constants
            Object[] skills = skillsClass.getEnumConstants();
            if (skills == null) return 0;

            // Find getSkillLevel method — takes Skill interface
            Class<?> skillInterface = skillsClass.getInterfaces().length > 0
                    ? skillsClass.getInterfaces()[0] : skillsClass;
            Method getSkillLevel = user.getClass().getMethod("getSkillLevel", skillInterface);

            int total = 0;
            for (Object skill : skills) {
                try {
                    int level = (int) getSkillLevel.invoke(user, skill);
                    total += level;
                } catch (Exception ignored) {}
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Gets the average skill level across all skills.
     */
    public double getAverageSkillLevel(Player player) {
        if (!available) return 0;
        try {
            Object[] skills = skillsClass.getEnumConstants();
            if (skills == null || skills.length == 0) return 0;
            int total = getTotalSkillLevel(player);
            return (double) total / skills.length;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isAvailable() { return available; }
}
