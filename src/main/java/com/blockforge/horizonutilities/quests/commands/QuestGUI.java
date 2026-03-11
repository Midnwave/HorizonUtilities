package com.blockforge.horizonutilities.quests.commands;

import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Chest GUI for viewing quests with full step detail and rewards.
 */
public class QuestGUI implements Listener {

    private static final Component TITLE = Component.text("Your Quests")
        .color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD);

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    // Category row start slots (column 0 of each row)
    private static final int[] CATEGORY_ROWS = {9, 18, 27, 36};
    private static final QuestCategory[] CATEGORY_ORDER = {
        QuestCategory.JOB_DAILY, QuestCategory.GENERAL_DAILY,
        QuestCategory.WEEKLY, QuestCategory.CHALLENGE
    };

    // Category glass pane colors
    private static final Map<QuestCategory, Material> CATEGORY_GLASS = new EnumMap<>(QuestCategory.class);
    static {
        CATEGORY_GLASS.put(QuestCategory.JOB_DAILY, Material.LIME_STAINED_GLASS_PANE);
        CATEGORY_GLASS.put(QuestCategory.GENERAL_DAILY, Material.YELLOW_STAINED_GLASS_PANE);
        CATEGORY_GLASS.put(QuestCategory.WEEKLY, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        CATEGORY_GLASS.put(QuestCategory.CHALLENGE, Material.MAGENTA_STAINED_GLASS_PANE);
    }

    // Action type -> display material for quest items
    private static final Map<String, Material> ACTION_MATERIALS = new HashMap<>();
    static {
        ACTION_MATERIALS.put("BREAK", Material.DIAMOND_PICKAXE);
        ACTION_MATERIALS.put("PLACE", Material.BRICKS);
        ACTION_MATERIALS.put("KILL", Material.IRON_SWORD);
        ACTION_MATERIALS.put("FISH", Material.FISHING_ROD);
        ACTION_MATERIALS.put("CRAFT", Material.CRAFTING_TABLE);
        ACTION_MATERIALS.put("SMELT", Material.FURNACE);
        ACTION_MATERIALS.put("BREW", Material.BREWING_STAND);
        ACTION_MATERIALS.put("ENCHANT", Material.ENCHANTING_TABLE);
        ACTION_MATERIALS.put("REPAIR", Material.ANVIL);
        ACTION_MATERIALS.put("TAME", Material.LEAD);
        ACTION_MATERIALS.put("SHEAR", Material.SHEARS);
        ACTION_MATERIALS.put("MILK", Material.MILK_BUCKET);
        ACTION_MATERIALS.put("FARM", Material.WHEAT);
        ACTION_MATERIALS.put("EAT", Material.COOKED_BEEF);
        ACTION_MATERIALS.put("BREED", Material.WHEAT_SEEDS);
        ACTION_MATERIALS.put("EXPLORE_CHUNK", Material.FILLED_MAP);
        ACTION_MATERIALS.put("EXPLORE_DISTANCE", Material.COMPASS);
    }

    private final QuestManager questManager;

    /** Track open quest GUIs to prevent click exploits */
    private final Set<UUID> openGuis = Collections.newSetFromMap(new WeakHashMap<>());

    public QuestGUI(QuestManager questManager) {
        this.questManager = questManager;
    }

    /**
     * Open the quest GUI for a player.
     */
    public void open(Player player) {
        open(player, null);
    }

    /**
     * Open the quest GUI filtered to a specific category (null = all).
     */
    public void open(Player player, QuestCategory filterCategory) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        List<ActiveQuest> allQuests = questManager.getPlayerQuests(player.getUniqueId());
        double tier = questManager.getPlayerTier(player.getUniqueId());

        // Top row: filler glass + player head with tier
        ItemStack filler = makeGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        // Player head at slot 4 with tier info
        inv.setItem(4, makePlayerHead(player, tier, allQuests));

        // Bottom row: filler
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Category rows
        for (int c = 0; c < CATEGORY_ORDER.length; c++) {
            QuestCategory cat = CATEGORY_ORDER[c];
            int rowStart = CATEGORY_ROWS[c];

            // Skip category if filtering and doesn't match
            if (filterCategory != null && filterCategory != cat) {
                for (int col = 0; col < 9; col++) {
                    inv.setItem(rowStart + col, filler);
                }
                continue;
            }

            List<ActiveQuest> catQuests = allQuests.stream()
                .filter(q -> q.getCategory() == cat)
                .toList();

            // Category label at column 0
            inv.setItem(rowStart, makeCategoryLabel(cat, catQuests));

            // Quest items starting at column 1
            for (int i = 0; i < Math.min(catQuests.size(), 7); i++) {
                inv.setItem(rowStart + 1 + i, makeQuestItem(catQuests.get(i)));
            }

            // Fill remaining slots in row with dark glass
            for (int col = 1 + catQuests.size(); col < 9; col++) {
                if (col < 8) {
                    inv.setItem(rowStart + col, filler);
                } else {
                    inv.setItem(rowStart + col, makeGlass(
                        CATEGORY_GLASS.getOrDefault(cat, Material.GRAY_STAINED_GLASS_PANE), " "));
                }
            }
        }

        openGuis.add(player.getUniqueId());
        player.openInventory(inv);
    }

    // ===== Item builders =====

    private ItemStack makePlayerHead(Player player, double tier, List<ActiveQuest> quests) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName() + "'s Quests")
            .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreText("Tier: ", NamedTextColor.GRAY)
            .append(Component.text(String.format("%.1f", tier)).color(NamedTextColor.AQUA)));

        long incomplete = quests.stream().filter(q -> !q.isCompleted()).count();
        long completed = quests.stream().filter(ActiveQuest::isCompleted).count();

        lore.add(loreText("Active: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(incomplete)).color(NamedTextColor.WHITE)));
        lore.add(loreText("Completed: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(completed)).color(NamedTextColor.GREEN)));
        lore.add(Component.empty());

        String dailyReset = questManager.getTimeUntilDailyReset();
        String weeklyReset = questManager.getTimeUntilWeeklyReset();
        lore.add(loreText("Daily Reset: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(dailyReset).color(NamedTextColor.YELLOW)));
        lore.add(loreText("Weekly Reset: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(weeklyReset).color(NamedTextColor.YELLOW)));

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack makeCategoryLabel(QuestCategory cat, List<ActiveQuest> quests) {
        Material glass = CATEGORY_GLASS.getOrDefault(cat, Material.GRAY_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(glass);
        ItemMeta meta = item.getItemMeta();

        long done = quests.stream().filter(ActiveQuest::isCompleted).count();
        NamedTextColor catColor = switch (cat) {
            case JOB_DAILY -> NamedTextColor.GREEN;
            case GENERAL_DAILY -> NamedTextColor.YELLOW;
            case WEEKLY -> NamedTextColor.AQUA;
            case CHALLENGE -> NamedTextColor.LIGHT_PURPLE;
        };

        meta.displayName(Component.text(cat.getIcon() + " " + cat.getDisplayName())
            .color(catColor).decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(loreText(done + "/" + quests.size() + " completed", NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeQuestItem(ActiveQuest quest) {
        boolean completed = quest.isCompleted();

        // Pick material based on primary action, or lime dye if completed
        Material mat;
        if (completed) {
            mat = Material.LIME_DYE;
        } else {
            ActiveQuest.ActiveStep current = quest.getCurrentStep();
            String actionName = current != null ? current.getActionType().name() : "BREAK";
            mat = ACTION_MATERIALS.getOrDefault(actionName, Material.PAPER);
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // Quest name
        NamedTextColor nameColor = completed ? NamedTextColor.GREEN : NamedTextColor.GOLD;
        meta.displayName(Component.text(quest.getQuestName())
            .color(nameColor).decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        // Enchant glint for completed
        if (completed) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();

        // Category + status
        NamedTextColor catColor = switch (quest.getCategory()) {
            case JOB_DAILY -> NamedTextColor.GREEN;
            case GENERAL_DAILY -> NamedTextColor.YELLOW;
            case WEEKLY -> NamedTextColor.AQUA;
            case CHALLENGE -> NamedTextColor.LIGHT_PURPLE;
        };
        String statusText = completed ? "Completed!" : "In Progress";
        NamedTextColor statusColor = completed ? NamedTextColor.GREEN : NamedTextColor.WHITE;

        lore.add(Component.text(quest.getCategory().getDisplayName())
            .color(catColor).decoration(TextDecoration.ITALIC, false)
            .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(statusText).color(statusColor)));

        // Job tag if applicable
        if (quest.getJobId() != null) {
            lore.add(loreText("Job: " + capitalize(quest.getJobId()), NamedTextColor.DARK_AQUA));
        }

        lore.add(Component.empty());

        // All steps with progress
        lore.add(loreText("Steps:", NamedTextColor.WHITE).decorate(TextDecoration.UNDERLINED));
        List<ActiveQuest.ActiveStep> steps = quest.getSteps();
        int currentIdx = quest.getCurrentStepIndex();

        for (int i = 0; i < steps.size(); i++) {
            ActiveQuest.ActiveStep step = steps.get(i);
            Component stepLine;

            if (step.isCompleted()) {
                // Completed step: green checkmark
                stepLine = Component.text("  \u2714 ").color(NamedTextColor.GREEN)
                    .append(Component.text(step.getDescription()).color(NamedTextColor.GRAY)
                        .decorate(TextDecoration.STRIKETHROUGH))
                    .append(Component.text(" " + step.getTargetAmount() + "/" + step.getTargetAmount())
                        .color(NamedTextColor.DARK_GREEN));
            } else if (i == currentIdx) {
                // Current step: yellow arrow with progress bar
                String progressBar = buildLoreProgressBar(step.getCurrentProgress(), step.getTargetAmount());
                stepLine = Component.text("  \u25B6 ").color(NamedTextColor.YELLOW)
                    .append(Component.text(step.getDescription()).color(NamedTextColor.WHITE))
                    .append(Component.text(" " + step.getCurrentProgress() + "/" + step.getTargetAmount())
                        .color(NamedTextColor.YELLOW));
                lore.add(stepLine.decoration(TextDecoration.ITALIC, false));
                // Progress bar on next line
                lore.add(Component.text("    " + progressBar).decoration(TextDecoration.ITALIC, false));
                continue;
            } else {
                // Future step: gray lock
                stepLine = Component.text("  \u2022 ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(step.getDescription()).color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(" 0/" + step.getTargetAmount())
                        .color(NamedTextColor.DARK_GRAY));
            }

            lore.add(stepLine.decoration(TextDecoration.ITALIC, false));
        }

        // Rewards section
        RewardDefinition rewards = quest.getScaledRewards();
        if (rewards != null && rewards.hasAnyReward()) {
            lore.add(Component.empty());
            lore.add(loreText("Rewards:", NamedTextColor.GOLD).decorate(TextDecoration.UNDERLINED));

            if (rewards.getMoney() > 0) {
                lore.add(Component.text("  \u2726 ").color(NamedTextColor.GREEN)
                    .append(Component.text(String.format("$%.0f", rewards.getMoney())).color(NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (rewards.getJobXp() > 0) {
                lore.add(Component.text("  \u2726 ").color(NamedTextColor.AQUA)
                    .append(Component.text(String.format("%.0f Job XP", rewards.getJobXp())).color(NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (rewards.getXpLevels() > 0) {
                lore.add(Component.text("  \u2726 ").color(NamedTextColor.DARK_GREEN)
                    .append(Component.text(rewards.getXpLevels() + " XP Levels").color(NamedTextColor.DARK_GREEN))
                    .decoration(TextDecoration.ITALIC, false));
            }
            if (rewards.getGems() > 0) {
                lore.add(Component.text("  \u2726 ").color(NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(rewards.getGems() + " Gems").color(NamedTextColor.LIGHT_PURPLE))
                    .decoration(TextDecoration.ITALIC, false));
            }
            for (RewardDefinition.ItemReward itemReward : rewards.getItems()) {
                String itemName = capitalize(itemReward.material().name().replace('_', ' ').toLowerCase());
                lore.add(Component.text("  \u2726 ").color(NamedTextColor.WHITE)
                    .append(Component.text(itemReward.amount() + "x " + itemName).color(NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            }
        }

        // Overall progress at bottom
        if (!completed) {
            lore.add(Component.empty());
            int pct = (int)(quest.getOverallProgressPercent() * 100);
            lore.add(loreText("Overall: " + pct + "%", NamedTextColor.DARK_GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ===== Utilities =====

    private static ItemStack makeGlass(Material glass, String name) {
        ItemStack item = new ItemStack(glass);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static Component loreText(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }

    private static String buildLoreProgressBar(int current, int max) {
        int bars = 20;
        int filled = max > 0 ? (int) Math.round((double) current / max * bars) : 0;
        filled = Math.min(filled, bars);

        StringBuilder sb = new StringBuilder();
        sb.append("\u00a7a"); // green
        for (int i = 0; i < filled; i++) sb.append("|");
        sb.append("\u00a77"); // gray
        for (int i = filled; i < bars; i++) sb.append("|");
        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    // ===== Click handler =====

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openGuis.contains(player.getUniqueId())) return;

        Component title = event.getView().title();
        if (!TITLE.equals(title)) {
            openGuis.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
    }
}
