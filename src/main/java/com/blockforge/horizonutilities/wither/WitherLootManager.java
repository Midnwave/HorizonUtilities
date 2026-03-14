package com.blockforge.horizonutilities.wither;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.economy.VaultHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles configurable loot drops for the Wither Sovereign boss fight,
 * including guaranteed and random drops, XP orbs, money distribution,
 * and optional CraftEngine item integration.
 */
public class WitherLootManager {

    private final HorizonUtilitiesPlugin plugin;
    private final WitherBossConfig config;

    public WitherLootManager(HorizonUtilitiesPlugin plugin, WitherBossConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Drops all loot for a defeated Wither Sovereign boss.
     * Handles guaranteed drops, random rolls, XP orbs, and money distribution.
     *
     * @param boss the defeated boss entity
     */
    public void dropLoot(WitherBossEntity boss) {
        Location deathLoc = boss.getWither().getLocation();
        Set<UUID> participants = boss.getParticipants();
        int participantCount = Math.max(1, participants.size());

        // Calculate drop multiplier based on player scaling
        double dropMultiplier = 1.0;
        if (config.isScaleWithPlayers() && participantCount > 1) {
            dropMultiplier = 1.0 + 0.1 * (participantCount - 1);
        }

        // Drop guaranteed items
        for (WitherBossConfig.LootEntry entry : config.getGuaranteedDrops()) {
            int scaledCount = getScaledDropCount(entry, dropMultiplier);
            for (int i = 0; i < scaledCount; i++) {
                ItemStack item = createItem(entry);
                if (item != null) {
                    deathLoc.getWorld().dropItemNaturally(deathLoc, item);
                }
            }
        }

        // Roll random items (each has an independent chance)
        for (WitherBossConfig.LootEntry entry : config.getRandomDrops()) {
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll <= entry.chance()) {
                int scaledCount = getScaledDropCount(entry, dropMultiplier);
                for (int i = 0; i < scaledCount; i++) {
                    ItemStack item = createItem(entry);
                    if (item != null) {
                        deathLoc.getWorld().dropItemNaturally(deathLoc, item);
                    }
                }
            }
        }

        // Drop XP orbs
        int totalXp = config.getDropXp();
        if (config.isScaleWithPlayers() && participantCount > 1) {
            totalXp = (int) (totalXp * dropMultiplier);
        }
        dropExperienceOrbs(deathLoc, totalXp);

        // Distribute money to participants via Vault
        distributeMoney(boss, participants, participantCount);

        plugin.getLogger().info("Wither Sovereign loot dropped at " + formatLocation(deathLoc)
                + " for " + participantCount + " participants (multiplier: "
                + String.format("%.1f", dropMultiplier) + "x).");
    }

    /**
     * Creates an ItemStack from a loot entry configuration.
     * Attempts CraftEngine integration if the entry specifies a CraftEngine item ID.
     * Falls back to vanilla items if CraftEngine is unavailable or the item cannot be resolved.
     *
     * @param entry the loot entry
     * @return the created ItemStack, or null if creation failed
     */
    public ItemStack createItem(WitherBossConfig.LootEntry entry) {
        int amount = randomAmount(entry.amountMin(), entry.amountMax());

        // Try CraftEngine integration first
        if (entry.isCraftEngine()) {
            ItemStack ceItem = tryCreateCraftEngineItem(entry.craftEngineItem(), amount);
            if (ceItem != null) {
                return ceItem;
            }
            // Fallback to vanilla material if CraftEngine item cannot be created
            plugin.getLogger().warning("CraftEngine item '" + entry.craftEngineItem()
                    + "' could not be created, falling back to vanilla material.");
        }

        // Create vanilla ItemStack
        Material material = entry.material();
        if (material == null || material == Material.AIR) {
            return null;
        }

        ItemStack stack = new ItemStack(material, amount);
        return stack;
    }

