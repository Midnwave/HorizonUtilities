package com.blockforge.horizonutilities.quests.generation;

import com.blockforge.horizonutilities.quests.QuestActionType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.*;

/**
 * Curated, sorted pools of valid targets for each QuestActionType.
 * Pools are built once at startup from Bukkit enums with smart filtering.
 * Sorted alphabetically to ensure deterministic ordering across restarts.
 */
public final class MaterialPools {

    // Block pools (for BREAK action)
    public static final List<String> ORES = new ArrayList<>();
    public static final List<String> STONE_TYPES = new ArrayList<>();
    public static final List<String> LOGS = new ArrayList<>();
    public static final List<String> DIRT_SAND = new ArrayList<>();
    public static final List<String> CROPS = new ArrayList<>();
    public static final List<String> NETHER_BLOCKS = new ArrayList<>();
    public static final List<String> END_BLOCKS = new ArrayList<>();
    public static final List<String> ALL_MINEABLE = new ArrayList<>();

    // Entity pools (for KILL action)
    public static final List<String> HOSTILE_OVERWORLD = new ArrayList<>();
    public static final List<String> HOSTILE_NETHER = new ArrayList<>();
    public static final List<String> HOSTILE_END = new ArrayList<>();
    public static final List<String> PASSIVE_ANIMALS = new ArrayList<>();
    public static final List<String> ALL_HOSTILE = new ArrayList<>();
    public static final List<String> ALL_KILLABLE = new ArrayList<>();

    // Item pools
    public static final List<String> FISH_CATCHES = new ArrayList<>();
    public static final List<String> SMELTABLE_OUTPUTS = new ArrayList<>();
    public static final List<String> CRAFTABLE_TOOLS = new ArrayList<>();
    public static final List<String> CRAFTABLE_ARMOR = new ArrayList<>();
    public static final List<String> CRAFTABLE_WEAPONS = new ArrayList<>();
    public static final List<String> CRAFTABLE_BUILDING = new ArrayList<>();
    public static final List<String> CRAFTABLE_FOOD = new ArrayList<>();
    public static final List<String> CRAFTABLE_REDSTONE = new ArrayList<>();
    public static final List<String> ALL_CRAFTABLE = new ArrayList<>();
    public static final List<String> ENCHANTABLE = new ArrayList<>();
    public static final List<String> EDIBLE = new ArrayList<>();
    public static final List<String> PLACEABLE_BLOCKS = new ArrayList<>();

    // Entity-specific pools
    public static final List<String> BREEDABLE = new ArrayList<>();
    public static final List<String> TAMEABLE = new ArrayList<>();
    public static final List<String> SHEARABLE = new ArrayList<>();

    // Difficulty ratings: material/entity name -> 0.0 (trivial) to 1.0 (very hard)
    public static final Map<String, Double> DIFFICULTY = new HashMap<>();

    // Excluded blocks (not valid for quests)
    private static final Set<String> EXCLUDED_BLOCKS = Set.of(
        "AIR", "CAVE_AIR", "VOID_AIR", "BEDROCK", "BARRIER", "LIGHT",
        "STRUCTURE_BLOCK", "STRUCTURE_VOID", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
        "REPEATING_COMMAND_BLOCK", "JIGSAW", "MOVING_PISTON", "PISTON_HEAD",
        "SPAWNER", "END_PORTAL", "END_PORTAL_FRAME", "NETHER_PORTAL",
        "END_GATEWAY", "FROSTED_ICE", "REINFORCED_DEEPSLATE",
        "BUDDING_AMETHYST", "INFESTED_STONE", "INFESTED_COBBLESTONE",
        "INFESTED_STONE_BRICKS", "INFESTED_CRACKED_STONE_BRICKS",
        "INFESTED_MOSSY_STONE_BRICKS", "INFESTED_CHISELED_STONE_BRICKS",
        "INFESTED_DEEPSLATE", "TRIAL_SPAWNER", "VAULT"
    );

    private static boolean initialized = false;

    private MaterialPools() {}

