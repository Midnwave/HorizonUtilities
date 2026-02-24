package com.blockforge.horizonutilities.games.types;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.games.ChatGame;
import com.blockforge.horizonutilities.games.content.WordList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class UnreverseGame extends ChatGame {

    private final String word;
    private final String reversed;

    public UnreverseGame(HorizonUtilitiesPlugin plugin) {
        super(plugin);
        this.word = WordList.randomWord();
        this.reversed = new StringBuilder(word).reverse().toString();
    }

    @Override
    public String getTypeName() { return "Unreverse"; }

    @Override
    public String getTypeKey() { return "unreverse"; }

    @Override
    public Component getQuestion() {
        return plugin.getMessagesManager().format("game-unreverse-prompt",
                Placeholder.unparsed("reversed", reversed));
    }

    @Override
    public boolean checkAnswer(String input) {
        return input.equalsIgnoreCase(word);
    }

    @Override
    public String getAnswer() { return word; }
}
