package com.blockforge.horizonutilities.auraskills;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.entity.Player;

/**
 * Central manager for AuraSkills integration.
 * Wires together the hook, config, XP sync, and milestone rewards.
 */
public class AuraSkillsManager {

    private final AuraSkillsHook hook;
    private final AuraSkillsIntegrationConfig config;
    private final AuraSkillsXPSync xpSync;
    private final AuraSkillsMilestoneRewards milestoneRewards;
    private final boolean active;

    public AuraSkillsManager(HorizonUtilitiesPlugin plugin) {
        this.hook = new AuraSkillsHook(plugin.getLogger());
        this.config = new AuraSkillsIntegrationConfig(plugin);
        config.load();

        boolean hooked = false;
        if (plugin.getServer().getPluginManager().isPluginEnabled("AuraSkills")) {
            hooked = hook.setup();
        }
        this.active = hooked && config.isEnabled();

        this.xpSync = new AuraSkillsXPSync(hook, config);
        this.milestoneRewards = new AuraSkillsMilestoneRewards(hook, config);

        if (active) {
            plugin.getLogger().info("[AuraSkills] Integration active.");
        } else {
            plugin.getLogger().info("[AuraSkills] Integration inactive (AuraSkills not found or disabled in config).");
        }
    }

    /** Sync job XP to AuraSkills skill XP. */
    public void syncXp(Player player, String jobId, double jobXp) {
        if (active) xpSync.syncXp(player, jobId, jobXp);
    }

    /** Check and grant milestone rewards on level-up. */
    public void checkMilestone(Player player, int level) {
        if (config.isEnabled() && config.isMilestonesEnabled()) {
            milestoneRewards.checkAndGrant(player, level);
        }
    }

    public boolean isActive() { return active; }
    public AuraSkillsHook getHook() { return hook; }
    public AuraSkillsIntegrationConfig getConfig() { return config; }
}
