package com.blockforge.horizonutilities.quests.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.config.MessagesManager;
import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class QuestCommand implements CommandExecutor {

    private final HorizonUtilitiesPlugin plugin;
    private final QuestManager questManager;
    private final MessagesManager msg;

    public QuestCommand(HorizonUtilitiesPlugin plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.msg = plugin.getMessagesManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "player-only");
                return true;
            }
            showAllQuests(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "daily" -> showCategory(sender, QuestCategory.GENERAL_DAILY);
            case "jobs" -> showCategory(sender, QuestCategory.JOB_DAILY);
            case "weekly" -> showCategory(sender, QuestCategory.WEEKLY);
            case "challenges" -> showCategory(sender, QuestCategory.CHALLENGE);
            case "history" -> showHistory(sender);
            case "admin" -> handleAdmin(sender, args);
            default -> msg.send(sender, "quest-usage");
        }
        return true;
    }

    private void showAllQuests(Player player) {
        List<ActiveQuest> quests = questManager.getPlayerQuests(player.getUniqueId());
        if (quests.isEmpty()) {
            msg.send(player, "quest-none-active");
            return;
        }

        double tier = questManager.getPlayerTier(player.getUniqueId());
        player.sendMessage(msg.format("quest-header"));
        player.sendMessage(msg.format("quest-tier",
            Placeholder.parsed("tier", String.format("%.1f", tier))));

        for (QuestCategory cat : QuestCategory.values()) {
            List<ActiveQuest> catQuests = quests.stream()
                .filter(q -> q.getCategory() == cat)
                .toList();
            if (catQuests.isEmpty()) continue;

            displayCategory(player, cat, catQuests);
        }

        player.sendMessage(msg.format("quest-footer"));
    }

    private void showCategory(CommandSender sender, QuestCategory category) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }

        List<ActiveQuest> catQuests = questManager.getPlayerQuests(player.getUniqueId(), category);
        if (catQuests.isEmpty()) {
            msg.send(player, "quest-none-active");
            return;
        }

        displayCategory(player, category, catQuests);
    }

    private void displayCategory(Player player, QuestCategory cat, List<ActiveQuest> quests) {
        long completed = quests.stream().filter(ActiveQuest::isCompleted).count();
        player.sendMessage(msg.format("quest-category-header",
            Placeholder.parsed("icon", cat.getIcon()),
            Placeholder.parsed("category", cat.getDisplayName()),
            Placeholder.parsed("completed", String.valueOf(completed)),
            Placeholder.parsed("total", String.valueOf(quests.size()))));

        for (ActiveQuest quest : quests) {
            displayQuest(player, quest);
        }
    }

    private void displayQuest(Player player, ActiveQuest quest) {
        if (quest.isCompleted()) {
            player.sendMessage(msg.format("quest-line-complete",
                Placeholder.parsed("description", quest.getTemplateId().replace('_', ' ').replace('-', ' '))));
            return;
        }

        ActiveQuest.ActiveStep step = quest.getCurrentStep();
        if (step == null) return;

        int stepNum = quest.getCurrentStepIndex() + 1;
        int totalSteps = quest.getTotalSteps();

        player.sendMessage(msg.format("quest-line-incomplete",
            Placeholder.parsed("description", step.getDescription()),
            Placeholder.parsed("step", String.valueOf(stepNum)),
            Placeholder.parsed("steps", String.valueOf(totalSteps))));

        String bar = buildProgressBar(step.getCurrentProgress(), step.getTargetAmount(), 10);
        player.sendMessage(msg.format("quest-line-progress",
            Placeholder.parsed("bar", bar),
            Placeholder.parsed("progress", String.valueOf(step.getCurrentProgress())),
            Placeholder.parsed("total", String.valueOf(step.getTargetAmount()))));
    }

    private String buildProgressBar(int current, int max, int bars) {
        int filled = max > 0 ? (int) Math.round((double) current / max * bars) : 0;
        filled = Math.min(filled, bars);

        StringBuilder sb = new StringBuilder();
        sb.append("\u00A7a");
        for (int i = 0; i < filled; i++) sb.append("\u2588");
        sb.append("\u00A77");
        for (int i = filled; i < bars; i++) sb.append("\u2588");
        return sb.toString();
    }

    private void showHistory(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }

        int total = questManager.getTotalCompleted(player.getUniqueId());
        msg.send(player, "quest-history", Placeholder.parsed("count", String.valueOf(total)));
    }

    // ========== ADMIN ==========

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("horizonutilities.quests.admin")) {
            msg.send(sender, "quest-no-permission");
            return;
        }

        if (args.length < 2) {
            msg.send(sender, "quest-admin-usage");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "reload" -> {
                questManager.reload();
                msg.send(sender, "quest-admin-reload");
            }
            case "reset" -> handleAdminReset(sender, args);
            case "settier" -> handleAdminSetTier(sender, args);
            case "generate" -> handleAdminGenerate(sender, args);
            default -> msg.send(sender, "quest-admin-usage");
        }
    }

    private void handleAdminReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg.send(sender, "quest-admin-usage");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }
        questManager.adminResetPlayer(target.getUniqueId());
        msg.send(sender, "quest-admin-reset", Placeholder.parsed("player", target.getName()));
    }

    private void handleAdminSetTier(CommandSender sender, String[] args) {
        if (args.length < 4) {
            msg.send(sender, "quest-admin-usage");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }
        try {
            double tier = Double.parseDouble(args[3]);
            questManager.adminSetTier(target.getUniqueId(), tier);
            msg.send(sender, "quest-admin-settier",
                Placeholder.parsed("player", target.getName()),
                Placeholder.parsed("tier", String.valueOf(tier)));
        } catch (NumberFormatException e) {
            msg.send(sender, "quest-admin-invalid-tier");
        }
    }

    private void handleAdminGenerate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            msg.send(sender, "quest-admin-usage");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }

        QuestCategory category;
        try {
            category = QuestCategory.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            msg.send(sender, "quest-admin-invalid-category");
            return;
        }

        double difficulty;
        try {
            difficulty = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            msg.send(sender, "quest-admin-invalid-difficulty");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ActiveQuest quest = questManager.adminGenerateQuest(target.getUniqueId(), category, difficulty);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (quest != null) {
                    msg.send(sender, "quest-admin-generate-success",
                        Placeholder.parsed("quest", quest.getTemplateId()),
                        Placeholder.parsed("player", target.getName()),
                        Placeholder.parsed("difficulty", String.valueOf(difficulty)));
                } else {
                    msg.send(sender, "quest-admin-generate-fail");
                }
            });
        });
    }
}
