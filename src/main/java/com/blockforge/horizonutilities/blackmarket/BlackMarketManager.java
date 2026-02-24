package com.blockforge.horizonutilities.blackmarket;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.economy.EconomyAuditLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class BlackMarketManager {

    private final HorizonUtilitiesPlugin plugin;
    private final EconomyAuditLog auditLog;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final List<BlackMarketItem> items = new ArrayList<>();
    private final Map<String, List<BlackMarketItem>> itemsByCategory = new LinkedHashMap<>();

    private File configFile;
    private YamlConfiguration config;

    public BlackMarketManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.auditLog = new EconomyAuditLog(plugin);
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    public void loadItems() {
        configFile = new File(plugin.getDataFolder(), "black-market.yml");
        if (!configFile.exists()) {
            plugin.saveResource("black-market.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        items.clear();
        itemsByCategory.clear();

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("[BlackMarket] No 'items' section found in black-market.yml");
            return;
        }

        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(id);
            BlackMarketItem item = BlackMarketItem.fromConfig(id, section);
            if (item == null) {
                plugin.getLogger().warning("[BlackMarket] Failed to load item: " + id);
                continue;
            }
            items.add(item);
            itemsByCategory.computeIfAbsent(item.getCategory(), k -> new ArrayList<>()).add(item);
        }

        plugin.getLogger().info("[BlackMarket] Loaded " + items.size() + " items across "
                + itemsByCategory.size() + " categories.");
    }

    public void reload() {
        loadItems();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public BlackMarketItem getItemById(String id) {
        return items.stream().filter(i -> i.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public List<BlackMarketItem> getItemsByCategory(String category) {
        if (category == null || category.equalsIgnoreCase("All")) return Collections.unmodifiableList(items);
        return Collections.unmodifiableList(itemsByCategory.getOrDefault(category, Collections.emptyList()));
    }

    public List<String> getCategories() {
        return Collections.unmodifiableList(new ArrayList<>(itemsByCategory.keySet()));
    }

    public List<BlackMarketItem> getAllItems() {
        return Collections.unmodifiableList(items);
    }

    // -------------------------------------------------------------------------
    // Purchase
    // -------------------------------------------------------------------------

    /**
     * Attempts to purchase an item from the black market for the given player.
     * Returns true on success, false on failure (sends appropriate messages).
     */
    public boolean purchaseItem(Player player, String itemId) {
        BlackMarketItem bmItem = getItemById(itemId);
        if (bmItem == null) {
            player.sendMessage(Component.text("That item does not exist in the Black Market.", NamedTextColor.RED));
            return false;
        }

        // Check funds
        if (!plugin.getVaultHook().has(player, bmItem.getPrice())) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>You don't have enough money. This item costs <gold>" +
                    plugin.getVaultHook().format(bmItem.getPrice()) + "</gold>.</red>"));
            return false;
        }

        // Build item first (so we can check inv space)
        ItemStack itemToGive = buildItemStack(bmItem);

        // Check inventory space (claim blocks don't need space)
        if (bmItem.getClaimBlocks() <= 0 && !hasInventorySpace(player, itemToGive)) {
            player.sendMessage(Component.text("Your inventory is full!", NamedTextColor.RED));
            return false;
        }

        // Withdraw money
        if (!plugin.getVaultHook().withdraw(player, bmItem.getPrice())) {
            player.sendMessage(Component.text("Payment failed — please try again.", NamedTextColor.RED));
            return false;
        }

        double balanceAfter = plugin.getVaultHook().getBalance(player);

        // Deliver item / claim blocks
        if (bmItem.getClaimBlocks() > 0) {
            grantClaimBlocks(player, bmItem.getClaimBlocks());
            player.sendMessage(miniMessage.deserialize(
                    "<green>You purchased <gold>" + bmItem.getClaimBlocks() +
                    " claim blocks</gold> for <yellow>" +
                    plugin.getVaultHook().format(bmItem.getPrice()) + "</yellow>!"));
        } else {
            player.getInventory().addItem(itemToGive);
            player.sendMessage(miniMessage.deserialize(
                    "<green>You purchased <gold>" + stripMiniMessage(bmItem.getDisplayName()) +
                    "</gold> for <yellow>" + plugin.getVaultHook().format(bmItem.getPrice()) + "</yellow>!"));
        }

        // Log to DB
        logPurchase(player, bmItem);

        // Audit log
        auditLog.log(player.getUniqueId(), player.getName(),
                EconomyAuditLog.BM_PURCHASE,
                -bmItem.getPrice(), balanceAfter,
                "black_market:" + bmItem.getId(), null);

        return true;
    }

    // -------------------------------------------------------------------------
    // Admin mutators
    // -------------------------------------------------------------------------

    public void addItem(BlackMarketItem item) {
        items.removeIf(i -> i.getId().equalsIgnoreCase(item.getId()));
        items.add(item);
        itemsByCategory.computeIfAbsent(item.getCategory(), k -> new ArrayList<>())
                .removeIf(i -> i.getId().equalsIgnoreCase(item.getId()));
        itemsByCategory.get(item.getCategory()).add(item);
        saveItemsToConfig();
    }

    public void removeItem(String itemId) {
        BlackMarketItem item = getItemById(itemId);
        if (item == null) return;
        items.removeIf(i -> i.getId().equalsIgnoreCase(itemId));
        List<BlackMarketItem> catList = itemsByCategory.get(item.getCategory());
        if (catList != null) catList.removeIf(i -> i.getId().equalsIgnoreCase(itemId));
        saveItemsToConfig();
    }

    public void setPrice(String itemId, double price) {
        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equalsIgnoreCase(itemId)) { idx = i; break; }
        }
        if (idx < 0) return;
        BlackMarketItem updated = items.get(idx).withPrice(price);
        items.set(idx, updated);
        List<BlackMarketItem> catList = itemsByCategory.get(updated.getCategory());
        if (catList != null) {
            catList.replaceAll(i -> i.getId().equalsIgnoreCase(itemId) ? updated : i);
        }
        saveItemsToConfig();
    }

    public void saveItemsToConfig() {
        if (configFile == null || config == null) return;
        // Clear items section
        config.set("items", null);
        for (BlackMarketItem item : items) {
            ConfigurationSection section = config.createSection("items." + item.getId());
            item.toConfig(section);
        }
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[BlackMarket] Failed to save config: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build the ItemStack for a non-breaker, non-claim-block item. */
    private ItemStack buildItemStack(BlackMarketItem bmItem) {
        if (bmItem.isBreaker()) {
            return BreakerItemFactory.createBreakerItem(bmItem);
        }

        ItemStack item = new ItemStack(bmItem.getMaterial(), bmItem.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(miniMessage.deserialize(bmItem.getDisplayName())
                .decoration(TextDecoration.ITALIC, false));

        if (!bmItem.getLore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : bmItem.getLore()) {
                lore.add(miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        if (bmItem.getCustomModelData() > 0) {
            meta.setCustomModelData(bmItem.getCustomModelData());
        }

        bmItem.getEnchantments().forEach((key, level) -> {
            Enchantment ench = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(key));
            if (ench != null) meta.addEnchant(ench, level, true);
        });

        item.setItemMeta(meta);
        return item;
    }

    private boolean hasInventorySpace(Player player, ItemStack item) {
        return player.getInventory().firstEmpty() != -1
                || Arrays.stream(player.getInventory().getContents())
                    .anyMatch(s -> s != null && s.isSimilar(item)
                            && s.getAmount() + item.getAmount() <= s.getMaxStackSize());
    }

    private void grantClaimBlocks(Player player, int blocks) {
        try {
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Object gpInstance = gpClass.getMethod("getInstance").invoke(null);
            Object dataStore = gpClass.getMethod("getDataStore").invoke(gpInstance);
            // PlayerData approach: dataStore.getPlayerData(uuid).setAccruedClaimBlocks(...)
            Object playerData = dataStore.getClass()
                    .getMethod("getPlayerData", UUID.class)
                    .invoke(dataStore, player.getUniqueId());
            int current = (int) playerData.getClass()
                    .getMethod("getAccruedClaimBlocks")
                    .invoke(playerData);
            playerData.getClass()
                    .getMethod("setAccruedClaimBlocks", int.class)
                    .invoke(playerData, current + blocks);
            plugin.getLogger().fine("[BlackMarket] Granted " + blocks + " claim blocks to " + player.getName()
                    + " via reflection.");
        } catch (Exception e) {
            plugin.getLogger().warning("[BlackMarket] Could not grant claim blocks (GriefPrevention not found or API changed): "
                    + e.getMessage());
            player.sendMessage(Component.text(
                    "Could not grant claim blocks — please contact an admin.", NamedTextColor.RED));
        }
    }

    private void logPurchase(Player player, BlackMarketItem item) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO blackmarket_log (player_uuid, player_name, item_id, quantity, price_each, total_price, purchased_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)");
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, item.getId());
                ps.setInt(4, item.getAmount());
                ps.setDouble(5, item.getPrice());
                ps.setDouble(6, item.getPrice());
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[BlackMarket] Failed to log purchase: " + e.getMessage());
            }
        });
    }

    /** Strip MiniMessage tags from a string for plain-text display. */
    private String stripMiniMessage(String input) {
        return miniMessage.stripTags(input);
    }
}
