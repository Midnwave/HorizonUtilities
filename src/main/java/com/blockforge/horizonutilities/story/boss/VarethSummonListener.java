package com.blockforge.horizonutilities.story.boss;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.story.StoryConfig;
import com.blockforge.horizonutilities.story.StoryManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.time.Duration;

/**
 * Handles right-click on the crypt altar with Soul of the Vigil to summon Vareth.
 * Plays a cinematic sequence before spawning the boss.
 */
public class VarethSummonListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager manager;
    private final VarethBossManager bossManager;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public VarethSummonListener(HorizonUtilitiesPlugin plugin, StoryManager manager, VarethBossManager bossManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.bossManager = bossManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();

        // Must be holding Soul of the Vigil
        String itemId = manager.getItemManager().getItemId(player.getInventory().getItemInMainHand());
        if (!"soul_of_the_vigil".equals(itemId)) return;

        // Must be on stage 1, chapter 7 (The Reckoning)
        StoryManager.StoryPlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data == null) return;
        if (data.getCurrentStage() != 1 || data.getCurrentChapter() != 7) return;

        // Must not already have an active fight
        if (bossManager.getFight(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("[Chronicle] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("Vareth is already upon you.", NamedTextColor.RED)));
            return;
        }

        // Consume Soul of the Vigil
        var hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);

        // Start cinematic
        StoryConfig config = manager.getStoryConfig();
        int cinematicTicks = config.getVarethCinematicTicks();
        Location spawnLoc = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);

        // Cinematic: darkness, particles, title
        player.showTitle(Title.title(
            MINI.deserialize("<dark_red><bold>The vigil ends."),
            MINI.deserialize("<gray>Something stirs below."),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));

        // Particle effects during cinematic
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) { task.cancel(); return; }
            spawnLoc.getWorld().spawnParticle(Particle.SOUL, spawnLoc, 15, 1, 2, 1, 0.02);
            spawnLoc.getWorld().spawnParticle(Particle.SMOKE, spawnLoc, 10, 0.5, 1, 0.5, 0.05);
        }, 0L, 5L);

        // Spawn boss after cinematic
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
            spawnLoc.getWorld().spawnParticle(Particle.EXPLOSION, spawnLoc, 3, 0.5, 0.5, 0.5, 0);

            bossManager.spawnVareth(player, spawnLoc);

            player.sendMessage(Component.text("[Chronicle] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("Vareth, Herald of Ruin, has awakened.", NamedTextColor.DARK_RED)));
        }, cinematicTicks);
    }
}