    /**
     * Initialize all pools. Call once after server is fully loaded.
     */
    public static void initialize() {
        if (initialized) return;
        initialized = true;

        buildBlockPools();
        buildEntityPools();
        buildItemPools();
        buildDifficultyRatings();
    }

    private static void buildBlockPools() {
        for (Material mat : Material.values()) {
            if (!mat.isBlock() || mat.isAir()) continue;
            String name = mat.name();
            if (EXCLUDED_BLOCKS.contains(name)) continue;

            // Ores
            if (name.contains("_ORE") || name.equals("ANCIENT_DEBRIS")) {
                ORES.add(name);
                ALL_MINEABLE.add(name);
                continue;
            }

            // Logs
            if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.equals("CRIMSON_STEM")
                    || name.equals("WARPED_STEM") || name.equals("CRIMSON_HYPHAE") || name.equals("WARPED_HYPHAE")) {
                LOGS.add(name);
                ALL_MINEABLE.add(name);
                continue;
            }

            // Stone types
            if (name.equals("STONE") || name.equals("DEEPSLATE") || name.equals("GRANITE")
                    || name.equals("DIORITE") || name.equals("ANDESITE") || name.equals("TUFF")
                    || name.equals("CALCITE") || name.equals("COBBLESTONE") || name.equals("COBBLED_DEEPSLATE")
                    || name.equals("BLACKSTONE") || name.equals("BASALT")) {
                STONE_TYPES.add(name);
                ALL_MINEABLE.add(name);
                continue;
            }

            // Dirt / Sand
            if (name.equals("DIRT") || name.equals("GRASS_BLOCK") || name.equals("SAND")
                    || name.equals("RED_SAND") || name.equals("GRAVEL") || name.equals("CLAY")
                    || name.equals("SOUL_SAND") || name.equals("SOUL_SOIL") || name.equals("MUD")
                    || name.equals("MYCELIUM") || name.equals("PODZOL") || name.equals("ROOTED_DIRT")) {
                DIRT_SAND.add(name);
                ALL_MINEABLE.add(name);
                continue;
            }

            // Crops
            if (name.equals("WHEAT") || name.equals("CARROTS") || name.equals("POTATOES")
                    || name.equals("BEETROOTS") || name.equals("MELON") || name.equals("PUMPKIN")
                    || name.equals("SWEET_BERRY_BUSH") || name.equals("SUGAR_CANE")
                    || name.equals("BAMBOO") || name.equals("CACTUS") || name.equals("NETHER_WART")
                    || name.equals("COCOA") || name.equals("TORCHFLOWER") || name.equals("PITCHER_PLANT")) {
                CROPS.add(name);
                continue;
            }

            // Nether blocks
            if (name.contains("NETHER") || name.contains("SOUL") || name.contains("CRIMSON")
                    || name.contains("WARPED") || name.equals("GLOWSTONE") || name.equals("MAGMA_BLOCK")
                    || name.equals("BASALT") || name.equals("BLACKSTONE") || name.equals("SHROOMLIGHT")
                    || name.equals("OBSIDIAN") || name.equals("CRYING_OBSIDIAN")) {
                if (!NETHER_BLOCKS.contains(name)) NETHER_BLOCKS.add(name);
                if (!ALL_MINEABLE.contains(name)) ALL_MINEABLE.add(name);
                continue;
            }

            // End blocks
            if (name.equals("END_STONE") || name.equals("END_STONE_BRICKS") || name.equals("PURPUR_BLOCK")
                    || name.equals("PURPUR_PILLAR") || name.equals("CHORUS_PLANT") || name.equals("CHORUS_FLOWER")) {
                END_BLOCKS.add(name);
                ALL_MINEABLE.add(name);
                continue;
            }

            // General mineable blocks
            if (mat.isItem()) {
                ALL_MINEABLE.add(name);
            }
        }

        // Placeable blocks (blocks that are also items)
        for (Material mat : Material.values()) {
            if (mat.isBlock() && mat.isItem() && !mat.isAir() && !EXCLUDED_BLOCKS.contains(mat.name())) {
                PLACEABLE_BLOCKS.add(mat.name());
            }
        }

