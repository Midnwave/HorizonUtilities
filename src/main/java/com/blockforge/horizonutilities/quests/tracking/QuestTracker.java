package com.blockforge.horizonutilities.quests.tracking;

import com.blockforge.horizonutilities.quests.QuestActionType;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Matches incoming actions to active quest steps and updates progress.
 */
public class QuestTracker {

    private final QuestManager questManager;

    public QuestTracker(QuestManager questManager) {
        this.questManager = questManager;
    }

    /**
     * Process an action and update any matching quest steps.
     *
     * @param player     The player performing the action
     * @param actionType The type of action
     * @param material   The material/entity involved (may be null for "any" quests)
     */
    public void trackAction(Player player, QuestActionType actionType, String material) {
        List<ActiveQuest> quests = questManager.getPlayerQuests(player.getUniqueId());
        if (quests.isEmpty()) return;

        for (ActiveQuest quest : quests) {
            if (!quest.canTrack(actionType, material)) continue;

            boolean justCompleted = quest.addProgress(actionType, material, 1);
            questManager.onQuestProgress(player, quest, justCompleted);
        }
    }

    /**
     * Process an action with a custom amount (for batch operations).
     */
    public void trackAction(Player player, QuestActionType actionType, String material, int amount) {
        List<ActiveQuest> quests = questManager.getPlayerQuests(player.getUniqueId());
        if (quests.isEmpty()) return;

        for (ActiveQuest quest : quests) {
            if (!quest.canTrack(actionType, material)) continue;

            boolean justCompleted = quest.addProgress(actionType, material, amount);
            questManager.onQuestProgress(player, quest, justCompleted);
        }
    }
}
