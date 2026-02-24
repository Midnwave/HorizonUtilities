package com.blockforge.horizonutilities.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player earns job income via chunk exploration or distance travel.
 * Used by {@link com.blockforge.horizonutilities.jobs.quests.objectives.JobExploreObjective}
 * to increment PikaMug/Quests objective progress.
 */
public class JobExploreEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public JobExploreEvent(Player player) {
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
