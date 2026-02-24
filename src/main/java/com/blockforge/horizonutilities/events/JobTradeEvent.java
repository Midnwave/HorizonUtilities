package com.blockforge.horizonutilities.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired for each participant when a player-to-player trade completes successfully.
 * Used by {@link com.blockforge.horizonutilities.jobs.quests.objectives.JobTradeObjective}
 * to increment PikaMug/Quests objective progress.
 */
public class JobTradeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public JobTradeEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
