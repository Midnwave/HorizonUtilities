package com.blockforge.horizonutilities.games.types;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.games.ChatGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.concurrent.ThreadLocalRandom;

public class MathGame extends ChatGame {

    private final String equation;
    private final int answer;

    public MathGame(HorizonUtilitiesPlugin plugin) {
        super(plugin);
        int max = plugin.getChatGamesConfig().getInt("math.max-number", 100);
        int a = ThreadLocalRandom.current().nextInt(1, max + 1);
        int b = ThreadLocalRandom.current().nextInt(1, max + 1);

        String[] ops = {"ADD", "SUBTRACT", "MULTIPLY"};
        var configOps = plugin.getChatGamesConfig().getStringList("math.operations");
        if (configOps.isEmpty()) configOps = java.util.List.of(ops);
        String op = configOps.get(ThreadLocalRandom.current().nextInt(configOps.size()));

        switch (op.toUpperCase()) {
            case "SUBTRACT" -> {
                // ensure positive result
                if (a < b) { int tmp = a; a = b; b = tmp; }
                this.equation = a + " - " + b;
                this.answer = a - b;
            }
            case "MULTIPLY" -> {
                // keep numbers smaller for multiply
                a = ThreadLocalRandom.current().nextInt(1, 20);
                b = ThreadLocalRandom.current().nextInt(1, 20);
                this.equation = a + " x " + b;
                this.answer = a * b;
            }
            case "DIVIDE" -> {
                b = ThreadLocalRandom.current().nextInt(1, 13);
                a = b * ThreadLocalRandom.current().nextInt(1, 13);
                this.equation = a + " / " + b;
                this.answer = a / b;
            }
            default -> {
                this.equation = a + " + " + b;
                this.answer = a + b;
            }
        }
    }

    @Override
    public String getTypeName() { return "Math"; }

    @Override
    public String getTypeKey() { return "math"; }

    @Override
    public Component getQuestion() {
        return plugin.getMessagesManager().format("game-math-prompt",
                Placeholder.unparsed("equation", equation));
    }

    @Override
    public boolean checkAnswer(String input) {
        try {
            return Integer.parseInt(input.trim()) == answer;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String getAnswer() { return String.valueOf(answer); }
}
