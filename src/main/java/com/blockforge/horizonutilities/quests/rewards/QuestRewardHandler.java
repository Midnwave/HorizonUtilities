package com.blockforge.horizonutilities.quests.rewards;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.quests.QuestConfig;
import com.blockforge.horizonutilities.quests.QuestStorage;
import com.blockforge.horizonutilities.quests.antiexploit.QuestAntiExploit;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * Distributes quest rewards to players, respecting daily caps and anti-exploit rules.
 */
public class QuestRewardHandler {

    private final HorizonUtilitiesPlugin plugin;
    private final QuestStorage storage;
    private final QuestAntiExploit antiExploit;
    private final Logger logger;

    public QuestRewardHandler(HorizonUtilitiesPlugin plugin, QuestStorage storage, QuestAntiExploit antiExploit) {
        this.plugin = plugin;
        this.storage = storage;
        this.antiExploit = antiExploit;
        this.logger = plugin.getLogger();
    }

    /**
     * Grant rewards for a completed quest. Returns true if rewards were successfully given.
     */
    public boolean grantRewards(Player player, ActiveQuest quest) {
        if (quest.isRewardsClaimed()) return false;

        RewardDefinition rewards = quest.getScaledRewards();
        if (rewards == null || !rewards.hasAnyReward()) {
            quest.setRewardsClaimed(true);
            return true;
        }

        int questId = quest.getDbId();

        // Money (with daily cap)
        if (rewards.getMoney() > 0) {
            double allowed = antiExploit.getAllowedMoney(player.getUniqueId(), rewards.getMoney());
            if (allowed > 0 && plugin.getVaultHook().isAvailable()) {
                plugin.getVaultHook().deposit(player, allowed);
                storage.logReward(questId, player.getUniqueId(), "MONEY", allowed, null);
                logger.fine("[Quests] Gave " + player.getName() + " $" + String.format("%.2f", allowed) +
                    " for quest " + quest.getTemplateId());
            }
        }

        // Job XP
        if (rewards.getJobXp() > 0) {
            var jobs = plugin.getJobManager().getPlayerJobs(player.getUniqueId());
            if (!jobs.isEmpty()) {
                // Distribute XP evenly across active jobs
                double xpEach = rewards.getJobXp() / jobs.size();
                for (var jp : jobs) {
                    jp.addXp(xpEach);
                }
                storage.logReward(questId, player.getUniqueId(), "JOB_XP", rewards.getJobXp(),
                    "Split across " + jobs.size() + " jobs");
            }
        }

        // Minecraft XP levels
        if (rewards.getXpLevels() > 0) {
            player.giveExpLevels(rewards.getXpLevels());
            storage.logReward(questId, player.getUniqueId(), "XP_LEVELS", rewards.getXpLevels(), null);
        }

        // Gems
        if (rewards.getGems() > 0) {
            plugin.getGemsManager().deposit(player.getUniqueId(), rewards.getGems(), "Quest: " + quest.getTemplateId());
            storage.logReward(questId, player.getUniqueId(), "GEMS", rewards.getGems(), null);
        }

        // Items (with daily cap)
        for (RewardDefinition.ItemReward item : rewards.getItems()) {
            if (!antiExploit.canGiveItemReward(player.getUniqueId())) {
                logger.fine("[Quests] Daily item reward cap reached for " + player.getName());
                break;
            }
            ItemStack stack = new ItemStack(item.material(), item.amount());
            var leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                // Drop on ground if inventory full
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            storage.logReward(questId, player.getUniqueId(), "ITEM", item.amount(),
                item.material().name());
        }

        quest.setRewardsClaimed(true);
        return true;
    }
}
