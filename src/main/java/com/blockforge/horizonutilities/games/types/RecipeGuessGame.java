package com.blockforge.horizonutilities.games.types;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.games.ChatGame;
import com.blockforge.horizonutilities.games.content.RecipeData;
import net.kyori.adventure.text.Component;

public class RecipeGuessGame extends ChatGame {

    private final RecipeData.RecipeEntry recipe;
    private final String answerName;

    public RecipeGuessGame(HorizonUtilitiesPlugin plugin) {
        super(plugin);
        this.recipe = RecipeData.randomRecipe();
        if (recipe != null) {
            this.answerName = recipe.result().getType().name().toLowerCase().replace('_', ' ');
        } else {
            this.answerName = "unknown";
        }
    }

    @Override
    public String getTypeName() { return "Recipe Guess"; }

    @Override
    public String getTypeKey() { return "recipe-guess"; }

    @Override
    public Component getQuestion() {
        if (recipe == null) {
            return Component.text("No recipes loaded!");
        }
        return RecipeData.buildGridMessage(recipe);
    }

    @Override
    public boolean checkAnswer(String input) {
        if (recipe == null) return false;
        String cleaned = input.toLowerCase().trim();
        return cleaned.equals(answerName)
                || cleaned.equals(answerName.replace(" ", "_"))
                || cleaned.equals(recipe.result().getType().name().toLowerCase());
    }

    @Override
    public String getAnswer() { return answerName; }
}
