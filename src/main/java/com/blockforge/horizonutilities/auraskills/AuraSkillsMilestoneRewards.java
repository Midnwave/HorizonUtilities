package com.blockforge.horizonutilities.auraskills;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * Grants AuraSkills milestone rewards when a player reaches a job level.
 * Called by JobManager on level-up.
 */
public class AuraSkillsMilestoneRewards {

    private final AuraSkillsHook hook;
    private final AuraSkillsIntegrationConfig config;

    public AuraSkillsMilestoneRewards(AuraSkillsHook hook, AuraSkillsIntegrationConfig config) {
        this.hook = hook;
        this.config = config;
    }

    /**
     * Checks if the given level is a milestone and grants rewards if so.
     *
     * @param player   the player who leveled up
     * @param level    the new level
     */
    public void checkAndGrant(Player player, int level) {
        if (!config.isEnabled() || !config.isMilestonesEnabled()) return;

        AuraSkillsIntegrationConfig.MilestoneReward reward = config.getMilestones().get(level);
        if (reward == null) return;

        // Give XP lamp items (use EXPERIENCE_BOTTLE as proxy)
        if (reward.xpLampAmount() > 0) {
            // Give in stacks of 64
            int remaining = reward.xpLampAmount();
            while (remaining > 0) {
                int stack = Math.min(remaining, 64);
                player.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE, stack));
                remaining -= stack;
            }
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "[Jobs] Milestone reward: " + reward.xpLampAmount() + "x XP Bottle!",
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
        }

        // Grant stat points if hook available
        if (reward.bonusStatPoints() > 0 && hook.isAvailable()) {
            hook.addStatPoints(player, reward.bonusStatPoints());
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "[Jobs] Milestone reward: +" + reward.bonusStatPoints() + " stat point(s)!",
                net.kyori.adventure.text.format.NamedTextColor.AQUA));
        }
    }
}
