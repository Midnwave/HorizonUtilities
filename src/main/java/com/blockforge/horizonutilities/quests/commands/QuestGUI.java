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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Tabbed, paginated quest GUI with alert indicators for new/available quests.
 *
 * Layout (54 slots, 6 rows):
 *   Row 1 (0-8):   [fill] [JOB_DAILY tab] [GENERAL_DAILY tab] [fill] [HEAD] [fill] [WEEKLY tab] [CHALLENGE tab] [fill]
 *   Rows 2-5 (9-44): Quest items for active tab (up to 28 per page)
 *   Row 6 (45-53):  [fill] [fill] [PREV] [fill] [PAGE] [fill] [NEXT] [fill] [CLOSE]
 */
public class QuestGUI implements Listener {

    private static final Component TITLE_PREFIX = Component.text("Quests")
        .color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD);

    private static final int SIZE = 54;
    private static final int QUESTS_PER_PAGE = 28; // rows 2-5 (slots 9-44, minus border columns)

    // Tab slots in row 1
    private static final int TAB_JOB_DAILY = 1;
    private static final int TAB_GENERAL_DAILY = 2;
    private static final int TAB_WEEKLY = 6;
    private static final int TAB_CHALLENGE = 7;
    private static final int SLOT_HEAD = 4;

    // Navigation slots in row 6
    private static final int SLOT_PREV = 47;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT = 51;
    private static final int SLOT_CLOSE = 53;

    // Quest content area: slots 10-16, 19-25, 28-34, 37-43 (7 per row, 4 rows = 28)
    private static final int[] QUEST_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final QuestCategory[] CATEGORY_ORDER = {
        QuestCategory.JOB_DAILY, QuestCategory.GENERAL_DAILY,
        QuestCategory.WEEKLY, QuestCategory.CHALLENGE
    };

    private static final Map<QuestCategory, Material> TAB_MATERIALS = new EnumMap<>(QuestCategory.class);
    static {
        TAB_MATERIALS.put(QuestCategory.JOB_DAILY, Material.IRON_PICKAXE);
        TAB_MATERIALS.put(QuestCategory.GENERAL_DAILY, Material.GOLDEN_SWORD);
        TAB_MATERIALS.put(QuestCategory.WEEKLY, Material.CLOCK);
        TAB_MATERIALS.put(QuestCategory.CHALLENGE, Material.NETHER_STAR);
    }

    private static final Map<QuestCategory, Material> CATEGORY_GLASS = new EnumMap<>(QuestCategory.class);
    static {
        CATEGORY_GLASS.put(QuestCategory.JOB_DAILY, Material.LIME_STAINED_GLASS_PANE);
        CATEGORY_GLASS.put(QuestCategory.GENERAL_DAILY, Material.YELLOW_STAINED_GLASS_PANE);
        CATEGORY_GLASS.put(QuestCategory.WEEKLY, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        CATEGORY_GLASS.put(QuestCategory.CHALLENGE, Material.MAGENTA_STAINED_GLASS_PANE);
    }

    private static final Map<QuestCategory, NamedTextColor> CATEGORY_COLORS = new EnumMap<>(QuestCategory.class);
    static {
        CATEGORY_COLORS.put(QuestCategory.JOB_DAILY, NamedTextColor.GREEN);
        CATEGORY_COLORS.put(QuestCategory.GENERAL_DAILY, NamedTextColor.YELLOW);
        CATEGORY_COLORS.put(QuestCategory.WEEKLY, NamedTextColor.AQUA);
        CATEGORY_COLORS.put(QuestCategory.CHALLENGE, NamedTextColor.LIGHT_PURPLE);
    }

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

    /** Per-player session state */
    private final Map<UUID, ViewSession> sessions = new HashMap<>();

    public QuestGUI(QuestManager questManager) {
        this.questManager = questManager;
    }

    // ===== Session tracking =====

    private static class ViewSession {
        QuestCategory activeTab;
        int page;
        List<ActiveQuest> allQuests;
        double tier;

        ViewSession(QuestCategory tab, List<ActiveQuest> quests, double tier) {
            this.activeTab = tab;
            this.page = 0;
            this.allQuests = quests;
            this.tier = tier;
        }

        List<ActiveQuest> getQuestsForTab() {
            return allQuests.stream()
                .filter(q -> q.getCategory() == activeTab)
                .toList();
        }

        long getIncompleteCount(QuestCategory cat) {
            return allQuests.stream()
                .filter(q -> q.getCategory() == cat && !q.isCompleted())
                .count();
        }

        int getMaxPage() {
            int count = getQuestsForTab().size();
            return Math.max(0, (count - 1) / QUESTS_PER_PAGE);
        }
    }

    // ===== Open methods =====

    public void open(Player player) {
        open(player, null);
    }

    public void open(Player player, QuestCategory filterCategory) {
        List<ActiveQuest> allQuests = questManager.getPlayerQuests(player.getUniqueId());
        double tier = questManager.getPlayerTier(player.getUniqueId());

        QuestCategory startTab = filterCategory != null ? filterCategory : QuestCategory.JOB_DAILY;
        ViewSession session = new ViewSession(startTab, allQuests, tier);
        sessions.put(player.getUniqueId(), session);

        renderAndOpen(player, session);
    }

    // ===== Rendering =====

    private void renderAndOpen(Player player, ViewSession session) {
        Component title = buildTitle(session.activeTab);
        Inventory inv = Bukkit.createInventory(null, SIZE, title);

        ItemStack filler = makeGlass(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack borderGlass = makeGlass(
            CATEGORY_GLASS.getOrDefault(session.activeTab, Material.GRAY_STAINED_GLASS_PANE), " ");

        // Fill everything with filler first
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // Row 1: tabs + head
        inv.setItem(0, borderGlass);
        inv.setItem(TAB_JOB_DAILY, makeTab(QuestCategory.JOB_DAILY, session));
        inv.setItem(TAB_GENERAL_DAILY, makeTab(QuestCategory.GENERAL_DAILY, session));
        inv.setItem(3, borderGlass);
        inv.setItem(SLOT_HEAD, makePlayerHead(player, session));
        inv.setItem(5, borderGlass);
        inv.setItem(TAB_WEEKLY, makeTab(QuestCategory.WEEKLY, session));
        inv.setItem(TAB_CHALLENGE, makeTab(QuestCategory.CHALLENGE, session));
        inv.setItem(8, borderGlass);

        // Rows 2-5: left/right borders + quest content
        for (int row = 1; row <= 4; row++) {
            int rowStart = row * 9;
            inv.setItem(rowStart, borderGlass);       // left border
            inv.setItem(rowStart + 8, borderGlass);   // right border
        }

        // Quest items
        List<ActiveQuest> tabQuests = session.getQuestsForTab();
        int startIdx = session.page * QUESTS_PER_PAGE;
        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            int questIdx = startIdx + i;
            if (questIdx < tabQuests.size()) {
                inv.setItem(QUEST_SLOTS[i], makeQuestItem(tabQuests.get(questIdx)));
            }
            // else leave as filler (already set)
        }

        // Row 6: navigation
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        inv.setItem(45, borderGlass);

        if (session.page > 0) {
            inv.setItem(SLOT_PREV, makeNavItem(Material.ARROW, "Previous Page", NamedTextColor.YELLOW));
        }

        inv.setItem(SLOT_PAGE_INFO, makePageInfo(session, tabQuests.size()));

        if (session.page < session.getMaxPage()) {
            inv.setItem(SLOT_NEXT, makeNavItem(Material.ARROW, "Next Page", NamedTextColor.YELLOW));
        }

        inv.setItem(SLOT_CLOSE, makeNavItem(Material.BARRIER, "Close", NamedTextColor.RED));

        player.openInventory(inv);
    }

    private Component buildTitle(QuestCategory tab) {
        NamedTextColor color = CATEGORY_COLORS.getOrDefault(tab, NamedTextColor.WHITE);
        return TITLE_PREFIX
            .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(tab.getIcon() + " " + tab.getDisplayName())
                .color(color).decorate(TextDecoration.BOLD));
    }

    // ===== Tab item =====

    private ItemStack makeTab(QuestCategory cat, ViewSession session) {
        boolean active = session.activeTab == cat;
        long incomplete = session.getIncompleteCount(cat);
        long total = session.allQuests.stream().filter(q -> q.getCategory() == cat).count();

        Material mat = active
            ? TAB_MATERIALS.getOrDefault(cat, Material.PAPER)
            : CATEGORY_GLASS.getOrDefault(cat, Material.GRAY_STAINED_GLASS_PANE);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor color = CATEGORY_COLORS.getOrDefault(cat, NamedTextColor.WHITE);

        String tabName = cat.getIcon() + " " + cat.getDisplayName();
        if (incomplete > 0 && !active) {
            tabName += " \u26A0"; // warning sign for available quests
        }

        meta.displayName(Component.text(tabName)
            .color(active ? color : NamedTextColor.GRAY)
            .decorate(active ? TextDecoration.BOLD : TextDecoration.OBFUSCATED) // bold if active
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.OBFUSCATED, false));

        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        long done = total - incomplete;
        lore.add(loreText(done + "/" + total + " completed", NamedTextColor.GRAY));

        if (incomplete > 0) {
            lore.add(Component.empty());
            lore.add(Component.text("\u25CF " + incomplete + " available")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        } else if (total > 0) {
            lore.add(Component.empty());
            lore.add(Component.text("\u2714 All completed!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        }

        if (!active) {
            lore.add(Component.empty());
            lore.add(loreText("Click to view", NamedTextColor.DARK_GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ===== Player head =====

    private ItemStack makePlayerHead(Player player, ViewSession session) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName() + "'s Quests")
            .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreText("Tier: ", NamedTextColor.GRAY)
            .append(Component.text(String.format("%.1f", session.tier)).color(NamedTextColor.AQUA)));

        long allIncomplete = session.allQuests.stream().filter(q -> !q.isCompleted()).count();
        long allCompleted = session.allQuests.stream().filter(ActiveQuest::isCompleted).count();

        lore.add(loreText("Active: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(allIncomplete)).color(NamedTextColor.WHITE)));
        lore.add(loreText("Completed: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(allCompleted)).color(NamedTextColor.GREEN)));
        lore.add(Component.empty());

        String dailyReset = questManager.getTimeUntilDailyReset();
        String weeklyReset = questManager.getTimeUntilWeeklyReset();
        lore.add(loreText("Daily Reset: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(dailyReset).color(NamedTextColor.YELLOW)));
        lore.add(loreText("Weekly Reset: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(weeklyReset).color(NamedTextColor.YELLOW)));

        // Per-category summary
        lore.add(Component.empty());
        for (QuestCategory cat : CATEGORY_ORDER) {
            long catTotal = session.allQuests.stream().filter(q -> q.getCategory() == cat).count();
            long catDone = session.allQuests.stream().filter(q -> q.getCategory() == cat && q.isCompleted()).count();
            NamedTextColor cc = CATEGORY_COLORS.getOrDefault(cat, NamedTextColor.WHITE);
            lore.add(Component.text(cat.getIcon() + " " + cat.getDisplayName() + ": ")
                .color(cc)
                .append(Component.text(catDone + "/" + catTotal).color(NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    // ===== Quest item (same rich display as before) =====

    private ItemStack makeQuestItem(ActiveQuest quest) {
        boolean completed = quest.isCompleted();

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

        NamedTextColor nameColor = completed ? NamedTextColor.GREEN : NamedTextColor.GOLD;
        meta.displayName(Component.text(quest.getQuestName())
            .color(nameColor).decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        if (completed) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();

        // Status
        NamedTextColor catColor = CATEGORY_COLORS.getOrDefault(quest.getCategory(), NamedTextColor.WHITE);
        String statusText = completed ? "Completed!" : "In Progress";
        NamedTextColor statusColor = completed ? NamedTextColor.GREEN : NamedTextColor.WHITE;

        lore.add(Component.text(quest.getCategory().getDisplayName())
            .color(catColor).decoration(TextDecoration.ITALIC, false)
            .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(statusText).color(statusColor)));

        if (quest.getJobId() != null) {
            lore.add(loreText("Job: " + capitalize(quest.getJobId()), NamedTextColor.DARK_AQUA));
        }

        lore.add(Component.empty());

        // Steps
        lore.add(loreText("Steps:", NamedTextColor.WHITE).decorate(TextDecoration.UNDERLINED));
        List<ActiveQuest.ActiveStep> steps = quest.getSteps();
        int currentIdx = quest.getCurrentStepIndex();

        for (int i = 0; i < steps.size(); i++) {
            ActiveQuest.ActiveStep step = steps.get(i);
            Component stepLine;

            if (step.isCompleted()) {
                stepLine = Component.text("  \u2714 ").color(NamedTextColor.GREEN)
                    .append(Component.text(step.getDescription()).color(NamedTextColor.GRAY)
                        .decorate(TextDecoration.STRIKETHROUGH))
                    .append(Component.text(" " + step.getTargetAmount() + "/" + step.getTargetAmount())
                        .color(NamedTextColor.DARK_GREEN));
            } else if (i == currentIdx) {
                String progressBar = buildLoreProgressBar(step.getCurrentProgress(), step.getTargetAmount());
                stepLine = Component.text("  \u25B6 ").color(NamedTextColor.YELLOW)
                    .append(Component.text(step.getDescription()).color(NamedTextColor.WHITE))
                    .append(Component.text(" " + step.getCurrentProgress() + "/" + step.getTargetAmount())
                        .color(NamedTextColor.YELLOW));
                lore.add(stepLine.decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("    " + progressBar).decoration(TextDecoration.ITALIC, false));
                continue;
            } else {
                stepLine = Component.text("  \u2022 ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(step.getDescription()).color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(" 0/" + step.getTargetAmount())
                        .color(NamedTextColor.DARK_GRAY));
            }

            lore.add(stepLine.decoration(TextDecoration.ITALIC, false));
        }

        // Rewards
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

        // Overall progress
        if (!completed) {
            lore.add(Component.empty());
            int pct = (int)(quest.getOverallProgressPercent() * 100);
            lore.add(loreText("Overall: " + pct + "%", NamedTextColor.DARK_GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ===== Navigation items =====

    private ItemStack makeNavItem(Material mat, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(color)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makePageInfo(ViewSession session, int totalQuests) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Page " + (session.page + 1) + "/" + (session.getMaxPage() + 1))
            .color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(loreText(totalQuests + " quest(s) in this category", NamedTextColor.GRAY));
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
        sb.append("\u00a7a");
        for (int i = 0; i < filled; i++) sb.append("|");
        sb.append("\u00a77");
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

        ViewSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        // Verify this is our GUI by checking title starts with "Quests"
        Component title = event.getView().title();
        if (title == null || !title.toString().contains("Quests")) {
            sessions.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        // Tab clicks
        switch (slot) {
            case TAB_JOB_DAILY -> switchTab(player, session, QuestCategory.JOB_DAILY);
            case TAB_GENERAL_DAILY -> switchTab(player, session, QuestCategory.GENERAL_DAILY);
            case TAB_WEEKLY -> switchTab(player, session, QuestCategory.WEEKLY);
            case TAB_CHALLENGE -> switchTab(player, session, QuestCategory.CHALLENGE);
            case SLOT_PREV -> {
                if (session.page > 0) {
                    session.page--;
                    renderAndOpen(player, session);
                }
            }
            case SLOT_NEXT -> {
                if (session.page < session.getMaxPage()) {
                    session.page++;
                    renderAndOpen(player, session);
                }
            }
            case SLOT_CLOSE -> {
                sessions.remove(player.getUniqueId());
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ViewSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        Component title = event.getView().title();
        if (title == null || !title.toString().contains("Quests")) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void switchTab(Player player, ViewSession session, QuestCategory newTab) {
        if (session.activeTab == newTab) return;
        session.activeTab = newTab;
        session.page = 0;
        renderAndOpen(player, session);
    }
}
