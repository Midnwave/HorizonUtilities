package com.blockforge.horizonutilities.quests.tracking;

import com.blockforge.horizonutilities.quests.QuestActionType;
import com.blockforge.horizonutilities.quests.QuestManager;
import com.blockforge.horizonutilities.quests.generation.MaterialPools;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit event listener that feeds non-job actions into the quest tracker.
 * Job actions are tracked separately via JobManager.processAction().
 */
public class QuestEventListener implements Listener {

    private final QuestManager questManager;

    /** Tracks which player last interacted with each brewing stand location */
    private final Map<Location, UUID> brewingStandUsers = new ConcurrentHashMap<>();

    public QuestEventListener(QuestManager questManager) {
        this.questManager = questManager;
    }

    // ========== LIFECYCLE ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        questManager.onPlayerJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        questManager.onPlayerQuit(uuid);
        // Clean up brewing stand ownership entries for this player
        brewingStandUsers.values().removeIf(uuid::equals);
    }

    // ========== GENERAL ACTIONS (non-job tracked) ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        String material = event.getBlock().getType().name();
        questManager.trackAction(event.getPlayer(), QuestActionType.BREAK, material);

        // Also track as FARM for crop blocks
        if (MaterialPools.CROPS.contains(material)) {
            questManager.trackAction(event.getPlayer(), QuestActionType.FARM, material);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        questManager.trackAction(event.getPlayer(), QuestActionType.PLACE,
            event.getBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        questManager.trackAction(killer, QuestActionType.KILL,
            entity.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Entity caught = event.getCaught();
        if (caught == null) return;

        // The caught entity for fish is an Item entity
        String material = "FISH";
        if (caught instanceof org.bukkit.entity.Item itemEntity) {
            material = itemEntity.getItemStack().getType().name();
        }

        questManager.trackAction(event.getPlayer(), QuestActionType.FISH, material);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getRecipe().getResult();
        int amount = result.getAmount();

        // Handle shift-click crafting
        if (event.isShiftClick()) {
            // Estimate max craftable — use minimum of each ingredient
            int maxCraftable = 64;
            for (ItemStack ingredient : event.getInventory().getMatrix()) {
                if (ingredient != null && ingredient.getType() != Material.AIR) {
                    maxCraftable = Math.min(maxCraftable, ingredient.getAmount());
                }
            }
            amount = maxCraftable * result.getAmount();
        }

        questManager.trackAction(player, QuestActionType.CRAFT, result.getType().name(), amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        questManager.trackAction(event.getPlayer(), QuestActionType.SMELT,
            event.getItemType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        questManager.trackAction(event.getEnchanter(), QuestActionType.ENCHANT,
            event.getItem().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerShear(PlayerShearEntityEvent event) {
        questManager.trackAction(event.getPlayer(), QuestActionType.SHEAR,
            event.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        questManager.trackAction(player, QuestActionType.BREED,
            event.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerEat(PlayerItemConsumeEvent event) {
        questManager.trackAction(event.getPlayer(), QuestActionType.EAT,
            event.getItem().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItem(event.getHand());

        // Detect milking (bucket + cow/goat/mooshroom)
        if (hand != null && hand.getType() == Material.BUCKET) {
            String entityType = entity.getType().name();
            if (entityType.equals("COW") || entityType.equals("MOOSHROOM") || entityType.equals("GOAT")) {
                questManager.trackAction(player, QuestActionType.MILK, entityType);
            }
        }

        // Detect taming (already handled by EntityTameEvent if needed, but this covers interaction)
    }

    // ========== TAME ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        questManager.trackAction(player, QuestActionType.TAME,
            event.getEntity().getType().name());
    }

    // ========== BREW (tracked via brewing stand ownership) ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.BREWING
                && event.getPlayer() instanceof Player player) {
            Location loc = event.getInventory().getLocation();
            if (loc != null) {
                brewingStandUsers.put(loc, player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Location loc = event.getBlock().getLocation();
        UUID ownerUuid = brewingStandUsers.get(loc);
        if (ownerUuid != null) {
            Player player = Bukkit.getPlayer(ownerUuid);
            if (player != null && player.isOnline()) {
                questManager.trackAction(player, QuestActionType.BREW, null);
            }
        }
    }
}
