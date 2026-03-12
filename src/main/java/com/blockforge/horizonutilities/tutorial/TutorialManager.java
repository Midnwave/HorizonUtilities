package com.blockforge.horizonutilities.tutorial;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.io.File;
import java.sql.*;
import java.util.*;

public class TutorialManager {

    private final HorizonUtilitiesPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Settings
    private boolean autoOpenFirstJoin;
    private int firstJoinDelayTicks;
    private double rewardMoney;
    private List<ItemStack> rewardItems;

    // Category data (ordered)
    private final LinkedHashMap<String, CategoryData> categories = new LinkedHashMap<>();

    public TutorialManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "tutorial.yml");
        if (!file.exists()) plugin.saveResource("tutorial.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Settings
        autoOpenFirstJoin = cfg.getBoolean("settings.auto-open-first-join", true);
        firstJoinDelayTicks = cfg.getInt("settings.first-join-delay-ticks", 60);
        rewardMoney = cfg.getDouble("settings.completion-reward.money", 500.0);

        // Reward items
        rewardItems = new ArrayList<>();
        var itemsList = cfg.getMapList("settings.completion-reward.items");
        for (Map<?, ?> itemMap : itemsList) {
            String matName = itemMap.containsKey("material") ? String.valueOf(itemMap.get("material")) : "STONE";
            Material mat = Material.matchMaterial(matName);
            if (mat == null) continue;
            int amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1;
            ItemStack stack = new ItemStack(mat, amount);

            if (itemMap.containsKey("leather-color")) {
                String colorStr = String.valueOf(itemMap.get("leather-color")).replace("#", "");
                try {
                    int rgb = Integer.parseInt(colorStr, 16);
                    if (stack.getItemMeta() instanceof LeatherArmorMeta leatherMeta) {
                        leatherMeta.setColor(Color.fromRGB(rgb));
                        stack.setItemMeta(leatherMeta);
                    }
                } catch (NumberFormatException ignored) {}
            }
            rewardItems.add(stack);
        }

        // Categories
        categories.clear();
        ConfigurationSection catSection = cfg.getConfigurationSection("categories");
        if (catSection == null) return;

        for (String catId : catSection.getKeys(false)) {
            ConfigurationSection cs = catSection.getConfigurationSection(catId);
            if (cs == null) continue;

            CategoryData cat = new CategoryData();
            cat.id = catId;
            cat.displayName = cs.getString("display-name", catId);
            cat.iconMaterial = Material.matchMaterial(cs.getString("icon", "BOOK"));
            cat.slot = cs.getInt("slot", 0);
            cat.colorName = cs.getString("color", "WHITE");
            cat.glassPaneMaterial = Material.matchMaterial(cs.getString("glass-pane", "GRAY_STAINED_GLASS_PANE"));
            cat.lore = cs.getStringList("lore");

            // Pages
            ConfigurationSection pagesSection = cs.getConfigurationSection("pages");
            if (pagesSection != null) {
                for (String pageKey : pagesSection.getKeys(false)) {
                    int pageNum;
                    try { pageNum = Integer.parseInt(pageKey); } catch (NumberFormatException e) { continue; }
                    ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
                    if (pageSection == null) continue;

                    PageData page = new PageData();
                    page.title = pageSection.getString("title", cat.displayName);

                    var itemsListCfg = pageSection.getMapList("items");
                    for (Map<?, ?> im : itemsListCfg) {
                        PageItem pi = new PageItem();
                        pi.slot = im.containsKey("slot") ? ((Number) im.get("slot")).intValue() : 0;
                        pi.material = Material.matchMaterial(im.containsKey("material") ? String.valueOf(im.get("material")) : "PAPER");
                        pi.name = im.containsKey("name") ? String.valueOf(im.get("name")) : "Item";
                        pi.lore = im.containsKey("lore") ? ((List<?>) im.get("lore")).stream()
                                .map(String::valueOf).toList() : List.of();

                        // Recipe data
                        if (im.containsKey("recipe")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> recipeMap = (Map<String, Object>) im.get("recipe");
                            pi.recipe = parseRecipe(recipeMap);
                        }

                        page.items.add(pi);
                    }
                    cat.pages.put(pageNum, page);
                }
            }
            categories.put(catId, cat);
        }
    }

    @SuppressWarnings("unchecked")
    private RecipeData parseRecipe(Map<?, ?> map) {
        RecipeData r = new RecipeData();
        r.type = map.containsKey("type") ? String.valueOf(map.get("type")) : "shaped";
        if (map.containsKey("pattern")) {
            r.pattern = ((List<?>) map.get("pattern")).stream().map(String::valueOf).toList();
        }
        if (map.containsKey("ingredients")) {
            Map<String, ?> ingMap = (Map<String, ?>) map.get("ingredients");
            for (var entry : ingMap.entrySet()) {
                Map<String, ?> ingData = (Map<String, ?>) entry.getValue();
                RecipeIngredient ing = new RecipeIngredient();
                ing.material = Material.matchMaterial(String.valueOf(
                        ingData.containsKey("material") ? ingData.get("material") : "STONE"));
                ing.name = ingData.containsKey("name") ? String.valueOf(ingData.get("name")) : "";
                r.ingredients.put(entry.getKey().charAt(0), ing);
            }
        }
        if (map.containsKey("result")) {
            Map<String, ?> resMap = (Map<String, ?>) map.get("result");
            r.resultMaterial = Material.matchMaterial(String.valueOf(
                    resMap.containsKey("material") ? resMap.get("material") : "STONE"));
            r.resultName = resMap.containsKey("name") ? String.valueOf(resMap.get("name")) : "Result";
        }
        return r;
    }

    public void reload() { loadConfig(); }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public boolean isAutoOpenFirstJoin() { return autoOpenFirstJoin; }
    public int getFirstJoinDelayTicks() { return firstJoinDelayTicks; }
    public LinkedHashMap<String, CategoryData> getCategories() { return categories; }
    public int getCategoryCount() { return categories.size(); }
    public CategoryData getCategory(String id) { return categories.get(id); }

    public Component parseMiniMessage(String text) {
        return miniMessage.deserialize(text);
    }

    public NamedTextColor resolveColor(String name) {
        NamedTextColor color = NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT));
        return color != null ? color : NamedTextColor.WHITE;
    }

    // ─── Completion tracking ─────────────────────────────────────────────────

    private Connection conn() { return plugin.getDatabaseManager().getConnection(); }

    public Set<String> getVisitedCategories(UUID playerUuid) {
        Set<String> visited = new HashSet<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT category_id FROM tutorial_completion WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) visited.add(rs.getString("category_id"));
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load tutorial progress: " + e.getMessage());
        }
        return visited;
    }

    public void markCategoryVisited(UUID playerUuid, String categoryId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    "INSERT OR IGNORE INTO tutorial_completion (player_uuid, category_id, visited_at) VALUES (?, ?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, categoryId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save tutorial progress: " + e.getMessage());
            }
        });
    }

    public boolean hasClaimedReward(UUID playerUuid) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT 1 FROM tutorial_rewards WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to check tutorial reward: " + e.getMessage());
        }
        return false;
    }

    public boolean hasCompletedAll(UUID playerUuid) {
        return getVisitedCategories(playerUuid).size() >= categories.size();
    }

    public void grantCompletionReward(Player player) {
        UUID uuid = player.getUniqueId();
        if (!hasCompletedAll(uuid) || hasClaimedReward(uuid)) return;

        // Money via Vault
        if (rewardMoney > 0) {
            plugin.getVaultHook().deposit(player, rewardMoney);
        }

        // Items
        for (ItemStack item : rewardItems) {
            var leftover = player.getInventory().addItem(item.clone());
            leftover.values().forEach(drop ->
                    player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }

        // Mark claimed
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = conn().prepareStatement(
                    "INSERT OR IGNORE INTO tutorial_rewards (player_uuid, claimed_at) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save tutorial reward claim: " + e.getMessage());
            }
        });

        player.sendMessage(Component.text("Tutorial completed! You've received your starter rewards!", NamedTextColor.GREEN));
    }

    // ─── Inner data classes ──────────────────────────────────────────────────

    public static class CategoryData {
        public String id;
        public String displayName;
        public Material iconMaterial;
        public int slot;
        public String colorName;
        public Material glassPaneMaterial;
        public List<String> lore;
        public final TreeMap<Integer, PageData> pages = new TreeMap<>();
    }

    public static class PageData {
        public String title;
        public final List<PageItem> items = new ArrayList<>();
    }

    public static class PageItem {
        public int slot;
        public Material material;
        public String name;
        public List<String> lore;
        public RecipeData recipe; // nullable
    }

    public static class RecipeData {
        public String type; // "shaped" or "shapeless"
        public List<String> pattern;
        public final Map<Character, RecipeIngredient> ingredients = new HashMap<>();
        public Material resultMaterial;
        public String resultName;
    }

    public static class RecipeIngredient {
        public Material material;
        public String name;
    }
}
