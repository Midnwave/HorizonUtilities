package com.blockforge.horizonutilities.story.sentinel;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.story.StoryConfig;
import com.blockforge.horizonutilities.story.StoryManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Withered Sentinel — a boosted wither skeleton mini-boss in Stage 1 Chapter 3.
 * Drops Vareth's Insignia on death.
 */
public class WitheredSentinel implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager manager;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    // Track active sentinels spawned per player
    private final Map<UUID, UUID> playerToSentinel = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> sentinelToPlayer = new ConcurrentHashMap<>();

    public WitheredSentinel(HorizonUtilitiesPlugin plugin, StoryManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Spawns a Withered Sentinel at the given location for the specified player.
     */
    public WitherSkeleton spawn(Location location, Player player) {
        StoryConfig config = manager.getStoryConfig();

        WitherSkeleton sentinel = (WitherSkeleton) location.getWorld().spawnEntity(location, EntityType.WITHER_SKELETON);

        // Custom name
        sentinel.customName(MINI.deserialize(config.getSentinelName()));
        sentinel.setCustomNameVisible(true);

        // Health
        var healthAttr = sentinel.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(config.getSentinelHealth());
            sentinel.setHealth(config.getSentinelHealth());
        }

        // Damage
        var damageAttr = sentinel.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(config.getSentinelDamage());
        }

        // No natural drops
        sentinel.getEquipment().clear();
        sentinel.setCanPickupItems(false);

        // Track
        playerToSentinel.put(player.getUniqueId(), sentinel.getUniqueId());
        sentinelToPlayer.put(sentinel.getUniqueId(), player.getUniqueId());

        return sentinel;
    }

    @EventHandler
    public void onSentinelDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton skeleton)) return;

        UUID sentinelId = skeleton.getUniqueId();
        UUID playerId = sentinelToPlayer.remove(sentinelId);
        if (playerId == null) return;
        playerToSentinel.remove(playerId);

        // Clear default drops
        event.getDrops().clear();
        event.setDroppedExp(50);

        // Drop Vareth's Insignia
        StoryConfig config = manager.getStoryConfig();
        String dropId = config.getSentinelDrop();
        ItemStack drop = manager.getItemManager().createItem(dropId);
        if (drop != null) {
            event.getDrops().add(drop);
        }

        // Update player progress
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            manager.completeObjective(playerId, "defeat_sentinel");
            manager.addItemCollected(playerId, dropId);
        }
    }

    public boolean hasSentinelActive(UUID playerId) {
        return playerToSentinel.containsKey(playerId);
    }
}