    /**
     * Attempts to create an item using the CraftEngine plugin API.
     * Uses reflection to avoid a hard compile-time dependency on the CraftEngine jar.
     *
     * @param craftEngineItemId the CraftEngine item ID (e.g., "craftengine:wither_blade")
     * @param amount            the desired stack amount
     * @return the created ItemStack, or null if CraftEngine is unavailable or the item doesn't exist
     */
    private ItemStack tryCreateCraftEngineItem(String craftEngineItemId, int amount) {
        try {
            Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
            if (craftEngine == null) {
                return null;
            }

            // TODO: Replace reflection with direct CraftEngine API call once the API jar is available.
            // The expected API usage would be something like:
            //   CraftEngineAPI.createItem(craftEngineItemId, amount)
            //
            // For now, attempt via reflection on the common API pattern:
            //   net.momirealms.craftengine.bukkit.api.CraftEngineItems.createItem(Key)
            Class<?> apiClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            var keyClass = Class.forName("net.momirealms.craftengine.core.item.Key");

            // Try parsing the key from the string ID
            var parseMethod = keyClass.getMethod("of", String.class);
            Object key = parseMethod.invoke(null, craftEngineItemId);

            // Create the item
            var createMethod = apiClass.getMethod("createItem", keyClass);
            Object result = createMethod.invoke(null, key);

            if (result instanceof ItemStack itemStack) {
                itemStack.setAmount(amount);
                return itemStack;
            }
        } catch (ClassNotFoundException e) {
            // CraftEngine API classes not found — plugin may not be installed
            plugin.getLogger().fine("CraftEngine API classes not found for item: " + craftEngineItemId);
        } catch (NoSuchMethodException e) {
            // API method signature mismatch — CraftEngine version may differ
            plugin.getLogger().warning("CraftEngine API method not found. Item: " + craftEngineItemId
                    + ". The CraftEngine version may be incompatible.");
        } catch (Exception e) {
            // Generic catch for reflection errors (IllegalAccessException, InvocationTargetException, etc.)
            plugin.getLogger().warning("Failed to create CraftEngine item '" + craftEngineItemId
                    + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Distributes money to fight participants via the Vault economy hook.
     * If moneyPerPlayer is true, each participant gets the full configured amount.
     * Otherwise, the total is split evenly among all participants.
     */
    private void distributeMoney(WitherBossEntity boss, Set<UUID> participants, int participantCount) {
        double moneyAmount = config.getDropMoney();
        if (moneyAmount <= 0) return;

        VaultHook vault = plugin.getVaultHook();
        if (!vault.isAvailable()) {
            plugin.getLogger().fine("Vault economy not available — skipping money distribution for Wither Sovereign.");
            return;
        }

        double perPlayer;
        if (config.isMoneyPerPlayer()) {
            // Each participant receives the full amount
            perPlayer = moneyAmount;
        } else {
            // Split evenly
            perPlayer = moneyAmount / participantCount;
        }

        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                vault.deposit(player, perPlayer);
                player.sendMessage(Component.text("[Wither Sovereign] ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text("You received ", NamedTextColor.GRAY))
                        .append(Component.text(vault.format(perPlayer), NamedTextColor.GOLD))
                        .append(Component.text(" for defeating the Wither Sovereign!", NamedTextColor.GRAY)));
            } else {
                // Player is offline — deposit to their offline account
                String playerName = Bukkit.getOfflinePlayer(playerId).getName();
                vault.depositOffline(playerId, playerName != null ? playerName : "Unknown", perPlayer);
            }
        }
    }

    /**
     * Drops XP orbs at the death location. Splits into multiple orbs
     * to avoid a single massive orb.
     */
    private void dropExperienceOrbs(Location location, int totalXp) {
        if (totalXp <= 0 || location.getWorld() == null) return;

        // Split into orbs of ~100 XP each, with a minimum of 1 and max of 50 orbs
        int orbCount = Math.max(1, Math.min(50, totalXp / 100));
        int xpPerOrb = totalXp / orbCount;
        int remainder = totalXp % orbCount;

        for (int i = 0; i < orbCount; i++) {
            int xp = xpPerOrb + (i == 0 ? remainder : 0);
            location.getWorld().spawn(
                    location.clone().add(
                            ThreadLocalRandom.current().nextDouble(-1.5, 1.5),
                            ThreadLocalRandom.current().nextDouble(0.5, 2.0),
                            ThreadLocalRandom.current().nextDouble(-1.5, 1.5)
                    ),
                    ExperienceOrb.class,
                    orb -> orb.setExperience(xp)
            );
        }
    }

    /**
     * Calculates the number of item drops after applying the player scaling multiplier.
     */
    private int getScaledDropCount(WitherBossConfig.LootEntry entry, double multiplier) {
        int baseAmount = randomAmount(entry.amountMin(), entry.amountMax());
        return Math.max(1, (int) Math.round(baseAmount * multiplier));
    }

    /**
     * Returns a random amount between min and max (inclusive).
     */
    private int randomAmount(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Formats a Location to a human-readable string for logging.
     */
    private String formatLocation(Location loc) {
        return String.format("%s (%.0f, %.0f, %.0f)",
                loc.getWorld() != null ? loc.getWorld().getName() : "null",
                loc.getX(), loc.getY(), loc.getZ());
    }
}
