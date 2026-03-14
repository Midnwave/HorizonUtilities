package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.story.boss.VarethBossManager;
import com.blockforge.horizonutilities.story.integration.MythicMobsHook;
import com.blockforge.horizonutilities.story.sentinel.WitheredSentinel;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StoryManager {

    private final HorizonUtilitiesPlugin plugin;
    private final StoryConfig storyConfig;
    private final StoryBookManager bookManager;
    private final StoryItemManager itemManager;
    private final StoryRegionManager regionManager;
    private final StoryDoorManager doorManager;
    private final MythicMobsHook mythicHook;
    private final VarethBossManager varethBossManager;
    private final WitheredSentinel witheredSentinel;
    private final Map<UUID, StoryPlayerData> cache = new ConcurrentHashMap<>();

    public StoryManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.storyConfig = new StoryConfig(plugin);
        this.bookManager = new StoryBookManager(plugin, storyConfig);
        this.itemManager = new StoryItemManager(plugin);
        this.regionManager = new StoryRegionManager(plugin);
        this.doorManager = new StoryDoorManager(plugin, storyConfig, itemManager);
        this.mythicHook = new MythicMobsHook();
        this.varethBossManager = new VarethBossManager(plugin, this, mythicHook);
        this.witheredSentinel = new WitheredSentinel(plugin, this);
    }

    public void init() {
        storyConfig.load();
    }

    public void reload() {
        storyConfig.load();
        cache.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            loadPlayer(p.getUniqueId());
        }
    }

    public void shutdown() {
        varethBossManager.shutdown();
        for (Map.Entry<UUID, StoryPlayerData> entry : cache.entrySet()) {
            savePlayerSync(entry.getKey(), entry.getValue());
        }
        cache.clear();
    }

    public StoryConfig getStoryConfig() {
        return storyConfig;
    }

    public StoryBookManager getBookManager() {
        return bookManager;
    }

    public StoryItemManager getItemManager() {
        return itemManager;
    }

    public StoryRegionManager getRegionManager() {
        return regionManager;
    }

    public StoryDoorManager getDoorManager() {
        return doorManager;
    }

    public VarethBossManager getVarethBossManager() {
        return varethBossManager;
    }

    public WitheredSentinel getWitheredSentinel() {
        return witheredSentinel;
    }

    public MythicMobsHook getMythicHook() {
        return mythicHook;
    }

    // -------------------------------------------------------------------------
    // Player data
    // -------------------------------------------------------------------------

    public StoryPlayerData getPlayerData(UUID uuid) {
        return cache.get(uuid);
    }

    public void loadPlayer(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = plugin.getDatabaseManager().getConnection();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM story_progress WHERE player_uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            StoryPlayerData data = new StoryPlayerData(
                                rs.getInt("current_stage"),
                                rs.getInt("current_chapter"),
                                parseSet(rs.getString("books_found")),
                                parseSet(rs.getString("items_collected")),
                                parseSet(rs.getString("side_quests_completed")),
                                parseSet(rs.getString("objectives_completed")),
                                rs.getInt("vareth_defeated") == 1
                            );
                            cache.put(uuid, data);
                        } else {
                            cache.put(uuid, new StoryPlayerData());
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[Story] Failed to load player data: " + e.getMessage());
                cache.put(uuid, new StoryPlayerData());
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        StoryPlayerData data = cache.remove(uuid);
        if (data != null) {
            savePlayerAsync(uuid, data);
        }
    }

    public void savePlayerAsync(UUID uuid, StoryPlayerData data) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> savePlayerSync(uuid, data));
    }

    private void savePlayerSync(UUID uuid, StoryPlayerData data) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO story_progress(player_uuid, current_stage, current_chapter, " +
                    "books_found, items_collected, side_quests_completed, objectives_completed, " +
                    "vareth_defeated, started_at, last_updated) VALUES(?,?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET " +
                    "current_stage=excluded.current_stage, current_chapter=excluded.current_chapter, " +
                    "books_found=excluded.books_found, items_collected=excluded.items_collected, " +
                    "side_quests_completed=excluded.side_quests_completed, " +
                    "objectives_completed=excluded.objectives_completed, " +
                    "vareth_defeated=excluded.vareth_defeated, last_updated=excluded.last_updated")) {
                long now = System.currentTimeMillis() / 1000;
                ps.setString(1, uuid.toString());
                ps.setInt(2, data.getCurrentStage());
                ps.setInt(3, data.getCurrentChapter());
                ps.setString(4, joinSet(data.getBooksFound()));
                ps.setString(5, joinSet(data.getItemsCollected()));
                ps.setString(6, joinSet(data.getSideQuestsCompleted()));
                ps.setString(7, joinSet(data.getObjectivesCompleted()));
                ps.setInt(8, data.isVarethDefeated() ? 1 : 0);
                ps.setLong(9, now);
                ps.setLong(10, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Story] Failed to save player data: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Progress helpers
    // -------------------------------------------------------------------------

    public void setStage(UUID uuid, int stage) {
        StoryPlayerData data = cache.get(uuid);
        if (data != null) {
            data.setCurrentStage(stage);
            data.setCurrentChapter(0);
            savePlayerAsync(uuid, data);
        }
    }

    public void setChapter(UUID uuid, int chapter) {
        StoryPlayerData data = cache.get(uuid);
        if (data != null) {
            data.setCurrentChapter(chapter);
            savePlayerAsync(uuid, data);
        }
    }

    public void addBookFound(UUID uuid, String bookId) {
        StoryPlayerData data = cache.get(uuid);
        if (data != null) {
            data.getBooksFound().add(bookId);
            savePlayerAsync(uuid, data);
        }
    }

    public void addItemCollected(UUID uuid, String itemId) {
        StoryPlayerData data = cache.get(uuid);
        if (data != null) {
            data.getItemsCollected().add(itemId);
            savePlayerAsync(uuid, data);
        }
    }

    public void completeObjective(UUID uuid, String objectiveId) {
        StoryPlayerData data = cache.get(uuid);
        if (data != null) {
            data.getObjectivesCompleted().add(objectiveId);
            savePlayerAsync(uuid, data);
        }
    }

    public void completeSideQuest(UUID uuid, String questId) {
        StoryPlayerData data = cache.get(uuid);
        if (data != null) {
            data.getSideQuestsCompleted().add(questId);
            savePlayerAsync(uuid, data);
        }
    }

    public void resetPlayer(UUID uuid) {
        cache.put(uuid, new StoryPlayerData());
        savePlayerAsync(uuid, cache.get(uuid));
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------

    private static Set<String> parseSet(String csv) {
        Set<String> set = new LinkedHashSet<>();
        if (csv != null && !csv.isEmpty()) {
            for (String s : csv.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed);
                }
            }
        }
        return set;
    }

    private static String joinSet(Set<String> set) {
        return String.join(",", set);
    }

    // -------------------------------------------------------------------------
    // Player data class
    // -------------------------------------------------------------------------

    public static class StoryPlayerData {
        private int currentStage;
        private int currentChapter;
        private final Set<String> booksFound;
        private final Set<String> itemsCollected;
        private final Set<String> sideQuestsCompleted;
        private final Set<String> objectivesCompleted;
        private boolean varethDefeated;

        public StoryPlayerData() {
            this(0, 0, new LinkedHashSet<>(), new LinkedHashSet<>(),
                 new LinkedHashSet<>(), new LinkedHashSet<>(), false);
        }

        public StoryPlayerData(int currentStage, int currentChapter,
                               Set<String> booksFound, Set<String> itemsCollected,
                               Set<String> sideQuestsCompleted, Set<String> objectivesCompleted,
                               boolean varethDefeated) {
            this.currentStage = currentStage;
            this.currentChapter = currentChapter;
            this.booksFound = booksFound;
            this.itemsCollected = itemsCollected;
            this.sideQuestsCompleted = sideQuestsCompleted;
            this.objectivesCompleted = objectivesCompleted;
            this.varethDefeated = varethDefeated;
        }

        public int getCurrentStage() { return currentStage; }
        public void setCurrentStage(int stage) { this.currentStage = stage; }

        public int getCurrentChapter() { return currentChapter; }
        public void setCurrentChapter(int chapter) { this.currentChapter = chapter; }

        public Set<String> getBooksFound() { return booksFound; }
        public Set<String> getItemsCollected() { return itemsCollected; }
        public Set<String> getSideQuestsCompleted() { return sideQuestsCompleted; }
        public Set<String> getObjectivesCompleted() { return objectivesCompleted; }

        public boolean isVarethDefeated() { return varethDefeated; }
        public void setVarethDefeated(boolean defeated) { this.varethDefeated = defeated; }
    }
}
