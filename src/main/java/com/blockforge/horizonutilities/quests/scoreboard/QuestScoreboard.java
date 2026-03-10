package com.blockforge.horizonutilities.quests.scoreboard;

import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the quest scoreboard for a single player.
 */
public class QuestScoreboard {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final QuestManager questManager;
    private final ScoreboardConfig config;

    public QuestScoreboard(QuestManager questManager, ScoreboardConfig config) {
        this.questManager = questManager;
        this.config = config;
    }

    /**
     * Build and apply the quest scoreboard to a player.
     */
    public void render(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("hquests", Criteria.DUMMY,
            MM.deserialize(config.getTitle()));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<Component> renderedLines = buildLines(player);

        // Scoreboard lines are numbered in reverse (higher score = higher on board)
        int score = renderedLines.size();
        for (Component line : renderedLines) {
            // Each line needs a unique entry — use invisible color codes as spacers
            String entry = createUniqueEntry(board, score);
            var team = board.registerNewTeam("line" + score);
            team.prefix(line);
            team.addEntry(entry);
            obj.getScore(entry).setScore(score);
            score--;
        }

        player.setScoreboard(board);
    }

    private List<Component> buildLines(Player player) {
        List<ActiveQuest> allQuests = questManager.getPlayerQuests(player.getUniqueId());
        List<Component> result = new ArrayList<>();

        for (String lineTemplate : config.getLines()) {
            if (lineTemplate.isEmpty() || lineTemplate.equals("{blank}")) {
                result.add(Component.empty());
                continue;
            }

            if (lineTemplate.equals("{daily_quests}")) {
                addQuestLines(result, allQuests, QuestCategory.JOB_DAILY);
                addQuestLines(result, allQuests, QuestCategory.GENERAL_DAILY);
                continue;
            }
            if (lineTemplate.equals("{weekly_quests}")) {
                addQuestLines(result, allQuests, QuestCategory.WEEKLY);
                continue;
            }
            if (lineTemplate.equals("{challenge_quests}")) {
                addQuestLines(result, allQuests, QuestCategory.CHALLENGE);
                continue;
            }

            // Replace simple placeholders
            String resolved = lineTemplate
                .replace("{tier}", String.format("%.1f", questManager.getPlayerTier(player.getUniqueId())))
                .replace("{quests_completed}", String.valueOf(
                    allQuests.stream().filter(ActiveQuest::isCompleted).count()))
                .replace("{daily_reset}", questManager.getTimeUntilDailyReset())
                .replace("{weekly_reset}", questManager.getTimeUntilWeeklyReset());

            result.add(MM.deserialize(resolved));
        }

        return result;
    }

    private void addQuestLines(List<Component> result, List<ActiveQuest> allQuests, QuestCategory category) {
        List<ActiveQuest> catQuests = allQuests.stream()
            .filter(q -> q.getCategory() == category)
            .toList();

        if (catQuests.isEmpty()) {
            result.add(MM.deserialize("<dark_gray>  No quests</dark_gray>"));
            return;
        }

        int shown = 0;
        for (ActiveQuest quest : catQuests) {
            if (shown >= config.getMaxQuestsPerCategory()) break;
            result.add(formatQuestLine(quest));
            shown++;
        }

        if (catQuests.size() > config.getMaxQuestsPerCategory()) {
            int remaining = catQuests.size() - config.getMaxQuestsPerCategory();
            result.add(MM.deserialize("<dark_gray>  +" + remaining + " more...</dark_gray>"));
        }
    }

    private Component formatQuestLine(ActiveQuest quest) {
        String icon = config.getIcon(quest.getCategory().name());

        if (quest.isCompleted()) {
            String line = config.getCompleteFormat()
                .replace("{icon}", icon)
                .replace("{description}", getShortDescription(quest));
            return MM.deserialize("  " + line);
        }

        ActiveQuest.ActiveStep step = quest.getCurrentStep();
        if (step == null) return Component.empty();

        String line = config.getIncompleteFormat()
            .replace("{icon}", icon)
            .replace("{description}", getShortDescription(quest))
            .replace("{progress}", String.valueOf(step.getCurrentProgress()))
            .replace("{total}", String.valueOf(step.getTargetAmount()));

        return MM.deserialize("  " + line);
    }

    private String getShortDescription(ActiveQuest quest) {
        ActiveQuest.ActiveStep step = quest.isCompleted()
            ? quest.getSteps().get(quest.getSteps().size() - 1)
            : quest.getCurrentStep();
        if (step == null) return quest.getTemplateId();

        String desc = step.getDescription();
        // Truncate for scoreboard readability
        if (desc.length() > 28) desc = desc.substring(0, 25) + "...";
        return desc;
    }

    /**
     * Create a unique invisible entry for the scoreboard.
     * Uses color code combinations that are visually empty.
     */
    private String createUniqueEntry(Scoreboard board, int index) {
        // Use section symbol + color codes for unique invisible entries
        StringBuilder sb = new StringBuilder();
        String hex = Integer.toHexString(index);
        for (char c : hex.toCharArray()) {
            sb.append("\u00A7").append(c);
        }
        // Pad to ensure uniqueness
        sb.append("\u00A7r");
        return sb.toString();
    }
}
