package com.blockforge.horizonutilities.quests.commands;

import com.blockforge.horizonutilities.quests.QuestActionType;
import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.generation.MaterialPools;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Step-by-step wizard GUI for admins to create and assign custom quests.
 * Flow: Pick Player → Pick Category → Edit Steps → Set Rewards → Confirm
 */
public class AdminQuestCreatorGUI implements Listener {

    private enum Page {
        PICK_PLAYER, PICK_CATEGORY, EDIT_STEPS,
        PICK_ACTION, PICK_TARGET, SET_AMOUNT,
        SET_REWARDS, CONFIRM
    }

    private static class StepDraft {
        QuestActionType action;
        String target;
        int amount = 16;
        String description;
    }

    private static class CreatorSession {
        Page currentPage = Page.PICK_PLAYER;
        UUID targetPlayer;
        String targetPlayerName;
        QuestCategory category;
        List<StepDraft> steps = new ArrayList<>();
        double rewardMoney = 100.0;
        double rewardJobXp = 50.0;
        int rewardXpLevels = 1;
        int rewardGems = 0;

        // Temp state for step being created
        StepDraft currentStep;
        int targetPage = 0;
        int playerPage = 0;
    }

    // Prefix for all inventory titles so we can identify our GUIs
    private static final String TITLE_PREFIX = "\u2726 ";

    private final QuestManager questManager;
    private final Map<UUID, CreatorSession> sessions = new HashMap<>();

    public AdminQuestCreatorGUI(QuestManager questManager) {
        this.questManager = questManager;
    }

    // =========================================================================
    // Public entry point
    // =========================================================================

    public void open(Player admin) {
        CreatorSession session = new CreatorSession();
        sessions.put(admin.getUniqueId(), session);
        openPlayerPicker(admin, session);
    }

    // =========================================================================
    // Page: PICK_PLAYER
    // =========================================================================

    private void openPlayerPicker(Player admin, CreatorSession session) {
        session.currentPage = Page.PICK_PLAYER;
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_PREFIX + "Select Player").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int start = session.playerPage * 28;
        int end = Math.min(start + 28, online.size());

        int[] slots = getContentSlots();
        for (int i = start; i < end && (i - start) < slots.length; i++) {
            Player target = online.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(target);
            }
            meta.displayName(Component.text(target.getName()).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Click to select").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            head.setItemMeta(meta);
            inv.setItem(slots[i - start], head);
        }

        // Navigation
        if (session.playerPage > 0) {
            inv.setItem(48, makeButton(Material.ARROW, "&ePrevious Page", null));
        }
        if (end < online.size()) {
            inv.setItem(50, makeButton(Material.ARROW, "&eNext Page", null));
        }
        inv.setItem(49, makeButton(Material.BARRIER, "&cCancel", null));

