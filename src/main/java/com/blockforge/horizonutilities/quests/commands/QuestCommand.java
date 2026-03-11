package com.blockforge.horizonutilities.quests.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.config.MessagesManager;
import com.blockforge.horizonutilities.quests.QuestActionType;
import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        sb.append("<green>");
        for (int i = 0; i < filled; i++) sb.append("\u2588");
        sb.append("</green><gray>");
        for (int i = filled; i < bars; i++) sb.append("\u2588");
        sb.append("</gray>");
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
            case "import" -> handleAdminImport(sender, args);
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
                        Placeholder.parsed("quest", quest.getQuestName()),
                        Placeholder.parsed("player", target.getName()),
                        Placeholder.parsed("difficulty", String.valueOf(difficulty)));
                } else {
                    msg.send(sender, "quest-admin-generate-fail");
                }
            });
        });
    }

    /**
     * /quests admin import [player] [path]
     * Imports BeautyQuests YAML files as one-off quests for a player.
     * Path defaults to plugins/BeautyQuests/quests/
     */
    private void handleAdminImport(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg.format("quest-admin-import-usage"));
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }

        // Determine BQ quest directory
        File bqDir;
        if (args.length >= 4) {
            bqDir = new File(args[3]);
        } else {
            bqDir = new File(plugin.getDataFolder().getParentFile(), "BeautyQuests/quests");
        }

        if (!bqDir.exists() || !bqDir.isDirectory()) {
            sender.sendMessage(msg.format("quest-admin-import-nodir",
                Placeholder.parsed("path", bqDir.getAbsolutePath())));
            return;
        }

        File[] questFiles = bqDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (questFiles == null || questFiles.length == 0) {
            sender.sendMessage(msg.format("quest-admin-import-empty"));
            return;
        }

        UUID uuid = target.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int imported = 0;
            int skipped = 0;

            for (File file : questFiles) {
                try {
                    ActiveQuest quest = parseBqQuest(file, uuid);
                    if (quest != null) {
                        questManager.getStorage().saveQuest(quest);
                        imported++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[Quests] Failed to import BQ file " + file.getName() + ": " + e.getMessage());
                    skipped++;
                }
            }

            // Refresh player's quests
            questManager.onPlayerJoin(uuid);

            int finalImported = imported;
            int finalSkipped = skipped;
            Bukkit.getScheduler().runTask(plugin, () ->
                sender.sendMessage(msg.format("quest-admin-import-done",
                    Placeholder.parsed("imported", String.valueOf(finalImported)),
                    Placeholder.parsed("skipped", String.valueOf(finalSkipped)),
                    Placeholder.parsed("player", target.getName())))
            );
        });
    }

    /**
     * Parse a single BeautyQuests YAML file into an ActiveQuest.
     * BQ format: manager.branches.0.stages.N → stageType, material/entity counts
     */
    private ActiveQuest parseBqQuest(File file, UUID playerUuid) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        String questName = yml.getString("name", file.getName().replace(".yml", ""));
        int bqId = yml.getInt("id", 0);

        // BQ stage type → our QuestActionType
        Map<String, QuestActionType> stageMap = Map.ofEntries(
            Map.entry("MINE", QuestActionType.BREAK),
            Map.entry("PLACE_BLOCKS", QuestActionType.PLACE),
            Map.entry("MOBS", QuestActionType.KILL),
            Map.entry("FISH", QuestActionType.FISH),
            Map.entry("CRAFT", QuestActionType.CRAFT),
            Map.entry("MELT", QuestActionType.SMELT),
            Map.entry("ENCHANT", QuestActionType.ENCHANT),
            Map.entry("BUCKET", QuestActionType.MILK),
            Map.entry("BREED", QuestActionType.BREED)
        );

        // Walk through branches → stages
        ConfigurationSection managerSec = yml.getConfigurationSection("manager");
        if (managerSec == null) return null;

        ConfigurationSection branchesSec = managerSec.getConfigurationSection("branches");
        if (branchesSec == null) return null;

        List<ActiveQuest.ActiveStep> steps = new ArrayList<>();
        double totalRewardMoney = 0;
        int totalRewardXp = 0;

        for (String branchKey : branchesSec.getKeys(false)) {
            ConfigurationSection branch = branchesSec.getConfigurationSection(branchKey);
            if (branch == null) continue;

            // Parse regular stages
            ConfigurationSection stagesSec = branch.getConfigurationSection("stages");
            if (stagesSec != null) {
                for (String stageKey : stagesSec.getKeys(false)) {
                    ConfigurationSection stage = stagesSec.getConfigurationSection(stageKey);
                    if (stage == null) continue;
                    ActiveQuest.ActiveStep step = parseBqStage(stage, steps.size(), stageMap);
                    if (step != null) steps.add(step);

                    // Extract rewards from stage
                    totalRewardMoney += parseBqRewardMoney(stage);
                    totalRewardXp += parseBqRewardXp(stage);
                }
            }

            // Parse ending stages
            ConfigurationSection endingsSec = branch.getConfigurationSection("endingStages");
            if (endingsSec != null) {
                for (String stageKey : endingsSec.getKeys(false)) {
                    ConfigurationSection stage = endingsSec.getConfigurationSection(stageKey);
                    if (stage == null) continue;
                    ActiveQuest.ActiveStep step = parseBqStage(stage, steps.size(), stageMap);
                    if (step != null) steps.add(step);

                    totalRewardMoney += parseBqRewardMoney(stage);
                    totalRewardXp += parseBqRewardXp(stage);
                }
            }
        }

        if (steps.isEmpty()) return null;

        // Also check top-level rewards
        totalRewardMoney += parseBqRewardMoney(yml);
        totalRewardXp += parseBqRewardXp(yml);

        // Default some rewards if BQ quest had none
        if (totalRewardMoney <= 0) totalRewardMoney = 100.0;
        if (totalRewardXp <= 0) totalRewardXp = 3;

        RewardDefinition rewards = new RewardDefinition(totalRewardMoney, 0, totalRewardXp, 0, List.of());
        String templateId = "bq_import_" + bqId;
        String periodKey = "imported-" + System.currentTimeMillis();
        long seed = System.nanoTime();

        return new ActiveQuest(
            -1, playerUuid, templateId, QuestCategory.GENERAL_DAILY,
            periodKey, seed, steps, rewards, 0, false, null, false,
            System.currentTimeMillis(), null, questName
        );
    }

    private ActiveQuest.ActiveStep parseBqStage(ConfigurationSection stage,
                                                  int stepIndex, Map<String, QuestActionType> stageMap) {
        String stageType = stage.getString("stageType");
        if (stageType == null) return null;

        QuestActionType action = stageMap.get(stageType.toUpperCase());
        if (action == null) return null; // Unsupported stage type (NPC, location, etc.)

        // Try to extract target and amount
        String target = null;
        int amount = stage.getInt("amount", 1);

        // BQ stores materials/mobs in various formats
        if (stage.contains("material")) {
            target = stage.getString("material");
        } else if (stage.contains("blockType")) {
            target = stage.getString("blockType");
        } else if (stage.contains("entityType")) {
            target = stage.getString("entityType");
        } else if (stage.isList("mobs")) {
            var mobs = stage.getMapList("mobs");
            if (!mobs.isEmpty()) {
                Object mobObj = mobs.get(0).get("mob");
                if (mobObj != null) target = String.valueOf(mobObj);
                Object amtObj = mobs.get(0).get("amount");
                if (amtObj instanceof Number n) amount = n.intValue();
            }
        } else if (stage.isList("blocks")) {
            var blocks = stage.getMapList("blocks");
            if (!blocks.isEmpty()) {
                Object matObj = blocks.get(0).get("material");
                if (matObj != null) target = String.valueOf(matObj);
                Object amtObj = blocks.get(0).get("amount");
                if (amtObj instanceof Number n) amount = n.intValue();
            }
        }

        // Normalize target name
        if (target != null) {
            target = target.toUpperCase().replace(' ', '_');
        }

        String description = stage.getString("customText",
            action.name().toLowerCase() + " " + amount + (target != null ? " " + target.toLowerCase().replace('_', ' ') : " items"));

        return new ActiveQuest.ActiveStep(
            -1, stepIndex, action, target, Math.max(1, amount),
            0, false, description
        );
    }

    private double parseBqRewardMoney(ConfigurationSection section) {
        if (!section.isList("rewards")) return 0;
        double total = 0;
        for (var rewardMap : section.getMapList("rewards")) {
            Object id = rewardMap.get("id");
            if ("money".equalsIgnoreCase(String.valueOf(id)) || "vault_money".equalsIgnoreCase(String.valueOf(id))) {
                Object amount = rewardMap.get("amount");
                if (amount instanceof Number n) total += n.doubleValue();
            }
        }
        return total;
    }

    private int parseBqRewardXp(ConfigurationSection section) {
        if (!section.isList("rewards")) return 0;
        int total = 0;
        for (var rewardMap : section.getMapList("rewards")) {
            Object id = rewardMap.get("id");
            if ("xp".equalsIgnoreCase(String.valueOf(id)) || "experience".equalsIgnoreCase(String.valueOf(id))) {
                Object amount = rewardMap.get("amount");
                if (amount instanceof Number n) total += n.intValue();
            }
        }
        return total;
    }
}
