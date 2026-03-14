package com.blockforge.horizonutilities.story.boss;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.story.StoryConfig;
import com.blockforge.horizonutilities.story.StoryManager;
import com.blockforge.horizonutilities.story.integration.MythicMobsHook;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player Vareth boss fights.
 * Spawns via MythicMobs if available, otherwise spawns a vanilla wither skeleton fallback.
 */
public class VarethBossManager {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager manager;
    private final MythicMobsHook mythicHook;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<UUID, VarethFight> activeFights = new ConcurrentHashMap<>();

    public VarethBossManager(HorizonUtilitiesPlugin plugin, StoryManager manager, MythicMobsHook mythicHook) {
        this.plugin = plugin;
        this.manager = manager;
        this.mythicHook = mythicHook;
    }

    /**
     * Spawns Vareth for a player at the given location.
     * Uses MythicMobs if available, vanilla wither skeleton as fallback.
     */
    public VarethFight spawnVareth(Player player, Location location) {
        StoryConfig config = manager.getStoryConfig();

        Entity bossEntity;
        if (mythicHook.isAvailable()) {
            bossEntity = mythicHook.spawnMob(config.getVarethMythicId(), location);
        } else {
            bossEntity = null;
        }

        // Vanilla fallback
        if (bossEntity == null) {
            WitherSkeleton fallback = (WitherSkeleton) location.getWorld().spawnEntity(location, EntityType.WITHER_SKELETON);
            fallback.customName(MINI.deserialize("<dark_red><bold>Vareth, Herald of Ruin"));
            fallback.setCustomNameVisible(true);

            var health = fallback.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(200);
                fallback.setHealth(200);
            }
            var damage = fallback.getAttribute(Attribute.ATTACK_DAMAGE);
            if (damage != null) {
                damage.setBaseValue(12.0);
            }

            fallback.getEquipment().clear();
            fallback.setCanPickupItems(false);
            bossEntity = fallback;
        }

        // Boss bar
        BossBar.Color barColor;
        try {
            barColor = BossBar.Color.valueOf(config.getVarethBossBarColor().toUpperCase());
        } catch (IllegalArgumentException e) {
            barColor = BossBar.Color.PURPLE;
        }

        BossBar bossBar = BossBar.bossBar(
            MINI.deserialize(config.getVarethBossBarTitle()),
            1.0f,
            barColor,
            BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bossBar);

        // Boss bar update task
        Entity finalBoss = bossEntity;
        BukkitTask barTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!finalBoss.isValid() || finalBoss.isDead()) return;
            if (finalBoss instanceof LivingEntity living) {
                var maxHp = living.getAttribute(Attribute.MAX_HEALTH);
                double max = maxHp != null ? maxHp.getBaseValue() : 200;
                float progress = (float) Math.max(0, Math.min(1, living.getHealth() / max));
                bossBar.progress(progress);
            }
        }, 5L, 5L);

        VarethFight fight = new VarethFight(player.getUniqueId(), bossEntity.getUniqueId(), bossBar, barTask);
        activeFights.put(player.getUniqueId(), fight);

        return fight;
    }

    public VarethFight getFight(UUID playerId) {
        return activeFights.get(playerId);
    }

    public VarethFight getFightByBoss(UUID bossId) {
        for (VarethFight fight : activeFights.values()) {
            if (fight.bossEntityId().equals(bossId)) return fight;
        }
        return null;
    }

    public void endFight(UUID playerId) {
        VarethFight fight = activeFights.remove(playerId);
        if (fight != null) {
            fight.barTask().cancel();
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.hideBossBar(fight.bossBar());
            }
        }
    }

    public void shutdown() {
        for (UUID playerId : activeFights.keySet()) {
            endFight(playerId);
        }
    }

    public record VarethFight(UUID playerId, UUID bossEntityId, BossBar bossBar, BukkitTask barTask) {}
}