        // Sort all for determinism
        ORES.sort(String::compareTo);
        STONE_TYPES.sort(String::compareTo);
        LOGS.sort(String::compareTo);
        DIRT_SAND.sort(String::compareTo);
        CROPS.sort(String::compareTo);
        NETHER_BLOCKS.sort(String::compareTo);
        END_BLOCKS.sort(String::compareTo);
        ALL_MINEABLE.sort(String::compareTo);
        PLACEABLE_BLOCKS.sort(String::compareTo);
    }

    private static void buildEntityPools() {
        // Hostile overworld
        for (String name : List.of("ZOMBIE", "SKELETON", "CREEPER", "SPIDER", "CAVE_SPIDER",
                "ENDERMAN", "WITCH", "SLIME", "PHANTOM", "DROWNED", "HUSK", "STRAY",
                "ZOMBIE_VILLAGER", "SILVERFISH", "VINDICATOR", "PILLAGER", "RAVAGER",
                "EVOKER", "VEX", "GUARDIAN", "ELDER_GUARDIAN", "WARDEN")) {
            if (isValidEntity(name)) {
                HOSTILE_OVERWORLD.add(name);
                ALL_HOSTILE.add(name);
                ALL_KILLABLE.add(name);
            }
        }

        // Hostile nether
        for (String name : List.of("BLAZE", "GHAST", "MAGMA_CUBE", "WITHER_SKELETON",
                "HOGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN")) {
            if (isValidEntity(name)) {
                HOSTILE_NETHER.add(name);
                ALL_HOSTILE.add(name);
                ALL_KILLABLE.add(name);
            }
        }

        // Hostile end
        for (String name : List.of("ENDERMAN", "SHULKER", "ENDERMITE")) {
            if (isValidEntity(name)) {
                if (!HOSTILE_END.contains(name)) HOSTILE_END.add(name);
                if (!ALL_HOSTILE.contains(name)) ALL_HOSTILE.add(name);
                if (!ALL_KILLABLE.contains(name)) ALL_KILLABLE.add(name);
            }
        }

        // Passive animals
        for (String name : List.of("COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "HORSE",
                "DONKEY", "MOOSHROOM", "TURTLE", "FOX", "AXOLOTL", "GOAT", "FROG",
                "CAMEL", "SNIFFER", "BEE", "WOLF", "CAT", "PARROT", "LLAMA", "PANDA",
                "POLAR_BEAR", "DOLPHIN", "SQUID", "GLOW_SQUID", "SALMON", "COD",
                "TROPICAL_FISH", "PUFFERFISH")) {
            if (isValidEntity(name)) {
                PASSIVE_ANIMALS.add(name);
                ALL_KILLABLE.add(name);
            }
        }

        // Breedable
        for (String name : List.of("COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "HORSE",
                "DONKEY", "WOLF", "CAT", "BEE", "TURTLE", "FOX", "AXOLOTL", "GOAT",
                "FROG", "CAMEL", "SNIFFER", "MOOSHROOM", "PANDA", "LLAMA")) {
            if (isValidEntity(name)) BREEDABLE.add(name);
        }

        // Tameable
        for (String name : List.of("WOLF", "CAT", "PARROT", "HORSE", "DONKEY", "MULE", "LLAMA")) {
            if (isValidEntity(name)) TAMEABLE.add(name);
        }

        // Shearable
        SHEARABLE.addAll(List.of("SHEEP", "MOOSHROOM", "SNOW_GOLEM"));

        // Sort
        HOSTILE_OVERWORLD.sort(String::compareTo);
        HOSTILE_NETHER.sort(String::compareTo);
        HOSTILE_END.sort(String::compareTo);
        PASSIVE_ANIMALS.sort(String::compareTo);
        ALL_HOSTILE.sort(String::compareTo);
        ALL_KILLABLE.sort(String::compareTo);
        BREEDABLE.sort(String::compareTo);
        TAMEABLE.sort(String::compareTo);
        SHEARABLE.sort(String::compareTo);
    }

    private static void buildItemPools() {
        // Fish catches
        FISH_CATCHES.addAll(List.of("COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH",
                "SADDLE", "NAME_TAG", "BOW", "FISHING_ROD", "ENCHANTED_BOOK",
                "LILY_PAD", "BOWL", "LEATHER", "LEATHER_BOOTS", "STICK",
                "STRING", "INK_SAC", "TRIPWIRE_HOOK", "ROTTEN_FLESH", "BONE"));
        FISH_CATCHES.sort(String::compareTo);

        // Smeltable outputs
        SMELTABLE_OUTPUTS.addAll(List.of("IRON_INGOT", "GOLD_INGOT", "COPPER_INGOT",
                "GLASS", "STONE", "SMOOTH_STONE", "CHARCOAL", "BRICK", "NETHER_BRICK",
                "TERRACOTTA", "GREEN_DYE", "LIME_DYE", "SPONGE",
                "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN", "COOKED_MUTTON",
                "COOKED_RABBIT", "COOKED_SALMON", "COOKED_COD", "BAKED_POTATO",
                "DRIED_KELP", "NETHERITE_SCRAP", "CRACKED_STONE_BRICKS",
                "CRACKED_NETHER_BRICKS", "CRACKED_DEEPSLATE_BRICKS"));
        SMELTABLE_OUTPUTS.sort(String::compareTo);

        // Craftable items by category
        CRAFTABLE_TOOLS.addAll(List.of("WOODEN_PICKAXE", "STONE_PICKAXE", "IRON_PICKAXE",
                "GOLDEN_PICKAXE", "DIAMOND_PICKAXE", "WOODEN_AXE", "STONE_AXE", "IRON_AXE",
                "GOLDEN_AXE", "DIAMOND_AXE", "WOODEN_SHOVEL", "STONE_SHOVEL", "IRON_SHOVEL",
                "GOLDEN_SHOVEL", "DIAMOND_SHOVEL", "WOODEN_HOE", "STONE_HOE", "IRON_HOE",
                "GOLDEN_HOE", "DIAMOND_HOE", "SHEARS", "FLINT_AND_STEEL", "COMPASS",
                "CLOCK", "FISHING_ROD", "LEAD", "BUCKET", "SPYGLASS"));
        CRAFTABLE_ARMOR.addAll(List.of("LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS",
                "LEATHER_BOOTS", "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
                "GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS",
                "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
                "SHIELD", "TURTLE_HELMET"));
        CRAFTABLE_WEAPONS.addAll(List.of("WOODEN_SWORD", "STONE_SWORD", "IRON_SWORD",
                "GOLDEN_SWORD", "DIAMOND_SWORD", "BOW", "CROSSBOW", "ARROW",
                "SPECTRAL_ARROW", "TIPPED_ARROW"));
        CRAFTABLE_BUILDING.addAll(List.of("CRAFTING_TABLE", "FURNACE", "CHEST",
                "BARREL", "SMOKER", "BLAST_FURNACE", "STONECUTTER", "GRINDSTONE",
                "CARTOGRAPHY_TABLE", "FLETCHING_TABLE", "SMITHING_TABLE", "LOOM",
                "COMPOSTER", "LECTERN", "BOOKSHELF", "LADDER", "TORCH",
                "LANTERN", "SOUL_LANTERN", "CAMPFIRE", "SOUL_CAMPFIRE",
                "OAK_PLANKS", "SPRUCE_PLANKS", "BIRCH_PLANKS", "JUNGLE_PLANKS",
                "ACACIA_PLANKS", "DARK_OAK_PLANKS", "CHERRY_PLANKS", "BAMBOO_PLANKS",
                "GLASS_PANE", "IRON_BARS", "RAIL", "POWERED_RAIL", "DETECTOR_RAIL",
                "ACTIVATOR_RAIL", "MINECART", "BOAT"));
        CRAFTABLE_FOOD.addAll(List.of("BREAD", "CAKE", "COOKIE", "PUMPKIN_PIE",
                "GOLDEN_APPLE", "GOLDEN_CARROT", "MUSHROOM_STEW", "BEETROOT_SOUP",
                "RABBIT_STEW", "SUSPICIOUS_STEW", "HONEY_BOTTLE"));
        CRAFTABLE_REDSTONE.addAll(List.of("REDSTONE_TORCH", "REPEATER", "COMPARATOR",
                "PISTON", "STICKY_PISTON", "OBSERVER", "DISPENSER", "DROPPER",
                "HOPPER", "LEVER", "STONE_BUTTON", "OAK_BUTTON", "TRIPWIRE_HOOK",
                "DAYLIGHT_DETECTOR", "NOTE_BLOCK", "TNT", "TARGET"));

        ALL_CRAFTABLE.addAll(CRAFTABLE_TOOLS);
        ALL_CRAFTABLE.addAll(CRAFTABLE_ARMOR);
        ALL_CRAFTABLE.addAll(CRAFTABLE_WEAPONS);
        ALL_CRAFTABLE.addAll(CRAFTABLE_BUILDING);
        ALL_CRAFTABLE.addAll(CRAFTABLE_FOOD);
        ALL_CRAFTABLE.addAll(CRAFTABLE_REDSTONE);

        // Enchantable
        ENCHANTABLE.addAll(CRAFTABLE_TOOLS);
        ENCHANTABLE.addAll(CRAFTABLE_ARMOR);
        ENCHANTABLE.addAll(CRAFTABLE_WEAPONS);
        ENCHANTABLE.add("BOOK");
        ENCHANTABLE.add("TRIDENT");

        // Edible
        for (Material mat : Material.values()) {
            if (mat.isEdible()) EDIBLE.add(mat.name());
        }

        // Sort all
        CRAFTABLE_TOOLS.sort(String::compareTo);
        CRAFTABLE_ARMOR.sort(String::compareTo);
        CRAFTABLE_WEAPONS.sort(String::compareTo);
        CRAFTABLE_BUILDING.sort(String::compareTo);
        CRAFTABLE_FOOD.sort(String::compareTo);
        CRAFTABLE_REDSTONE.sort(String::compareTo);
        ALL_CRAFTABLE.sort(String::compareTo);
        ENCHANTABLE.sort(String::compareTo);
        EDIBLE.sort(String::compareTo);
    }

    private static void buildDifficultyRatings() {
        // Ores by rarity
        diff("COAL_ORE", 0.1); diff("DEEPSLATE_COAL_ORE", 0.15);
        diff("COPPER_ORE", 0.15); diff("DEEPSLATE_COPPER_ORE", 0.2);
        diff("IRON_ORE", 0.2); diff("DEEPSLATE_IRON_ORE", 0.25);
        diff("GOLD_ORE", 0.35); diff("DEEPSLATE_GOLD_ORE", 0.4);
        diff("LAPIS_ORE", 0.3); diff("DEEPSLATE_LAPIS_ORE", 0.35);
        diff("REDSTONE_ORE", 0.25); diff("DEEPSLATE_REDSTONE_ORE", 0.3);
        diff("DIAMOND_ORE", 0.7); diff("DEEPSLATE_DIAMOND_ORE", 0.75);
        diff("EMERALD_ORE", 0.8); diff("DEEPSLATE_EMERALD_ORE", 0.85);
        diff("NETHER_GOLD_ORE", 0.3); diff("NETHER_QUARTZ_ORE", 0.2);
        diff("ANCIENT_DEBRIS", 0.95);

        // Stone types (easy)
        for (String s : STONE_TYPES) diff(s, 0.05);
        for (String s : DIRT_SAND) diff(s, 0.03);
        for (String s : LOGS) diff(s, 0.08);

        // Hostile mobs
        diff("ZOMBIE", 0.15); diff("SKELETON", 0.2); diff("CREEPER", 0.25);
        diff("SPIDER", 0.15); diff("CAVE_SPIDER", 0.3); diff("ENDERMAN", 0.4);
        diff("WITCH", 0.35); diff("SLIME", 0.2); diff("PHANTOM", 0.3);
        diff("DROWNED", 0.2); diff("HUSK", 0.2); diff("STRAY", 0.25);
        diff("GUARDIAN", 0.45); diff("ELDER_GUARDIAN", 0.85);
        diff("WARDEN", 0.95); diff("BLAZE", 0.4); diff("GHAST", 0.45);
        diff("MAGMA_CUBE", 0.3); diff("WITHER_SKELETON", 0.5);
        diff("HOGLIN", 0.4); diff("PIGLIN_BRUTE", 0.55);
        diff("SHULKER", 0.5); diff("ENDERMITE", 0.2);
        diff("VINDICATOR", 0.4); diff("PILLAGER", 0.3); diff("RAVAGER", 0.6);
        diff("EVOKER", 0.5); diff("VEX", 0.35);

        // Passive (easy to find/kill)
        for (String s : PASSIVE_ANIMALS) diff(s, 0.05);

        // Crafting materials by tier
        diff("NETHERITE_SCRAP", 0.9);
        diff("DIAMOND_PICKAXE", 0.6); diff("DIAMOND_SWORD", 0.6);
        diff("DIAMOND_CHESTPLATE", 0.7); diff("DIAMOND_HELMET", 0.6);
        diff("IRON_PICKAXE", 0.25); diff("IRON_SWORD", 0.25);
        diff("GOLDEN_APPLE", 0.5); diff("GOLDEN_CARROT", 0.35);

        // Fish (common to rare)
        diff("COD", 0.1); diff("SALMON", 0.15);
        diff("TROPICAL_FISH", 0.3); diff("PUFFERFISH", 0.35);
        diff("SADDLE", 0.6); diff("NAME_TAG", 0.6); diff("ENCHANTED_BOOK", 0.7);

        // Smelting
        diff("IRON_INGOT", 0.2); diff("GOLD_INGOT", 0.35); diff("COPPER_INGOT", 0.15);
        diff("CHARCOAL", 0.05); diff("GLASS", 0.05); diff("STONE", 0.05);
        diff("BRICK", 0.1); diff("COOKED_BEEF", 0.1); diff("COOKED_PORKCHOP", 0.1);
    }

    private static void diff(String name, double difficulty) {
        DIFFICULTY.put(name, difficulty);
    }

    /**
     * Get the difficulty rating for a target. Defaults to 0.2 if not explicitly set.
     */
    public static double getDifficulty(String target) {
        return DIFFICULTY.getOrDefault(target, 0.2);
    }

    /**
     * Get the appropriate target pool for an action type.
     */
    public static List<String> getPoolForAction(QuestActionType action) {
        return switch (action) {
            case BREAK -> ALL_MINEABLE;
            case PLACE -> PLACEABLE_BLOCKS;
            case KILL -> ALL_KILLABLE;
            case FISH -> FISH_CATCHES;
            case CRAFT -> ALL_CRAFTABLE;
            case SMELT -> SMELTABLE_OUTPUTS;
            case ENCHANT -> ENCHANTABLE;
            case EAT -> EDIBLE;
            case BREED -> BREEDABLE;
            case TAME -> TAMEABLE;
            case SHEAR -> SHEARABLE;
            case FARM -> CROPS;
            case MILK -> List.of("COW", "MOOSHROOM", "GOAT");
            case BREW, REPAIR, EXPLORE_CHUNK, EXPLORE_DISTANCE, TRADE_PLAYER, TRADE_AH -> List.of();
        };
    }

    /**
     * Whether this action type requires a specific target material/entity.
     */
    public static boolean needsTarget(QuestActionType action) {
        return switch (action) {
            case BREW, REPAIR, EXPLORE_CHUNK, EXPLORE_DISTANCE, TRADE_PLAYER, TRADE_AH -> false;
            default -> true;
        };
    }

    private static boolean isValidEntity(String name) {
        try {
            EntityType.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
