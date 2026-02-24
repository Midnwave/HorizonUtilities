package com.blockforge.horizonutilities.games.types;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.games.ChatGame;
import com.blockforge.horizonutilities.games.content.WordList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.concurrent.ThreadLocalRandom;

public class RetypeGame extends ChatGame {

    private final String text;

    public RetypeGame(HorizonUtilitiesPlugin plugin) {
        super(plugin);
        boolean useMcNames = plugin.getChatGamesConfig().getBoolean("retype.use-minecraft-names", true);
        boolean useRandom = plugin.getChatGamesConfig().getBoolean("retype.use-random-letters", true);

        if (useMcNames && (!useRandom || ThreadLocalRandom.current().nextBoolean())) {
            this.text = WordList.randomWord();
        } else {
            int minLen = plugin.getChatGamesConfig().getInt("retype.min-length", 5);
            int maxLen = plugin.getChatGamesConfig().getInt("retype.max-length", 12);
            int len = ThreadLocalRandom.current().nextInt(minLen, maxLen + 1);
            this.text = generateRandom(len);
        }
    }

    private String generateRandom(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public String getTypeName() { return "Retype"; }

    @Override
    public String getTypeKey() { return "retype"; }

    @Override
    public Component getQuestion() {
        return plugin.getMessagesManager().format("game-retype-prompt",
                Placeholder.unparsed("text", text));
    }

    @Override
    public boolean checkAnswer(String input) {
        return input.equals(text);
    }

    @Override
    public String getAnswer() { return text; }
}
