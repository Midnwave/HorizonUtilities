package com.blockforge.horizonutilities.games.types;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.games.ChatGame;
import com.blockforge.horizonutilities.games.content.WordList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnscrambleGame extends ChatGame {

    private final String word;
    private final String scrambled;

    public UnscrambleGame(HorizonUtilitiesPlugin plugin) {
        super(plugin);
        this.word = WordList.randomWord();
        this.scrambled = scramble(word);
    }

    private String scramble(String input) {
        List<Character> chars = new ArrayList<>();
        for (char c : input.toCharArray()) chars.add(c);
        // keep shuffling until it's different
        String result;
        do {
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            result = sb.toString();
        } while (result.equals(input) && input.length() > 1);
        return result;
    }

    @Override
    public String getTypeName() { return "Unscramble"; }

    @Override
    public String getTypeKey() { return "unscramble"; }

    @Override
    public Component getQuestion() {
        return plugin.getMessagesManager().format("game-unscramble-prompt",
                Placeholder.unparsed("scrambled", scrambled));
    }

    @Override
    public boolean checkAnswer(String input) {
        return input.equalsIgnoreCase(word);
    }

    @Override
    public String getAnswer() { return word; }
}
