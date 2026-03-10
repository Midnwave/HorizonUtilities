package com.blockforge.horizonutilities.quests.model;

import com.blockforge.horizonutilities.quests.QuestActionType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A single step within a quest template.
 * Steps are sequential — the player must complete each step to advance.
 */
public class QuestStep {

    private final QuestActionType actionType;
    private final List<String> targets;
    private final String descriptionTemplate;
    private final int baseAmount;
    private final int minAmount;
    private final int maxAmount;

    public QuestStep(QuestActionType actionType, List<String> targets,
                     String descriptionTemplate, int baseAmount, int minAmount, int maxAmount) {
        this.actionType = actionType;
        this.targets = targets;
        this.descriptionTemplate = descriptionTemplate;
        this.baseAmount = baseAmount;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public QuestActionType getActionType()   { return actionType; }
    public List<String> getTargets()         { return targets; }
    public String getDescriptionTemplate()   { return descriptionTemplate; }
    public int getBaseAmount()               { return baseAmount; }
    public int getMinAmount()                { return minAmount; }
    public int getMaxAmount()                { return maxAmount; }

    /**
     * Format the description with the resolved target and amount.
     */
    public String formatDescription(String resolvedTarget, int scaledAmount) {
        String pretty = resolvedTarget.replace('_', ' ').toLowerCase();
        return descriptionTemplate
                .replace("{amount}", String.valueOf(scaledAmount))
                .replace("{target}", pretty);
    }

    public static QuestStep fromConfig(ConfigurationSection section) {
        String actionStr = section.getString("action", "BREAK");
        QuestActionType action;
        try {
            action = QuestActionType.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            action = QuestActionType.BREAK;
        }

        List<String> targets = section.getStringList("targets");
        if (targets.isEmpty()) {
            String single = section.getString("target");
            if (single != null) targets = List.of(single);
        }

        String desc = section.getString("description", "Complete task");
        int baseAmount = section.getInt("base-amount", 32);

        List<Integer> range = section.getIntegerList("amount-range");
        int minAmount = range.size() >= 1 ? range.get(0) : baseAmount / 2;
        int maxAmount = range.size() >= 2 ? range.get(1) : baseAmount * 4;

        return new QuestStep(action, targets, desc, baseAmount, minAmount, maxAmount);
    }
}
