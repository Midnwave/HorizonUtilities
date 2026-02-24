package com.blockforge.horizonutilities.games;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;

public abstract class ChatGame {

    protected final HorizonUtilitiesPlugin plugin;
    private final long startTime;

    protected ChatGame(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.startTime = System.currentTimeMillis();
    }

    public abstract String getTypeName();

    public abstract String getTypeKey();

    public abstract Component getQuestion();

    public abstract boolean checkAnswer(String input);

    public abstract String getAnswer();

    public long getStartTime() { return startTime; }

    public long getElapsedMs() { return System.currentTimeMillis() - startTime; }
}
