package com.blockforge.horizonutilities.jobs.quests;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.quests.objectives.JobExploreObjective;
import com.blockforge.horizonutilities.jobs.quests.objectives.JobTradeObjective;
import com.blockforge.horizonutilities.jobs.quests.requirements.JobLevelRequirement;
import com.blockforge.horizonutilities.jobs.quests.rewards.JobXpReward;
import me.pikamug.quests.Quests;
import org.bukkit.plugin.Plugin;

/**
 * Registers HorizonUtilities custom objectives, rewards, and requirements
 * with PikaMug/Quests on startup (soft-dependency).
 *
 * <p>Custom objectives provided:
 * <ul>
 *   <li>"Job: Explore Chunks" — tracks chunk discovery from the Explorer job
 *   <li>"Job: Player Trade"   — tracks trades completed via /trade
 * </ul>
 *
 * <p>Custom reward: "Job XP Reward" — grants XP to a specific job on quest completion.
 * <p>Custom requirement: "Job Level" — gates a quest behind a minimum job level.
 */
public class QuestsIntegration {

    private final boolean available;

    public QuestsIntegration(HorizonUtilitiesPlugin plugin) {
        Plugin questsPlugin = plugin.getServer().getPluginManager().getPlugin("Quests");
        if (questsPlugin instanceof Quests qp && questsPlugin.isEnabled()) {
            JobExploreObjective exploreObj = new JobExploreObjective();
            JobTradeObjective   tradeObj   = new JobTradeObjective();
            JobXpReward         xpReward   = new JobXpReward();
            JobLevelRequirement levelReq   = new JobLevelRequirement();

            qp.getCustomObjectives().add(exploreObj);
            qp.getCustomObjectives().add(tradeObj);
            qp.getCustomRewards().add(xpReward);
            qp.getCustomRequirements().add(levelReq);

            // Register Bukkit listeners for the objective event handlers
            plugin.getServer().getPluginManager().registerEvents(exploreObj, plugin);
            plugin.getServer().getPluginManager().registerEvents(tradeObj, plugin);

            available = true;
            plugin.getLogger().info("[Jobs] PikaMug/Quests integration enabled — "
                    + "custom objectives, rewards, and requirements registered.");
        } else {
            available = false;
            plugin.getLogger().info("[Jobs] Quests plugin not found — quest integration disabled.");
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
