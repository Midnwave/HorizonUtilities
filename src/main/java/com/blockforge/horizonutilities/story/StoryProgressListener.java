package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Map;

/**
 * Tracks player progress through the Hollow Chronicle:
 * - Book reads (right-click written book with PDC tag)
 * - Item pickups
 * - Chapter auto-advancement when all objectives complete
 */
public class StoryProgressListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager manager;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Component PREFIX = Component.text("[Chronicle] ", NamedTextColor.DARK_PURPLE);

    public StoryProgressListener(HorizonUtilitiesPlugin plugin, StoryManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if this is a story item interaction
        String itemId = manager.getItemManager().getItemId(item);
        if (itemId == null) return;

        StoryManager.StoryPlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        // Track item collection
        if (!data.getItemsCollected().contains(itemId)) {
            manager.addItemCollected(player.getUniqueId(), itemId);
            player.sendMessage(PREFIX.append(
                Component.text("Item discovered: ", NamedTextColor.GRAY)
                    .append(Component.text(itemId.replace("_", " "), NamedTextColor.GOLD))));
        }

        // Check objective completion
        checkObjectiveCompletion(player, data, itemId);
    }

    /**
     * Checks if collecting/interacting with an item completes any current chapter objectives.
     * Auto-advances chapter if all objectives are done.
     */
    private void checkObjectiveCompletion(Player player, StoryManager.StoryPlayerData data, String itemId) {
        StoryConfig cfg = manager.getStoryConfig();
        int stage = data.getCurrentStage();
        int chapter = data.getCurrentChapter();

        StoryConfig.StageData stageData = cfg.getStage(stage);
        if (stageData == null) return;

        StoryConfig.ChapterData chapterData = stageData.chapters().get(chapter);
        if (chapterData == null) return;

        // Check if any objectives match this item
        for (StoryConfig.ObjectiveData obj : chapterData.objectives()) {
            if (data.getObjectivesCompleted().contains(obj.id())) continue;

            // Match objective target to item ID
            if (obj.target().equals(itemId)) {
                manager.completeObjective(player.getUniqueId(), obj.id());
                player.sendMessage(PREFIX.append(
                    Component.text("Objective complete: ", NamedTextColor.GREEN)
                        .append(Component.text(obj.description(), NamedTextColor.WHITE))));
            }
        }

        // Check if all chapter objectives are now complete
        boolean allDone = chapterData.objectives().stream()
            .allMatch(obj -> data.getObjectivesCompleted().contains(obj.id()));

        if (allDone && !chapterData.objectives().isEmpty()) {
            advanceChapter(player, data, stage, chapter, stageData);
        }
    }

    private void advanceChapter(Player player, StoryManager.StoryPlayerData data,
                                int stage, int chapter, StoryConfig.StageData stageData) {
        int nextChapter = chapter + 1;

        if (stageData.chapters().containsKey(nextChapter)) {
            // Advance to next chapter
            manager.setChapter(player.getUniqueId(), nextChapter);
            StoryConfig.ChapterData next = stageData.chapters().get(nextChapter);
            String chapterName = next != null ? next.name() : "Chapter " + nextChapter;

            player.sendMessage(Component.empty());
            player.sendMessage(PREFIX.append(
                Component.text("Chapter " + nextChapter + ": " + chapterName, NamedTextColor.GOLD)));
            if (next != null && !next.description().isEmpty()) {
                player.sendMessage(Component.text("  " + next.description(), NamedTextColor.GRAY));
            }
            player.sendMessage(Component.empty());
        } else {
            // Stage complete
            completeStage(player, stage);
        }
    }

    private void completeStage(Player player, int stage) {
        StoryConfig cfg = manager.getStoryConfig();
        StoryConfig.CompletionData completion = cfg.getCompletion("stage" + stage);

        if (completion != null) {
            Title title = Title.title(
                MINI.deserialize(completion.title()),
                MINI.deserialize(completion.subtitle()),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(1000))
            );
            player.showTitle(title);
        }

        // Advance to next stage, chapter 0
        manager.setStage(player.getUniqueId(), stage + 1);
        player.sendMessage(PREFIX.append(
            Component.text("Stage " + stage + " complete.", NamedTextColor.GREEN)));
    }
}
