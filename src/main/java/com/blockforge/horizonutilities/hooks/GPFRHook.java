package com.blockforge.horizonutilities.hooks;

import com.blockforge.griefpreventionflagsreborn.api.GPFRApi;
import com.blockforge.griefpreventionflagsreborn.api.FlagManager;
import com.blockforge.griefpreventionflagsreborn.api.FlagRegistry;
import com.blockforge.griefpreventionflagsreborn.api.FlagScope;
import com.blockforge.griefpreventionflagsreborn.api.FlagCategory;
import com.blockforge.griefpreventionflagsreborn.api.FlagType;
import com.blockforge.griefpreventionflagsreborn.api.FlagDefinition;
import com.blockforge.griefpreventionflagsreborn.flags.BooleanFlag;
import com.blockforge.griefpreventionflagsreborn.flags.NumberFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import java.util.logging.Logger;

public class GPFRHook {
    private FlagRegistry registry;
    private FlagManager manager;
    private boolean available = false;
    private final Logger logger;

    public GPFRHook(Logger logger) {
        this.logger = logger;
    }

    public boolean setup() {
        try {
            if (!GPFRApi.isAvailable()) return false;
            registry = GPFRApi.getRegistry();
            manager = GPFRApi.getManager();
            available = true;
            logger.info("Successfully hooked into GriefPreventionFlagsReborn!");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to hook into GPFR: " + e.getMessage());
            available = false;
            return false;
        }
    }

    public void registerCustomFlags() {
        if (!available) return;
        int registered = 0;
        // Register each flag individually — skip if GPFR already provides it natively
        registered += tryRegister(new BooleanFlag(
            FlagDefinition.builder()
                .id("job-tax-enabled")
                .displayName("Job Tax Enabled")
                .description("Whether job income earned in this claim is taxed")
                .type(FlagType.BOOLEAN)
                .category(FlagCategory.ECONOMY)
                .defaultValue(false)
                .allowedScopes(FlagScope.SERVER, FlagScope.WORLD, FlagScope.CLAIM)
                .guiIcon(Material.GOLD_NUGGET)
                .adminOnly(false)
                .build()
        ));
        registered += tryRegister(new NumberFlag(
            FlagDefinition.builder()
                .id("job-tax-rate")
                .displayName("Job Tax Rate")
                .description("Percentage of job income taken as tax (0.0 to 1.0). Requires job-tax-enabled.")
                .type(FlagType.DOUBLE)
                .category(FlagCategory.ECONOMY)
                .defaultValue(0.10)
                .allowedScopes(FlagScope.SERVER, FlagScope.WORLD, FlagScope.CLAIM)
                .guiIcon(Material.GOLD_INGOT)
                .adminOnly(false)
                .build(),
            0.0, 1.0
        ));
        registered += tryRegister(new BooleanFlag(
            FlagDefinition.builder()
                .id("bounty-hunting-allowed")
                .displayName("Bounty Hunting Allowed")
                .description("Whether bounty kills in this claim are rewarded (HorizonUtilities)")
                .type(FlagType.BOOLEAN)
                .category(FlagCategory.PVP)
                .defaultValue(true)
                .allowedScopes(FlagScope.SERVER, FlagScope.WORLD, FlagScope.CLAIM, FlagScope.SUBCLAIM)
                .guiIcon(Material.DIAMOND_SWORD)
                .adminOnly(false)
                .build()
        ));
        registered += tryRegister(new BooleanFlag(
            FlagDefinition.builder()
                .id("skill-boost-zone")
                .displayName("Skill Boost Zone")
                .description("Players in this claim receive bonus AuraSkills XP")
                .type(FlagType.BOOLEAN)
                .category(FlagCategory.ECONOMY)
                .defaultValue(false)
                .allowedScopes(FlagScope.CLAIM, FlagScope.SUBCLAIM)
                .guiIcon(Material.EXPERIENCE_BOTTLE)
                .adminOnly(true)
                .build()
        ));
        if (registered > 0) {
            logger.info("Registered " + registered + " custom HorizonUtilities flags in GPFR");
        }
    }

    private int tryRegister(com.blockforge.griefpreventionflagsreborn.flags.AbstractFlag<?> flag) {
        try {
            registry.registerFlag(flag);
            return 1;
        } catch (Exception e) {
            // Flag already registered by GPFR natively — skip silently
            return 0;
        }
    }

    public boolean isJobTaxEnabled(Location location) {
        if (!available) return false;
        return manager.isFlagEnabled("job-tax-enabled", location);
    }

    public double getJobTaxRate(Location location) {
        if (!available) return 0.10;
        try {
            Number rate = manager.getFlagValue("job-tax-rate", location);
            return rate != null ? rate.doubleValue() : 0.10;
        } catch (Exception e) { return 0.10; }
    }

    public boolean isBountyHuntingAllowed(Location location) {
        if (!available) return true;
        return manager.isFlagEnabled("bounty-hunting-allowed", location);
    }

    public boolean isSkillBoostZone(Location location) {
        if (!available) return false;
        return manager.isFlagEnabled("skill-boost-zone", location);
    }

    public boolean isAvailable() { return available; }
}
