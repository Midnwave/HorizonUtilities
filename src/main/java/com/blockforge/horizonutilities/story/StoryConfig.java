package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class StoryConfig {

    private final HorizonUtilitiesPlugin plugin;
    private FileConfiguration config;

    // Settings
    private boolean enabled;
    private boolean firstJoinMessageEnabled;
    private boolean firstJoinBookEnabled;
    private int firstJoinDelayTicks;

    // First join
    private List<String> firstJoinMessage;
    private String firstJoinBookTitle;
    private String firstJoinBookAuthor;
    private List<String> firstJoinBookPages;

    // Stages
    private final Map<Integer, StageData> stages = new LinkedHashMap<>();

    // Books
    private final Map<String, Map<String, BookData>> books = new LinkedHashMap<>();

    // Doors
    private final Map<String, Map<String, DoorData>> doors = new LinkedHashMap<>();

    // Sentinel
    private String sentinelName;
    private double sentinelHealth;
    private double sentinelDamage;
    private String sentinelDrop;
    private double boneThrowDamage;
    private int vigilStanceCooldown;
    private int vigilStanceDuration;
    private double vigilStanceDamageReduction;

    // Vareth
    private String varethMythicId;
    private String varethBossBarTitle;
    private String varethBossBarColor;
    private int varethCinematicTicks;
    private boolean varethArenaSeal;
    private List<Map<String, Object>> varethDrops;
    private String varethDeathTitle;
    private String varethDeathSubtitle;

    // Completion
    private final Map<String, CompletionData> completionMessages = new HashMap<>();

    public StoryConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "story-config.yml");
        if (!file.exists()) {
            plugin.saveResource("story-config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Settings
        enabled = config.getBoolean("settings.enabled", true);
        firstJoinMessageEnabled = config.getBoolean("settings.first-join-message-enabled", true);
        firstJoinBookEnabled = config.getBoolean("settings.first-join-book-enabled", true);
        firstJoinDelayTicks = config.getInt("settings.first-join-delay-ticks", 60);

        // First join message
        firstJoinMessage = config.getStringList("first-join-message");

        // First join book
        firstJoinBookTitle = config.getString("first-join-book.title", "A Whisper in Stone");
        firstJoinBookAuthor = config.getString("first-join-book.author", "Unknown");
        firstJoinBookPages = config.getStringList("first-join-book.pages");

        // Stages
        stages.clear();
        ConfigurationSection stagesSection = config.getConfigurationSection("stages");
        if (stagesSection != null) {
            for (String key : stagesSection.getKeys(false)) {
                int stageNum = Integer.parseInt(key);
                ConfigurationSection stageSection = stagesSection.getConfigurationSection(key);
                if (stageSection != null) {
                    stages.put(stageNum, loadStage(stageSection));
                }
            }
        }

        // Books
        books.clear();
        ConfigurationSection booksSection = config.getConfigurationSection("books");
        if (booksSection != null) {
            for (String stageKey : booksSection.getKeys(false)) {
                Map<String, BookData> stageBooks = new LinkedHashMap<>();
                ConfigurationSection stageBooksSection = booksSection.getConfigurationSection(stageKey);
                if (stageBooksSection != null) {
                    for (String bookKey : stageBooksSection.getKeys(false)) {
                        ConfigurationSection bookSection = stageBooksSection.getConfigurationSection(bookKey);
                        if (bookSection != null) {
                            stageBooks.put(bookKey, new BookData(
                                bookSection.getString("title", "Unknown"),
                                bookSection.getString("author", "Unknown"),
                                bookSection.getStringList("pages")
                            ));
                        }
                    }
                }
                books.put(stageKey, stageBooks);
            }
        }

        // Doors
        doors.clear();
        ConfigurationSection doorsSection = config.getConfigurationSection("doors");
        if (doorsSection != null) {
            for (String stageKey : doorsSection.getKeys(false)) {
                Map<String, DoorData> stageDoors = new LinkedHashMap<>();
                ConfigurationSection stageDoorsSection = doorsSection.getConfigurationSection(stageKey);
                if (stageDoorsSection != null) {
                    for (String doorKey : stageDoorsSection.getKeys(false)) {
                        ConfigurationSection doorSection = stageDoorsSection.getConfigurationSection(doorKey);
                        if (doorSection != null) {
                            stageDoors.put(doorKey, new DoorData(
                                doorSection.getInt("width", 2),
                                doorSection.getInt("height", 3),
                                Material.matchMaterial(doorSection.getString("material", "POLISHED_BLACKSTONE_BRICKS")),
                                doorSection.getInt("animation-speed-ticks", 8),
                                doorSection.getString("required-item", "wardens_key"),
                                doorSection.getString("deny-message", "<red>The door is sealed."),
                                doorSection.getString("open-message", "<dark_purple>The door remembers.")
                            ));
                        }
                    }
                }
                doors.put(stageKey, stageDoors);
            }
        }

        // Sentinel
        sentinelName = config.getString("sentinel.name", "<dark_red>The Withered Sentinel");
        sentinelHealth = config.getDouble("sentinel.health", 80);
        sentinelDamage = config.getDouble("sentinel.damage", 8.0);
        sentinelDrop = config.getString("sentinel.drop", "vareths_insignia");
        boneThrowDamage = config.getDouble("sentinel.bone-throw-damage", 6.0);
        vigilStanceCooldown = config.getInt("sentinel.vigil-stance-cooldown-seconds", 30);
        vigilStanceDuration = config.getInt("sentinel.vigil-stance-duration-seconds", 5);
        vigilStanceDamageReduction = config.getDouble("sentinel.vigil-stance-damage-reduction", 0.5);

        // Vareth
        varethMythicId = config.getString("vareth.mythicmobs-id", "Vareth");
        varethBossBarTitle = config.getString("vareth.boss-bar-title", "<dark_red>☠ Vareth, Herald of Ruin ☠");
        varethBossBarColor = config.getString("vareth.boss-bar-color", "PURPLE");
        varethCinematicTicks = config.getInt("vareth.cinematic-duration-ticks", 60);
        varethArenaSeal = config.getBoolean("vareth.arena-seal-enabled", true);
        varethDrops = config.getMapList("vareth.drops").stream()
            .map(m -> new HashMap<String, Object>(m.entrySet().stream()
                .collect(HashMap::new, (map, e) -> map.put(String.valueOf(e.getKey()), e.getValue()), HashMap::putAll)))
            .map(m -> (Map<String, Object>) m)
            .toList();
        varethDeathTitle = config.getString("vareth.death-title", "<dark_red>The vigil... ends.");
        varethDeathSubtitle = config.getString("vareth.death-subtitle", "<gray>Keep walking.");

        // Completion
        completionMessages.clear();
        ConfigurationSection completionSection = config.getConfigurationSection("completion");
        if (completionSection != null) {
            for (String key : completionSection.getKeys(false)) {
                ConfigurationSection cs = completionSection.getConfigurationSection(key);
                if (cs != null) {
                    completionMessages.put(key, new CompletionData(
                        cs.getString("title", ""),
                        cs.getString("subtitle", "")
                    ));
                }
            }
        }

        plugin.getLogger().info("Hollow Chronicle configuration loaded.");
    }

    private StageData loadStage(ConfigurationSection section) {
        String name = section.getString("name", "Unknown Stage");
        String description = section.getString("description", "");
        Material iconMaterial = Material.matchMaterial(section.getString("icon-material", "GRAY_DYE"));
        if (iconMaterial == null) iconMaterial = Material.GRAY_DYE;
        int totalChapters = section.getInt("total-chapters", 0);

        Map<Integer, ChapterData> chapters = new LinkedHashMap<>();
        ConfigurationSection chaptersSection = section.getConfigurationSection("chapters");
        if (chaptersSection != null) {
            for (String chapterKey : chaptersSection.getKeys(false)) {
                int chapterNum = Integer.parseInt(chapterKey);
                ConfigurationSection chapterSection = chaptersSection.getConfigurationSection(chapterKey);
                if (chapterSection != null) {
                    chapters.put(chapterNum, loadChapter(chapterSection));
                }
            }
        }

        List<SideQuestData> sideQuests = new ArrayList<>();
        List<Map<?, ?>> sqList = section.getMapList("side-quests");
        for (Map<?, ?> sqMap : sqList) {
            sideQuests.add(loadSideQuest(sqMap));
        }

        return new StageData(name, description, iconMaterial, totalChapters, chapters, sideQuests);
    }

    private ChapterData loadChapter(ConfigurationSection section) {
        String name = section.getString("name", "");
        String description = section.getString("description", "");
        String triggerType = section.getString("trigger-type", "");
        String triggerValue = section.getString("trigger-value", "");

        List<ObjectiveData> objectives = new ArrayList<>();
        List<Map<?, ?>> objList = section.getMapList("objectives");
        for (Map<?, ?> objMap : objList) {
            objectives.add(parseObjective(objMap));
        }

        return new ChapterData(name, description, triggerType, triggerValue, objectives);
    }

    private SideQuestData loadSideQuest(Map<?, ?> map) {
        String id = mapStr(map, "id", "");
        String name = mapStr(map, "name", "");
        String description = mapStr(map, "description", "");
        Material icon = Material.matchMaterial(mapStr(map, "icon-material", "PAPER"));
        if (icon == null) icon = Material.PAPER;
        String rewardItem = mapStr(map, "reward-item", "");

        List<ObjectiveData> objectives = new ArrayList<>();
        Object objRaw = map.get("objectives");
        if (objRaw instanceof List<?> objList) {
            for (Object obj : objList) {
                if (obj instanceof Map<?, ?> objMap) {
                    objectives.add(parseObjective(objMap));
                }
            }
        }

        return new SideQuestData(id, name, description, icon, objectives, rewardItem);
    }

    private ObjectiveData parseObjective(Map<?, ?> objMap) {
        return new ObjectiveData(
            mapStr(objMap, "id", ""),
            mapStr(objMap, "type", ""),
            mapStr(objMap, "description", ""),
            mapStr(objMap, "target", ""),
            objMap.containsKey("count") ? ((Number) objMap.get("count")).intValue() : 1
        );
    }

    private static String mapStr(Map<?, ?> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : def;
    }

    public FileConfiguration getRawConfig() { return config; }

    // Settings getters
    public boolean isEnabled() { return enabled; }
    public boolean isFirstJoinMessageEnabled() { return firstJoinMessageEnabled; }
    public boolean isFirstJoinBookEnabled() { return firstJoinBookEnabled; }
    public int getFirstJoinDelayTicks() { return firstJoinDelayTicks; }

    // First join getters
    public List<String> getFirstJoinMessage() { return firstJoinMessage; }
    public String getFirstJoinBookTitle() { return firstJoinBookTitle; }
    public String getFirstJoinBookAuthor() { return firstJoinBookAuthor; }
    public List<String> getFirstJoinBookPages() { return firstJoinBookPages; }

    // Stage getters
    public Map<Integer, StageData> getStages() { return stages; }
    public StageData getStage(int stageNum) { return stages.get(stageNum); }

    // Book getters
    public Map<String, BookData> getBooksForStage(String stageKey) { return books.getOrDefault(stageKey, Map.of()); }
    public BookData getBook(String stageKey, String bookId) {
        Map<String, BookData> stageBooks = books.get(stageKey);
        return stageBooks != null ? stageBooks.get(bookId) : null;
    }

    // Door getters
    public Map<String, DoorData> getDoorsForStage(String stageKey) { return doors.getOrDefault(stageKey, Map.of()); }
    public DoorData getDoor(String stageKey, String doorId) {
        Map<String, DoorData> stageDoors = doors.get(stageKey);
        return stageDoors != null ? stageDoors.get(doorId) : null;
    }

    // Sentinel getters
    public String getSentinelName() { return sentinelName; }
    public double getSentinelHealth() { return sentinelHealth; }
    public double getSentinelDamage() { return sentinelDamage; }
    public String getSentinelDrop() { return sentinelDrop; }
    public double getBoneThrowDamage() { return boneThrowDamage; }
    public int getVigilStanceCooldown() { return vigilStanceCooldown; }
    public int getVigilStanceDuration() { return vigilStanceDuration; }
    public double getVigilStanceDamageReduction() { return vigilStanceDamageReduction; }

    // Vareth getters
    public String getVarethMythicId() { return varethMythicId; }
    public String getVarethBossBarTitle() { return varethBossBarTitle; }
    public String getVarethBossBarColor() { return varethBossBarColor; }
    public int getVarethCinematicTicks() { return varethCinematicTicks; }
    public boolean isVarethArenaSeal() { return varethArenaSeal; }
    public List<Map<String, Object>> getVarethDrops() { return varethDrops; }
    public String getVarethDeathTitle() { return varethDeathTitle; }
    public String getVarethDeathSubtitle() { return varethDeathSubtitle; }

    // Completion getters
    public CompletionData getCompletion(String key) { return completionMessages.get(key); }

    // Data records
    public record StageData(String name, String description, Material iconMaterial,
                            int totalChapters, Map<Integer, ChapterData> chapters,
                            List<SideQuestData> sideQuests) {}

    public record ChapterData(String name, String description, String triggerType,
                              String triggerValue, List<ObjectiveData> objectives) {}

    public record ObjectiveData(String id, String type, String description,
                                String target, int count) {}

    public record SideQuestData(String id, String name, String description,
                                Material icon, List<ObjectiveData> objectives,
                                String rewardItem) {}

    public record BookData(String title, String author, List<String> pages) {}

    public record DoorData(int width, int height, Material material,
                           int animationSpeedTicks, String requiredItem,
                           String denyMessage, String openMessage) {}

    public record CompletionData(String title, String subtitle) {}
}
