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
        // scramble each word individually, preserving spaces between words
        String[] words = input.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            result.append(scrambleWord(words[i]));
        }
        return result.toString();
    }

    private String scrambleWord(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        String result;
        do {
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            result = sb.toString();
        } while (result.equalsIgnoreCase(word) && word.length() > 1);
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
