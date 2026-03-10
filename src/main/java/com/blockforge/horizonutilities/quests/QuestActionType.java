package com.blockforge.horizonutilities.quests;

import com.blockforge.horizonutilities.jobs.JobAction;

/**
 * All trackable actions for quests. Superset of JobAction with additional types.
 */
public enum QuestActionType {
    BREAK,
    PLACE,
    KILL,
    FISH,
    CRAFT,
    SMELT,
    BREW,
    ENCHANT,
    REPAIR,
    TAME,
    SHEAR,
    MILK,
    FARM,
    EXPLORE_CHUNK,
    EXPLORE_DISTANCE,
    TRADE_PLAYER,
    TRADE_AH,
    EAT,
    BREED;

    /**
     * Attempt to convert a JobAction to a QuestActionType.
     * Returns null if no mapping exists.
     */
    public static QuestActionType fromJobAction(JobAction action) {
        try {
            return valueOf(action.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Attempt to convert this to a JobAction.
     * Returns null if this action type has no JobAction equivalent.
     */
    public JobAction toJobAction() {
        try {
            return JobAction.valueOf(this.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
