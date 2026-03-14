package com.blockforge.horizonutilities.story.boss;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.story.StoryConfig;
import com.blockforge.horizonutilities.story.StoryManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;

/**
 * Handles Vareth's death — drops 5 guaranteed items, marks stage complete.
 * Drops: Vareth's Sigil, Rune of Ruin, Vigil's Edge, Warden's Cuirass, Tome of the First Herald
 */
public class VarethLootHandler implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager manager;
    private final VarethBossManager bossManager;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final String[] VARETH_DROPS = {
        "vareths_sigil", "rune_of_ruin", "vigils_edge", "wardens_cuirass", "tome_of_first_herald"
    };

    public VarethLootHandler(HorizonUtilitiesPlugin plugin, StoryManager manager, VarethBossManager bossManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.bossManager = bossManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        VarethBossManager.VarethFight fight = bossManager.getFightByBoss(entity.getUniqueId());
        if (fight == null) return;

        // Clear default drops
        event.getDrops().clear();
        event.setDroppedExp(200);

        // Add guaranteed drops
        for (String dropId : VARETH_DROPS) {
            ItemStack drop = manager.getItemManager().createItem(dropId);
            if (drop != null) {
                event.getDrops().add(drop);
            }
        }

        // End the fight
        bossManager.endFight(fight.playerId());

        // Update player progress
        Player player = plugin.getServer().getPlayer(fight.playerId());
        if (player != null && player.isOnline()) {
            StoryManager.StoryPlayerData data = manager.getPlayerData(player.getUniqueId());
            if (data != null) {
                data.setVarethDefeated(true);
            }

            // Completion objective
            manager.completeObjective(fight.playerId(), "defeat_vareth");

            // Show death title
            StoryConfig config = manager.getStoryConfig();
            player.showTitle(Title.title(
                MINI.deserialize(config.getVarethDeathTitle()),
                MINI.deserialize(config.getVarethDeathSubtitle()),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(1000))
            ));

            // Mark items collected
            for (String dropId : VARETH_DROPS) {
                manager.addItemCollected(fight.playerId(), dropId);
            }
        }
    }
}