        admin.openInventory(inv);
    }

    // =========================================================================
    // Page: PICK_CATEGORY
    // =========================================================================

    private void openCategoryPicker(Player admin, CreatorSession session) {
        session.currentPage = Page.PICK_CATEGORY;
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(TITLE_PREFIX + "Select Category").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(10, makeCategoryItem(QuestCategory.JOB_DAILY, Material.LIME_DYE, "Job Daily"));
        inv.setItem(12, makeCategoryItem(QuestCategory.GENERAL_DAILY, Material.YELLOW_DYE, "General Daily"));
        inv.setItem(14, makeCategoryItem(QuestCategory.WEEKLY, Material.LIGHT_BLUE_DYE, "Weekly"));
        inv.setItem(16, makeCategoryItem(QuestCategory.CHALLENGE, Material.MAGENTA_DYE, "Challenge"));

        inv.setItem(22, makeButton(Material.ARROW, "&eBack", null));

        admin.openInventory(inv);
    }

    private ItemStack makeCategoryItem(QuestCategory cat, Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Click to select").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    // =========================================================================
    // Page: EDIT_STEPS (main editor)
    // =========================================================================

    private void openStepEditor(Player admin, CreatorSession session) {
        session.currentPage = Page.EDIT_STEPS;
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_PREFIX + "Quest Editor").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Info item at slot 4
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Quest Info").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("Player: ").color(NamedTextColor.GRAY)
                .append(Component.text(session.targetPlayerName).color(NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Category: ").color(NamedTextColor.GRAY)
                .append(Component.text(formatCategory(session.category)).color(NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Steps: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.steps.size())).color(NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Display existing steps in rows 1-3
        int[] contentSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < session.steps.size() && i < contentSlots.length; i++) {
            StepDraft step = session.steps.get(i);
            ItemStack stepItem = makeStepDisplayItem(step, i);
            inv.setItem(contentSlots[i], stepItem);
        }

        // Bottom row buttons
        if (session.steps.size() < 5) {
            inv.setItem(46, makeButton(Material.EMERALD, "&aAdd Step", "&7Click to add a new step"));
        }
        inv.setItem(48, makeButton(Material.GOLD_INGOT, "&6Set Rewards", "&7Money: $" + String.format("%.0f", session.rewardMoney)
                + "\n&7Job XP: " + String.format("%.0f", session.rewardJobXp)
                + "\n&7XP Levels: " + session.rewardXpLevels));
        inv.setItem(50, makeButton(Material.ARROW, "&eBack", null));

        if (!session.steps.isEmpty()) {
            inv.setItem(52, makeButton(Material.LIME_CONCRETE, "&a&lCreate Quest", "&7Click to finalize"));
        }

        admin.openInventory(inv);
    }

    private ItemStack makeStepDisplayItem(StepDraft step, int index) {
        Material displayMat = getDisplayMaterial(step.action, step.target);
        ItemStack item = new ItemStack(displayMat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Step " + (index + 1) + ": " + formatAction(step.action))
                .color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Target: ").color(NamedTextColor.GRAY)
                .append(Component.text(step.target != null ? formatName(step.target) : "Any").color(NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Amount: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(step.amount)).color(NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Shift+Click to remove").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // =========================================================================
    // Page: PICK_ACTION
    // =========================================================================

    private void openActionPicker(Player admin, CreatorSession session) {
        session.currentPage = Page.PICK_ACTION;
        session.currentStep = new StepDraft();

        Inventory inv = Bukkit.createInventory(null, 45,
                Component.text(TITLE_PREFIX + "Select Action Type").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Action types with representative items
        Object[][] actions = {
                {QuestActionType.BREAK, Material.IRON_PICKAXE, "Break/Mine"},
                {QuestActionType.PLACE, Material.BRICKS, "Place Blocks"},
                {QuestActionType.KILL, Material.IRON_SWORD, "Kill Mobs"},
                {QuestActionType.FISH, Material.FISHING_ROD, "Fish"},
                {QuestActionType.CRAFT, Material.CRAFTING_TABLE, "Craft Items"},
                {QuestActionType.SMELT, Material.FURNACE, "Smelt Items"},
                {QuestActionType.BREW, Material.BREWING_STAND, "Brew Potions"},
                {QuestActionType.ENCHANT, Material.ENCHANTING_TABLE, "Enchant Items"},
                {QuestActionType.FARM, Material.WHEAT, "Farm Crops"},
                {QuestActionType.BREED, Material.WHEAT_SEEDS, "Breed Animals"},
                {QuestActionType.TAME, Material.BONE, "Tame Animals"},
                {QuestActionType.SHEAR, Material.SHEARS, "Shear Animals"},
                {QuestActionType.MILK, Material.MILK_BUCKET, "Milk Cows"},
                {QuestActionType.EAT, Material.BREAD, "Eat Food"},
        };

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < actions.length && i < slots.length; i++) {
            QuestActionType action = (QuestActionType) actions[i][0];
            Material mat = (Material) actions[i][1];
            String name = (String) actions[i][2];

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(name).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(action.name()).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        inv.setItem(40, makeButton(Material.ARROW, "&eBack", null));
        admin.openInventory(inv);
    }

    // =========================================================================
    // Page: PICK_TARGET (paginated)
    // =========================================================================

    private void openTargetPicker(Player admin, CreatorSession session) {
        session.currentPage = Page.PICK_TARGET;

        List<String> pool = MaterialPools.getPoolForAction(session.currentStep.action);
        if (pool == null || pool.isEmpty()) {
            // No specific targets for this action — skip to amount
            session.currentStep.target = null;
            openAmountSetter(admin, session);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_PREFIX + "Select Target (" + formatAction(session.currentStep.action) + ")")
                        .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // "Any" option at slot 4
        ItemStack anyItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta anyMeta = anyItem.getItemMeta();
        anyMeta.displayName(Component.text("Any (No Specific Target)").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        anyItem.setItemMeta(anyMeta);
        inv.setItem(4, anyItem);

        int[] contentSlots = getContentSlots();
        int start = session.targetPage * contentSlots.length;
        int end = Math.min(start + contentSlots.length, pool.size());

        for (int i = start; i < end; i++) {
            String target = pool.get(i);
            Material displayMat = getDisplayMaterial(session.currentStep.action, target);
            ItemStack item = new ItemStack(displayMat);
            ItemMeta meta = item.getItemMeta();
            double diff = MaterialPools.getDifficulty(target);
            NamedTextColor diffColor = diff < 0.3 ? NamedTextColor.GREEN : diff < 0.6 ? NamedTextColor.YELLOW : NamedTextColor.RED;
            meta.displayName(Component.text(formatName(target)).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Difficulty: ").color(NamedTextColor.GRAY)
                            .append(Component.text(String.format("%.0f%%", diff * 100)).color(diffColor))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(target).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            inv.setItem(contentSlots[i - start], item);
        }

        // Navigation
        if (session.targetPage > 0) {
            inv.setItem(48, makeButton(Material.ARROW, "&ePrevious Page", null));
        }
        if (end < pool.size()) {
            inv.setItem(50, makeButton(Material.ARROW, "&eNext Page", null));
        }
        inv.setItem(49, makeButton(Material.BARRIER, "&cBack", null));

        admin.openInventory(inv);
    }

    // =========================================================================
    // Page: SET_AMOUNT
    // =========================================================================

    private void openAmountSetter(Player admin, CreatorSession session) {
        session.currentPage = Page.SET_AMOUNT;
        StepDraft step = session.currentStep;

        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(TITLE_PREFIX + "Set Amount").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Amount display in center
        inv.setItem(13, makeAmountDisplay(step));

        // Decrease buttons
        inv.setItem(10, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -64));
        inv.setItem(11, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -10));
        inv.setItem(12, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -1));

        // Increase buttons
        inv.setItem(14, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 1));
        inv.setItem(15, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 10));
        inv.setItem(16, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 64));

        // Bottom
        inv.setItem(21, makeButton(Material.ARROW, "&eBack", null));
        inv.setItem(23, makeButton(Material.LIME_CONCRETE, "&aConfirm Step", null));

        admin.openInventory(inv);
    }

    private ItemStack makeAmountDisplay(StepDraft step) {
        Material displayMat = getDisplayMaterial(step.action, step.target);
        ItemStack item = new ItemStack(displayMat, Math.max(1, Math.min(64, step.amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Amount: " + step.amount).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Action: " + formatAction(step.action)).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (step.target != null) {
            lore.add(Component.text("Target: " + formatName(step.target)).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeAdjustButton(Material mat, int delta) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String prefix = delta > 0 ? "+" : "";
        NamedTextColor color = delta > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        meta.displayName(Component.text(prefix + delta).color(color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    // =========================================================================
    // Page: SET_REWARDS
    // =========================================================================

    private void openRewardEditor(Player admin, CreatorSession session) {
        session.currentPage = Page.SET_REWARDS;
        Inventory inv = Bukkit.createInventory(null, 45,
                Component.text(TITLE_PREFIX + "Set Rewards").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Money row (row 1)
        inv.setItem(9, makeRewardLabel(Material.GOLD_INGOT, "&6Money", "$" + String.format("%.0f", session.rewardMoney)));
        inv.setItem(10, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -100));
        inv.setItem(11, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -10));
        inv.setItem(14, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 10));
        inv.setItem(15, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 100));
        inv.setItem(16, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 500));

        // Job XP row (row 2)
        inv.setItem(18, makeRewardLabel(Material.EXPERIENCE_BOTTLE, "&bJob XP", String.format("%.0f", session.rewardJobXp)));
        inv.setItem(19, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -50));
        inv.setItem(20, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -10));
        inv.setItem(23, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 10));
        inv.setItem(24, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 50));
        inv.setItem(25, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 200));

        // XP Levels row (row 3)
        inv.setItem(27, makeRewardLabel(Material.ENCHANTING_TABLE, "&dXP Levels", String.valueOf(session.rewardXpLevels)));
        inv.setItem(28, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -5));
        inv.setItem(29, makeAdjustButton(Material.RED_STAINED_GLASS_PANE, -1));
        inv.setItem(32, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 1));
        inv.setItem(33, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 5));
        inv.setItem(34, makeAdjustButton(Material.LIME_STAINED_GLASS_PANE, 10));

        // Bottom
        inv.setItem(39, makeButton(Material.ARROW, "&eBack", null));
        inv.setItem(41, makeButton(Material.LIME_CONCRETE, "&aDone", null));

        admin.openInventory(inv);
    }

    private ItemStack makeRewardLabel(Material mat, String name, String value) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colorize(name));
        meta.lore(List.of(Component.text("Current: " + value).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    // =========================================================================
    // Page: CONFIRM
    // =========================================================================

    private void openConfirmPage(Player admin, CreatorSession session) {
        session.currentPage = Page.CONFIRM;
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_PREFIX + "Confirm Quest").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Quest summary at slot 4
        ItemStack summary = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta sumMeta = summary.getItemMeta();
        sumMeta.displayName(Component.text("Quest Summary").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> sumLore = new ArrayList<>();
        sumLore.add(Component.text("Player: ").color(NamedTextColor.GRAY)
                .append(Component.text(session.targetPlayerName).color(NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        sumLore.add(Component.text("Category: ").color(NamedTextColor.GRAY)
                .append(Component.text(formatCategory(session.category)).color(NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        sumLore.add(Component.text("Steps: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.steps.size())).color(NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        sumLore.add(Component.empty());
        sumLore.add(Component.text("Rewards:").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        sumLore.add(Component.text("  Money: $" + String.format("%.0f", session.rewardMoney)).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        sumLore.add(Component.text("  Job XP: " + String.format("%.0f", session.rewardJobXp)).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        sumLore.add(Component.text("  XP Levels: " + session.rewardXpLevels).color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        sumMeta.lore(sumLore);
        summary.setItemMeta(sumMeta);
        inv.setItem(4, summary);

        // Steps in row 2
        int[] stepSlots = {19, 20, 21, 22, 23};
        for (int i = 0; i < session.steps.size() && i < stepSlots.length; i++) {
            inv.setItem(stepSlots[i], makeStepDisplayItem(session.steps.get(i), i));
        }

        // Rewards display in row 3
        if (session.rewardMoney > 0) {
            inv.setItem(29, makeRewardLabel(Material.GOLD_INGOT, "&6Money", "$" + String.format("%.0f", session.rewardMoney)));
        }
        if (session.rewardJobXp > 0) {
            inv.setItem(31, makeRewardLabel(Material.EXPERIENCE_BOTTLE, "&bJob XP", String.format("%.0f", session.rewardJobXp)));
        }
        if (session.rewardXpLevels > 0) {
            inv.setItem(33, makeRewardLabel(Material.ENCHANTING_TABLE, "&dXP Levels", String.valueOf(session.rewardXpLevels)));
        }

        // Bottom buttons
        inv.setItem(47, makeButton(Material.ARROW, "&eBack", null));
        inv.setItem(49, makeButton(Material.BARRIER, "&cCancel", null));
        inv.setItem(51, makeButton(Material.LIME_CONCRETE, "&a&lConfirm & Create", "&7Assigns the quest to the player"));

        admin.openInventory(inv);
    }

    // =========================================================================
    // Click handler
    // =========================================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        CreatorSession session = sessions.get(admin.getUniqueId());
        if (session == null) return;

        Component title = event.getView().title();
        String titleStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title);
        if (!titleStr.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        switch (session.currentPage) {
            case PICK_PLAYER -> handlePlayerPick(admin, session, slot, clicked);
            case PICK_CATEGORY -> handleCategoryPick(admin, session, slot);
            case EDIT_STEPS -> handleStepEditor(admin, session, slot, event.isShiftClick());
            case PICK_ACTION -> handleActionPick(admin, session, slot, clicked);
            case PICK_TARGET -> handleTargetPick(admin, session, slot, clicked);
            case SET_AMOUNT -> handleAmountAdjust(admin, session, slot);
            case SET_REWARDS -> handleRewardAdjust(admin, session, slot);
            case CONFIRM -> handleConfirm(admin, session, slot);
        }
    }

    private void handlePlayerPick(Player admin, CreatorSession session, int slot, ItemStack clicked) {
        if (clicked.getType() == Material.BARRIER) {
            close(admin);
            return;
        }
        if (slot == 48 && session.playerPage > 0) {
            session.playerPage--;
            openPlayerPicker(admin, session);
            return;
        }
        if (slot == 50) {
            session.playerPage++;
            openPlayerPicker(admin, session);
            return;
        }
        if (clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta skull) {
            if (skull.getOwningPlayer() != null) {
                session.targetPlayer = skull.getOwningPlayer().getUniqueId();
                session.targetPlayerName = skull.getOwningPlayer().getName();
                openCategoryPicker(admin, session);
            }
        }
    }

    private void handleCategoryPick(Player admin, CreatorSession session, int slot) {
        switch (slot) {
            case 10 -> { session.category = QuestCategory.JOB_DAILY; openStepEditor(admin, session); }
            case 12 -> { session.category = QuestCategory.GENERAL_DAILY; openStepEditor(admin, session); }
            case 14 -> { session.category = QuestCategory.WEEKLY; openStepEditor(admin, session); }
            case 16 -> { session.category = QuestCategory.CHALLENGE; openStepEditor(admin, session); }
            case 22 -> openPlayerPicker(admin, session);
        }
    }

    private void handleStepEditor(Player admin, CreatorSession session, int slot, boolean shiftClick) {
        int[] contentSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

        // Check if clicking a step (shift-click to remove)
        if (shiftClick) {
            for (int i = 0; i < contentSlots.length && i < session.steps.size(); i++) {
                if (slot == contentSlots[i]) {
                    session.steps.remove(i);
                    openStepEditor(admin, session);
                    return;
                }
            }
        }

        switch (slot) {
            case 46 -> { // Add Step
                if (session.steps.size() < 5) openActionPicker(admin, session);
            }
            case 48 -> openRewardEditor(admin, session); // Set Rewards
            case 50 -> openCategoryPicker(admin, session); // Back
            case 52 -> { // Create Quest
                if (!session.steps.isEmpty()) openConfirmPage(admin, session);
            }
        }
    }

    private void handleActionPick(Player admin, CreatorSession session, int slot, ItemStack clicked) {
        if (slot == 40) { // Back
            openStepEditor(admin, session);
            return;
        }

        // Map slot to action type via the lore (which contains the action name)
        if (clicked.getItemMeta() != null && clicked.getItemMeta().lore() != null && !clicked.getItemMeta().lore().isEmpty()) {
            String actionName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(clicked.getItemMeta().lore().get(0));
            try {
                session.currentStep.action = QuestActionType.valueOf(actionName.trim());
                session.targetPage = 0;
                openTargetPicker(admin, session);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void handleTargetPick(Player admin, CreatorSession session, int slot, ItemStack clicked) {
        if (slot == 49) { // Back
            openActionPicker(admin, session);
            return;
        }
        if (slot == 48) { // Previous page
            if (session.targetPage > 0) {
                session.targetPage--;
                openTargetPicker(admin, session);
            }
            return;
        }
        if (slot == 50) { // Next page
            session.targetPage++;
            openTargetPicker(admin, session);
            return;
        }
        if (slot == 4) { // "Any" option
            session.currentStep.target = null;
            openAmountSetter(admin, session);
            return;
        }

        // Content slot — extract target name from lore line 2
        if (clicked.getItemMeta() != null && clicked.getItemMeta().lore() != null && clicked.getItemMeta().lore().size() >= 2) {
            String targetName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(clicked.getItemMeta().lore().get(1));
            session.currentStep.target = targetName.trim();
            // Set a sensible default amount based on difficulty
            double diff = MaterialPools.getDifficulty(session.currentStep.target);
            session.currentStep.amount = Math.max(1, (int) (64 * Math.pow(1.0 - diff, 3.0)));
            openAmountSetter(admin, session);
        }
    }

    private void handleAmountAdjust(Player admin, CreatorSession session, int slot) {
        StepDraft step = session.currentStep;
        switch (slot) {
            case 10 -> step.amount = Math.max(1, step.amount - 64);
            case 11 -> step.amount = Math.max(1, step.amount - 10);
            case 12 -> step.amount = Math.max(1, step.amount - 1);
            case 14 -> step.amount = Math.min(9999, step.amount + 1);
            case 15 -> step.amount = Math.min(9999, step.amount + 10);
            case 16 -> step.amount = Math.min(9999, step.amount + 64);
            case 21 -> { // Back
                openTargetPicker(admin, session);
                return;
            }
            case 23 -> { // Confirm step
                step.description = buildStepDescription(step);
                session.steps.add(step);
                session.currentStep = null;
                openStepEditor(admin, session);
                return;
            }
            default -> { return; }
        }
        // Refresh amount display
        openAmountSetter(admin, session);
    }

    private void handleRewardAdjust(Player admin, CreatorSession session, int slot) {
        switch (slot) {
            // Money row
            case 10 -> session.rewardMoney = Math.max(0, session.rewardMoney - 100);
            case 11 -> session.rewardMoney = Math.max(0, session.rewardMoney - 10);
            case 14 -> session.rewardMoney += 10;
            case 15 -> session.rewardMoney += 100;
            case 16 -> session.rewardMoney += 500;
            // Job XP row
            case 19 -> session.rewardJobXp = Math.max(0, session.rewardJobXp - 50);
            case 20 -> session.rewardJobXp = Math.max(0, session.rewardJobXp - 10);
            case 23 -> session.rewardJobXp += 10;
            case 24 -> session.rewardJobXp += 50;
            case 25 -> session.rewardJobXp += 200;
            // XP Levels row
            case 28 -> session.rewardXpLevels = Math.max(0, session.rewardXpLevels - 5);
            case 29 -> session.rewardXpLevels = Math.max(0, session.rewardXpLevels - 1);
            case 32 -> session.rewardXpLevels += 1;
            case 33 -> session.rewardXpLevels += 5;
            case 34 -> session.rewardXpLevels += 10;
            // Navigation
            case 39 -> { openStepEditor(admin, session); return; }
            case 41 -> { openStepEditor(admin, session); return; }
            default -> { return; }
        }
        openRewardEditor(admin, session);
    }

    private void handleConfirm(Player admin, CreatorSession session, int slot) {
        switch (slot) {
            case 47 -> openStepEditor(admin, session); // Back
            case 49 -> close(admin); // Cancel
            case 51 -> { // Confirm & Create
                createQuest(admin, session);
                close(admin);
            }
        }
    }

    // =========================================================================
    // Quest creation
    // =========================================================================

    private void createQuest(Player admin, CreatorSession session) {
        List<ActiveQuest.ActiveStep> steps = new ArrayList<>();
        for (int i = 0; i < session.steps.size(); i++) {
            StepDraft draft = session.steps.get(i);
            steps.add(new ActiveQuest.ActiveStep(
                    -1, i, draft.action, draft.target, draft.amount,
                    0, false, draft.description
            ));
        }

        RewardDefinition rewards = new RewardDefinition(
                session.rewardMoney, session.rewardJobXp,
                session.rewardXpLevels, session.rewardGems, List.of()
        );

        // Build quest name from first step
        StepDraft first = session.steps.get(0);
        String questName = formatAction(first.action) + " " +
                (first.target != null ? formatName(first.target) : "Items");

        String templateId = "admin_created_" + System.currentTimeMillis();
        String periodKey = "admin-" + admin.getName() + "-" + System.currentTimeMillis();
        long seed = System.nanoTime();

        ActiveQuest quest = new ActiveQuest(
                -1, session.targetPlayer, templateId, session.category,
                periodKey, seed, steps, rewards, 0, false, null, false,
                System.currentTimeMillis(), null, questName
        );

        // Save asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(questManager.getPlugin(), () -> {
            int dbId = questManager.getStorage().saveQuest(quest);
            if (dbId > 0) {
                // Reload the player's quests
                questManager.onPlayerJoin(session.targetPlayer);

                Bukkit.getScheduler().runTask(questManager.getPlugin(), () -> {
                    admin.sendMessage(Component.text("Quest created and assigned to " + session.targetPlayerName + "!")
                            .color(NamedTextColor.GREEN));

                    Player target = Bukkit.getPlayer(session.targetPlayer);
                    if (target != null) {
                        target.sendMessage(Component.text("You've been assigned a new quest: ")
                                .color(NamedTextColor.GOLD)
                                .append(Component.text(questName).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
                    }
                });
            } else {
                Bukkit.getScheduler().runTask(questManager.getPlugin(), () ->
                        admin.sendMessage(Component.text("Failed to create quest! Check console.").color(NamedTextColor.RED)));
            }
        });
    }

    // =========================================================================
    // Inventory close cleanup
    // =========================================================================

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // Only remove if they're not opening another GUI immediately
            Bukkit.getScheduler().runTaskLater(questManager.getPlugin(), () -> {
                if (player.getOpenInventory().getTopInventory().getSize() <= 4) {
                    sessions.remove(player.getUniqueId());
                }
            }, 2L);
        }
    }

    private void close(Player admin) {
        sessions.remove(admin.getUniqueId());
        admin.closeInventory();
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static int[] getContentSlots() {
        return new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
    }

    private static void fillBorder(Inventory inv, Material mat) {
        ItemStack filler = makeFiller(mat);
        // Top row
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        // Bottom row
        int last = inv.getSize() - 9;
        for (int i = last; i < inv.getSize(); i++) inv.setItem(i, filler);
        // Sides
        for (int row = 1; row < inv.getSize() / 9 - 1; row++) {
            inv.setItem(row * 9, filler);
            inv.setItem(row * 9 + 8, filler);
        }
    }

    private static void fillAll(Inventory inv, Material mat) {
        ItemStack filler = makeFiller(mat);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private static ItemStack makeFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeButton(Material mat, String name, String loreText) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colorize(name));
        if (loreText != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreText.split("\n")) {
                lore.add(colorize(line));
            }
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Component colorize(String text) {
        // Simple &-code to Component conversion
        NamedTextColor color = NamedTextColor.WHITE;
        boolean bold = false;
        if (text.startsWith("&a")) { color = NamedTextColor.GREEN; text = text.substring(2); }
        else if (text.startsWith("&c")) { color = NamedTextColor.RED; text = text.substring(2); }
        else if (text.startsWith("&e")) { color = NamedTextColor.YELLOW; text = text.substring(2); }
        else if (text.startsWith("&6")) { color = NamedTextColor.GOLD; text = text.substring(2); }
        else if (text.startsWith("&b")) { color = NamedTextColor.AQUA; text = text.substring(2); }
        else if (text.startsWith("&d")) { color = NamedTextColor.LIGHT_PURPLE; text = text.substring(2); }
        else if (text.startsWith("&7")) { color = NamedTextColor.GRAY; text = text.substring(2); }

        if (text.startsWith("&l")) { bold = true; text = text.substring(2); }

        Component comp = Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
        if (bold) comp = comp.decorate(TextDecoration.BOLD);
        return comp;
    }

    private static Material getDisplayMaterial(QuestActionType action, String target) {
        // Try to use the target as a Material directly
        if (target != null) {
            try {
                Material mat = Material.valueOf(target);
                if (mat.isItem()) return mat;
            } catch (IllegalArgumentException ignored) {}

            // For entity targets, try spawn egg
            try {
                Material egg = Material.valueOf(target + "_SPAWN_EGG");
                return egg;
            } catch (IllegalArgumentException ignored) {}
        }

        // Fallback based on action type
        return switch (action) {
            case BREAK -> Material.IRON_PICKAXE;
            case PLACE -> Material.BRICKS;
            case KILL -> Material.IRON_SWORD;
            case FISH -> Material.FISHING_ROD;
            case CRAFT -> Material.CRAFTING_TABLE;
            case SMELT -> Material.FURNACE;
            case BREW -> Material.BREWING_STAND;
            case ENCHANT -> Material.ENCHANTING_TABLE;
            case FARM -> Material.WHEAT;
            case BREED -> Material.WHEAT_SEEDS;
            case TAME -> Material.BONE;
            case SHEAR -> Material.SHEARS;
            case MILK -> Material.MILK_BUCKET;
            case EAT -> Material.BREAD;
            default -> Material.PAPER;
        };
    }

    private static String formatAction(QuestActionType action) {
        return switch (action) {
            case BREAK -> "Mine";
            case PLACE -> "Place";
            case KILL -> "Kill";
            case FISH -> "Fish";
            case CRAFT -> "Craft";
            case SMELT -> "Smelt";
            case BREW -> "Brew";
            case ENCHANT -> "Enchant";
            case FARM -> "Harvest";
            case BREED -> "Breed";
            case TAME -> "Tame";
            case SHEAR -> "Shear";
            case MILK -> "Milk";
            case EAT -> "Eat";
            case EXPLORE_CHUNK -> "Explore";
            case EXPLORE_DISTANCE -> "Travel";
            case TRADE_PLAYER -> "Trade";
            case TRADE_AH -> "Auction";
            default -> action.name();
        };
    }

    private static String formatName(String name) {
        if (name == null) return "Any";
        String[] words = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private static String formatCategory(QuestCategory cat) {
        return switch (cat) {
            case JOB_DAILY -> "Job Daily";
            case GENERAL_DAILY -> "General Daily";
            case WEEKLY -> "Weekly";
            case CHALLENGE -> "Challenge";
        };
    }

    private static String buildStepDescription(StepDraft step) {
        String action = formatAction(step.action).toLowerCase();
        String target = step.target != null ? formatName(step.target).toLowerCase() : "items";
        return action + " " + step.amount + " " + target;
    }
}
