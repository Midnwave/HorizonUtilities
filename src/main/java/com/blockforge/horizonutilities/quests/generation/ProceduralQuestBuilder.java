package com.blockforge.horizonutilities.quests.generation;

import com.blockforge.horizonutilities.quests.QuestActionType;
import com.blockforge.horizonutilities.quests.QuestCategory;
import com.blockforge.horizonutilities.quests.QuestConfig;
import com.blockforge.horizonutilities.quests.model.ActiveQuest;
import com.blockforge.horizonutilities.quests.rewards.RewardDefinition;
import org.bukkit.Material;

import java.util.*;

/**
 * Fully procedural quest builder. Generates infinite unique quests from
 * Bukkit's Material/EntityType enums + seed-based RNG. No templates needed.
 *
 * The same seed always produces the exact same quest (deterministic).
 */
public final class ProceduralQuestBuilder {

    // Verb options per action type for description generation
    private static final Map<QuestActionType, String[]> VERBS = new EnumMap<>(QuestActionType.class);
    static {
        VERBS.put(QuestActionType.BREAK,    new String[]{"Mine", "Break", "Dig up", "Collect"});
        VERBS.put(QuestActionType.PLACE,    new String[]{"Place", "Build with", "Set down"});
        VERBS.put(QuestActionType.KILL,     new String[]{"Slay", "Defeat", "Hunt", "Eliminate"});
        VERBS.put(QuestActionType.FISH,     new String[]{"Catch", "Fish up", "Reel in"});
        VERBS.put(QuestActionType.CRAFT,    new String[]{"Craft", "Create", "Make"});
        VERBS.put(QuestActionType.SMELT,    new String[]{"Smelt", "Cook", "Refine"});
        VERBS.put(QuestActionType.BREW,     new String[]{"Brew", "Concoct"});
        VERBS.put(QuestActionType.ENCHANT,  new String[]{"Enchant", "Imbue"});
        VERBS.put(QuestActionType.REPAIR,   new String[]{"Repair"});
        VERBS.put(QuestActionType.TAME,     new String[]{"Tame", "Befriend"});
        VERBS.put(QuestActionType.SHEAR,    new String[]{"Shear"});
        VERBS.put(QuestActionType.MILK,     new String[]{"Milk"});
        VERBS.put(QuestActionType.FARM,     new String[]{"Harvest", "Gather", "Farm"});
        VERBS.put(QuestActionType.EAT,      new String[]{"Eat", "Consume"});
        VERBS.put(QuestActionType.BREED,    new String[]{"Breed", "Raise"});
        VERBS.put(QuestActionType.EXPLORE_CHUNK,    new String[]{"Explore"});
        VERBS.put(QuestActionType.EXPLORE_DISTANCE, new String[]{"Travel"});
        VERBS.put(QuestActionType.TRADE_PLAYER,     new String[]{"Trade with players"});
        VERBS.put(QuestActionType.TRADE_AH,         new String[]{"Sell on auction house"});
    }

    // Base amount ranges per action [min, max] before tier scaling
    private static final Map<QuestActionType, int[]> BASE_AMOUNTS = new EnumMap<>(QuestActionType.class);
    static {
        BASE_AMOUNTS.put(QuestActionType.BREAK,    new int[]{128, 384});
        BASE_AMOUNTS.put(QuestActionType.PLACE,    new int[]{48, 256});
        BASE_AMOUNTS.put(QuestActionType.KILL,     new int[]{12, 64});
        BASE_AMOUNTS.put(QuestActionType.FISH,     new int[]{6, 32});
        BASE_AMOUNTS.put(QuestActionType.CRAFT,    new int[]{6, 40});
        BASE_AMOUNTS.put(QuestActionType.SMELT,    new int[]{16, 96});
        BASE_AMOUNTS.put(QuestActionType.BREW,     new int[]{3, 18});
        BASE_AMOUNTS.put(QuestActionType.ENCHANT,  new int[]{1, 10});
        BASE_AMOUNTS.put(QuestActionType.REPAIR,   new int[]{1, 6});
        BASE_AMOUNTS.put(QuestActionType.TAME,     new int[]{1, 6});
        BASE_AMOUNTS.put(QuestActionType.SHEAR,    new int[]{6, 32});
        BASE_AMOUNTS.put(QuestActionType.MILK,     new int[]{5, 24});
        BASE_AMOUNTS.put(QuestActionType.FARM,     new int[]{24, 128});
        BASE_AMOUNTS.put(QuestActionType.EAT,      new int[]{8, 48});
        BASE_AMOUNTS.put(QuestActionType.BREED,    new int[]{3, 16});
        BASE_AMOUNTS.put(QuestActionType.EXPLORE_CHUNK,    new int[]{15, 150});
        BASE_AMOUNTS.put(QuestActionType.EXPLORE_DISTANCE, new int[]{1000, 8000});
        BASE_AMOUNTS.put(QuestActionType.TRADE_PLAYER,     new int[]{1, 6});
        BASE_AMOUNTS.put(QuestActionType.TRADE_AH,         new int[]{1, 6});
    }

    // Intrinsic difficulty multiplier per action (affects reward calc)
    private static final Map<QuestActionType, Double> ACTION_DIFFICULTY = new EnumMap<>(QuestActionType.class);
    static {
        ACTION_DIFFICULTY.put(QuestActionType.BREAK, 0.3);
        ACTION_DIFFICULTY.put(QuestActionType.PLACE, 0.2);
        ACTION_DIFFICULTY.put(QuestActionType.KILL, 0.6);
        ACTION_DIFFICULTY.put(QuestActionType.FISH, 0.5);
        ACTION_DIFFICULTY.put(QuestActionType.CRAFT, 0.4);
        ACTION_DIFFICULTY.put(QuestActionType.SMELT, 0.3);
        ACTION_DIFFICULTY.put(QuestActionType.BREW, 0.7);
        ACTION_DIFFICULTY.put(QuestActionType.ENCHANT, 0.8);
        ACTION_DIFFICULTY.put(QuestActionType.REPAIR, 0.5);
        ACTION_DIFFICULTY.put(QuestActionType.TAME, 0.6);
        ACTION_DIFFICULTY.put(QuestActionType.SHEAR, 0.4);
        ACTION_DIFFICULTY.put(QuestActionType.MILK, 0.35);
        ACTION_DIFFICULTY.put(QuestActionType.FARM, 0.3);
        ACTION_DIFFICULTY.put(QuestActionType.EAT, 0.25);
        ACTION_DIFFICULTY.put(QuestActionType.BREED, 0.5);
        ACTION_DIFFICULTY.put(QuestActionType.EXPLORE_CHUNK, 0.5);
        ACTION_DIFFICULTY.put(QuestActionType.EXPLORE_DISTANCE, 0.4);
        ACTION_DIFFICULTY.put(QuestActionType.TRADE_PLAYER, 0.5);
        ACTION_DIFFICULTY.put(QuestActionType.TRADE_AH, 0.4);
    }

    // Actions valid per job
    private static final Map<String, List<QuestActionType>> JOB_ACTIONS = new HashMap<>();
    static {
        JOB_ACTIONS.put("miner",       List.of(QuestActionType.BREAK, QuestActionType.SMELT));
        JOB_ACTIONS.put("hunter",      List.of(QuestActionType.KILL));
        JOB_ACTIONS.put("farmer",      List.of(QuestActionType.FARM, QuestActionType.BREED));
        JOB_ACTIONS.put("fisherman",   List.of(QuestActionType.FISH));
        JOB_ACTIONS.put("woodcutter",  List.of(QuestActionType.BREAK));
        JOB_ACTIONS.put("builder",     List.of(QuestActionType.PLACE, QuestActionType.CRAFT));
        JOB_ACTIONS.put("crafter",     List.of(QuestActionType.CRAFT));
        JOB_ACTIONS.put("enchanter",   List.of(QuestActionType.ENCHANT));
        JOB_ACTIONS.put("brewer",      List.of(QuestActionType.BREW));
        JOB_ACTIONS.put("explorer",    List.of(QuestActionType.EXPLORE_CHUNK, QuestActionType.EXPLORE_DISTANCE));
        JOB_ACTIONS.put("digger",      List.of(QuestActionType.BREAK));
        JOB_ACTIONS.put("weaponsmith", List.of(QuestActionType.CRAFT, QuestActionType.SMELT));
    }

    // Material sub-pool tags per job (for BREAK filtering)
    private static final Map<String, List<String>> JOB_BREAK_POOLS = new HashMap<>();
    static {
        JOB_BREAK_POOLS.put("miner",      List.of("ores", "stone"));
        JOB_BREAK_POOLS.put("woodcutter",  List.of("logs"));
        JOB_BREAK_POOLS.put("digger",      List.of("dirt_sand"));
        JOB_BREAK_POOLS.put("builder",     List.of("all"));
        JOB_BREAK_POOLS.put("farmer",      List.of("crops"));
    }

    // All general actions available for non-job quests
    // REPAIR excluded — no reliable Bukkit event for anvil repair tracking
    private static final List<QuestActionType> GENERAL_ACTIONS = List.of(
        QuestActionType.BREAK, QuestActionType.KILL, QuestActionType.FISH,
        QuestActionType.CRAFT, QuestActionType.SMELT, QuestActionType.BREW,
        QuestActionType.ENCHANT, QuestActionType.FARM, QuestActionType.BREED,
        QuestActionType.EAT, QuestActionType.PLACE, QuestActionType.SHEAR,
        QuestActionType.TAME, QuestActionType.MILK
    );

    // Quest name fragments for procedural name generation
    private static final String[] NAME_PREFIXES = {
        "The", "A", "Daily", "Weekly", "Grand", "Simple", "Urgent", "Ancient",
        "Shadow", "Crystal", "Iron", "Golden", "Mystic", "Rustic", "Brave"
    };
    private static final Map<QuestActionType, String[]> NAME_NOUNS = new EnumMap<>(QuestActionType.class);
    static {
        NAME_NOUNS.put(QuestActionType.BREAK,   new String[]{"Excavation", "Mining Run", "Dig", "Quarry Job", "Deep Strike"});
        NAME_NOUNS.put(QuestActionType.PLACE,   new String[]{"Build", "Construction", "Foundation", "Blueprint"});
        NAME_NOUNS.put(QuestActionType.KILL,    new String[]{"Hunt", "Slaughter", "Bounty", "Extermination", "Purge"});
        NAME_NOUNS.put(QuestActionType.FISH,    new String[]{"Fishing Trip", "Catch", "Angler's Task", "Reel Deal"});
        NAME_NOUNS.put(QuestActionType.CRAFT,   new String[]{"Workshop", "Craft Order", "Assembly", "Creation"});
        NAME_NOUNS.put(QuestActionType.SMELT,   new String[]{"Smelting Run", "Furnace Job", "Refinery", "Forge Work"});
        NAME_NOUNS.put(QuestActionType.BREW,    new String[]{"Brew Batch", "Potion Order", "Alchemy", "Concoction"});
        NAME_NOUNS.put(QuestActionType.ENCHANT, new String[]{"Enchantment", "Arcane Task", "Imbuing", "Mystic Work"});
        NAME_NOUNS.put(QuestActionType.REPAIR,  new String[]{"Repair Job", "Restoration", "Mending"});
        NAME_NOUNS.put(QuestActionType.TAME,    new String[]{"Taming", "Beast Bond", "Companion Search"});
        NAME_NOUNS.put(QuestActionType.SHEAR,   new String[]{"Shearing", "Wool Harvest", "Clip Job"});
        NAME_NOUNS.put(QuestActionType.MILK,    new String[]{"Milking", "Dairy Run", "Bucket Fill"});
        NAME_NOUNS.put(QuestActionType.FARM,    new String[]{"Harvest", "Farm Run", "Crop Gathering", "Field Work"});
        NAME_NOUNS.put(QuestActionType.EAT,     new String[]{"Feast", "Tasting", "Food Challenge", "Meal Prep"});
        NAME_NOUNS.put(QuestActionType.BREED,   new String[]{"Breeding", "Ranch Work", "Animal Husbandry"});
        NAME_NOUNS.put(QuestActionType.EXPLORE_CHUNK, new String[]{"Expedition", "Exploration", "Survey", "Charting"});
        NAME_NOUNS.put(QuestActionType.EXPLORE_DISTANCE, new String[]{"Journey", "Trek", "March", "Voyage"});
        NAME_NOUNS.put(QuestActionType.TRADE_PLAYER, new String[]{"Trade Deal", "Exchange", "Barter"});
        NAME_NOUNS.put(QuestActionType.TRADE_AH, new String[]{"Market Sale", "Auction", "Commerce"});
    }

    private ProceduralQuestBuilder() {}

    /**
     * Build a single quest procedurally from a seed.
     */
    public static ActiveQuest build(UUID playerUuid, String periodKey, QuestCategory category,
                                     long seed, double tier, String jobId, QuestConfig config) {
        Random rng = new Random(seed);

        // Determine step count based on category
        int stepCount = pickStepCount(category, tier, rng);

        // Build steps
        List<ActiveQuest.ActiveStep> steps = new ArrayList<>();
        Set<QuestActionType> usedActions = new HashSet<>();
        double totalDifficultyScore = 0;

        for (int s = 0; s < stepCount; s++) {
            long stepSeed = SeedUtil.stepSeed(seed, s);
            Random stepRng = new Random(stepSeed);

            // Pick action type
            QuestActionType action = pickAction(category, jobId, usedActions, stepRng);
            if (action == null) break;

            // For multi-step, try to vary actions
            if (stepCount > 1) usedActions.add(action);

            // Pick target
            String target = pickTarget(action, jobId, category, tier, stepRng);

            // Calculate amount
            int amount = calculateAmount(action, target, tier, category, config, stepRng);

            // Generate description
            String description = generateDescription(action, target, amount, stepRng);

            // Track difficulty for reward calculation
            // Each step has a BASE contribution (so even small quests feel worthwhile)
            // plus a scaling component from target rarity * action difficulty * amount
            double targetDiff = target != null ? MaterialPools.getDifficulty(target) : 0.3;
            double actionDiff = ACTION_DIFFICULTY.getOrDefault(action, 0.3);
            double basePerStep = config.getRewardBasePerStep();
            totalDifficultyScore += basePerStep + (targetDiff * actionDiff * amount);

            steps.add(new ActiveQuest.ActiveStep(
                -1, s, action, target, amount, 0, false, description
            ));
        }

        if (steps.isEmpty()) return null;

        // Calculate rewards from total difficulty
        double rewardMultiplier = 1.0 + tier * config.getRewardScale();
        RewardDefinition rewards = calculateRewards(totalDifficultyScore, category, tier, rewardMultiplier, config, rng);

        // Generate quest name and synthetic ID
        QuestActionType primaryAction = steps.get(0).getActionType();
        String questName = generateQuestName(primaryAction, rng);
        String syntheticId = "proc_" + category.name().toLowerCase() + "_" + Long.toHexString(seed).substring(0, Math.min(8, Long.toHexString(seed).length()));

        return new ActiveQuest(
            -1, playerUuid, syntheticId, category, periodKey, seed,
            steps, rewards, 0, false, null, false, System.currentTimeMillis(),
            jobId, questName
        );
    }

    // ===== Step count =====

    private static int pickStepCount(QuestCategory category, double tier, Random rng) {
        return switch (category) {
            case JOB_DAILY -> {
                // Higher tiers get more steps: tier 0-3 = 1, tier 3-6 = 1-2, tier 6+ = 1-3
                if (tier >= 6.0) yield 1 + rng.nextInt(3);       // 1-3
                if (tier >= 3.0) yield 1 + rng.nextInt(2);       // 1-2
                yield 1;
            }
            case GENERAL_DAILY -> {
                // Scale with tier: tier 0-2 = 1, tier 2-5 = 1-2, tier 5+ = 1-3
                if (tier >= 5.0) yield 1 + rng.nextInt(3);       // 1-3
                if (tier >= 2.0) yield 1 + rng.nextInt(2);       // 1-2
                yield 1;
            }
            case WEEKLY -> 2 + rng.nextInt(2);                   // 2-3
            case CHALLENGE -> 2 + rng.nextInt(Math.max(1, (int)(tier / 3))); // 2-5 based on tier
        };
    }

    // ===== Action picking =====

    private static QuestActionType pickAction(QuestCategory category, String jobId,
                                               Set<QuestActionType> used, Random rng) {
        List<QuestActionType> candidates;
        if (category == QuestCategory.JOB_DAILY && jobId != null) {
            candidates = JOB_ACTIONS.getOrDefault(jobId.toLowerCase(), GENERAL_ACTIONS);
        } else {
            candidates = GENERAL_ACTIONS;
        }

        // Filter out already-used actions for variety, but fall back if needed
        List<QuestActionType> filtered = new ArrayList<>();
        for (QuestActionType a : candidates) {
            if (!used.contains(a) && !MaterialPools.getPoolForAction(a).isEmpty()) {
                filtered.add(a);
            }
        }
        if (filtered.isEmpty()) {
            // Fall back to all candidates
            for (QuestActionType a : candidates) {
                if (!MaterialPools.getPoolForAction(a).isEmpty() || !MaterialPools.needsTarget(a)) {
                    filtered.add(a);
                }
            }
        }
        if (filtered.isEmpty()) return QuestActionType.BREAK; // absolute fallback
        return filtered.get(rng.nextInt(filtered.size()));
    }

    // ===== Target picking =====

    private static String pickTarget(QuestActionType action, String jobId,
                                      QuestCategory category, double tier, Random rng) {
        if (!MaterialPools.needsTarget(action)) return null;

        List<String> pool = getFilteredPool(action, jobId);
        if (pool.isEmpty()) return null;

        if (category == QuestCategory.CHALLENGE) {
            // Bias toward harder targets for challenges
            return pickBiasedTarget(pool, true, rng);
        }

        // Filter pool by tier — low-tier players shouldn't get rare targets
        // Max allowed difficulty scales with tier: tier 0 = 0.3, tier 5 = 0.65, tier 10 = 1.0
        double maxDifficulty = Math.min(1.0, 0.3 + tier * 0.07);
        List<String> tierPool = new ArrayList<>();
        for (String target : pool) {
            if (MaterialPools.getDifficulty(target) <= maxDifficulty) {
                tierPool.add(target);
            }
        }
        // Fall back to full pool if filtering removed everything
        if (tierPool.isEmpty()) tierPool = pool;

        // Bias toward easier targets (weighted random)
        return pickBiasedTarget(tierPool, false, rng);
    }

    /**
     * Get the appropriate target pool, filtered by job if applicable.
     */
    private static List<String> getFilteredPool(QuestActionType action, String jobId) {
        if (action == QuestActionType.BREAK && jobId != null) {
            List<String> tags = JOB_BREAK_POOLS.getOrDefault(jobId.toLowerCase(), List.of("all"));
            List<String> pool = new ArrayList<>();
            for (String tag : tags) {
                switch (tag) {
                    case "ores" -> pool.addAll(MaterialPools.ORES);
                    case "stone" -> pool.addAll(MaterialPools.STONE_TYPES);
                    case "logs" -> pool.addAll(MaterialPools.LOGS);
                    case "dirt_sand" -> pool.addAll(MaterialPools.DIRT_SAND);
                    case "crops" -> pool.addAll(MaterialPools.CROPS);
                    case "all" -> pool.addAll(MaterialPools.ALL_MINEABLE);
                }
            }
            return pool.isEmpty() ? MaterialPools.ALL_MINEABLE : pool;
        }

        if (action == QuestActionType.CRAFT && jobId != null) {
            String job = jobId.toLowerCase();
            return switch (job) {
                case "weaponsmith" -> MaterialPools.CRAFTABLE_WEAPONS;
                case "builder" -> MaterialPools.CRAFTABLE_BUILDING;
                default -> MaterialPools.ALL_CRAFTABLE;
            };
        }

        return MaterialPools.getPoolForAction(action);
    }

    /**
     * Pick a target biased toward harder (high difficulty) entries.
     */
    private static String pickBiasedTarget(List<String> pool, boolean biasHard, Random rng) {
        if (pool.size() <= 1) return pool.isEmpty() ? null : pool.get(0);

        // Weight by difficulty
        double totalWeight = 0;
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            double diff = MaterialPools.getDifficulty(pool.get(i));
            weights[i] = biasHard ? (diff + 0.1) : (1.1 - diff);
            totalWeight += weights[i];
        }

        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < pool.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    // ===== Amount calculation =====

    private static int calculateAmount(QuestActionType action, String target, double tier,
                                        QuestCategory category, QuestConfig config, Random rng) {
        int[] baseRange = BASE_AMOUNTS.getOrDefault(action, new int[]{5, 50});
        double targetDifficulty = target != null ? MaterialPools.getDifficulty(target) : 0.3;

        // Base amount: random within range, aggressively scaled by target difficulty (cubic curve)
        // Easy targets (0.05): scale ~0.86 → near full amount (stone: 2-6 stacks)
        // Medium targets (0.3): scale ~0.34 → big reduction (iron ore: ~1-2 stacks)
        // Hard targets (0.7): scale ~0.03 → tiny amounts (diamond ore: 3-10)
        // Very rare (0.9+): scale ~0.001 → minimal (ancient debris: 1-2)
        double difficultyScale = Math.pow(1.0 - targetDifficulty, 3.0); // cubic curve for aggressive rarity scaling
        difficultyScale = Math.max(0.05, difficultyScale); // floor at 5% of base
        int rawBase = baseRange[0] + rng.nextInt(Math.max(1, baseRange[1] - baseRange[0]));
        int base = Math.max(1, (int)(rawBase * difficultyScale));

        // Scale by tier
        int scaled = TargetScaler.scaleAmount(base, tier, config.getDifficultyScale(), 1, base * 5);

        // Category multiplier
        scaled = switch (category) {
            case JOB_DAILY, GENERAL_DAILY -> scaled;
            case WEEKLY -> (int)(scaled * 2.5);
            case CHALLENGE -> (int)(scaled * 4.0);
        };

        // Round to "nice" numbers
        scaled = roundToNice(scaled, action);

        return Math.max(1, scaled);
    }

    private static int roundToNice(int amount, QuestActionType action) {
        if (amount <= 3) return amount;
        return switch (action) {
            case BREAK, PLACE, SMELT, FARM -> roundTo(amount, amount > 32 ? 16 : 4);
            case KILL -> roundTo(amount, amount > 20 ? 5 : 1);
            case CRAFT, FISH, EAT -> roundTo(amount, amount > 16 ? 4 : 1);
            case BREW, ENCHANT, TAME, REPAIR -> amount;
            case EXPLORE_DISTANCE -> roundTo(amount, 100);
            case EXPLORE_CHUNK -> roundTo(amount, 5);
            default -> roundTo(amount, 5);
        };
    }

    private static int roundTo(int amount, int multiple) {
        if (multiple <= 1) return amount;
        return Math.max(multiple, ((amount + multiple / 2) / multiple) * multiple);
    }

    // ===== Description generation =====

    private static String generateDescription(QuestActionType action, String target, int amount, Random rng) {
        String[] verbs = VERBS.getOrDefault(action, new String[]{action.name().toLowerCase()});
        String verb = verbs[rng.nextInt(verbs.length)];

        if (target == null) {
            // No target: "Brew 6 potions", "Repair 3 items"
            String noun = switch (action) {
                case BREW -> "potions";
                case REPAIR -> "items";
                case EXPLORE_CHUNK -> "new chunks";
                case EXPLORE_DISTANCE -> "blocks";
                case TRADE_PLAYER -> "times";
                case TRADE_AH -> "items";
                default -> "items";
            };
            return verb + " " + amount + " " + noun;
        }

        String prettyTarget = prettify(target);
        return verb + " " + amount + " " + prettyTarget;
    }

    /**
     * Convert "DEEPSLATE_IRON_ORE" to "Deepslate Iron Ore"
     */
    static String prettify(String name) {
        if (name == null) return "items";
        String[] words = name.toLowerCase().replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(" ");
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    // ===== Quest name generation =====

    private static String generateQuestName(QuestActionType primaryAction, Random rng) {
        String prefix = NAME_PREFIXES[rng.nextInt(NAME_PREFIXES.length)];
        String[] nouns = NAME_NOUNS.getOrDefault(primaryAction, new String[]{"Task"});
        String noun = nouns[rng.nextInt(nouns.length)];
        return prefix + " " + noun;
    }

    // ===== Reward calculation =====

    private static RewardDefinition calculateRewards(double difficultyScore, QuestCategory category,
                                                      double tier, double rewardMultiplier, QuestConfig config, Random rng) {
        // Reward values from config (tunable per-server)
        double moneyPerDiff = config.getRewardMoneyPerDifficulty();
        double jobXpPerDiff = config.getRewardJobXpPerDifficulty();

        double money = difficultyScore * moneyPerDiff * rewardMultiplier;
        double jobXp = difficultyScore * jobXpPerDiff * rewardMultiplier;

        // Minimum floor so no quest ever feels worthless
        money = Math.max(money, 75.0 * rewardMultiplier);
        jobXp = Math.max(jobXp, 50.0 * rewardMultiplier);

        // Category bonuses
        money *= switch (category) {
            case JOB_DAILY, GENERAL_DAILY -> 1.0;
            case WEEKLY -> 2.5;
            case CHALLENGE -> 4.0;
        };
        jobXp *= switch (category) {
            case JOB_DAILY, GENERAL_DAILY -> 1.0;
            case WEEKLY -> 2.0;
            case CHALLENGE -> 3.0;
        };

        // XP levels: always at least 1, scales with difficulty (nerfed 35%)
        int xpLevels = Math.max(1, (int)(difficultyScore * 0.52 * rewardMultiplier));
        xpLevels *= switch (category) {
            case JOB_DAILY, GENERAL_DAILY -> 1;
            case WEEKLY -> 2;
            case CHALLENGE -> 3;
        };
        xpLevels = Math.min(xpLevels, 30);

        // Gems: only for challenges or high-tier weekly
        int gems = 0;
        if (category == QuestCategory.CHALLENGE) {
            gems = Math.max(1, (int)(difficultyScore / 50.0 * rewardMultiplier));
        } else if (category == QuestCategory.WEEKLY && tier >= 4.0) {
            gems = rng.nextDouble() < 0.4 ? 1 : 0;
        }

        // Item rewards — chance-based, better items for harder quests
        List<RewardDefinition.ItemReward> items = generateItemRewards(difficultyScore, category, tier, rng);

        return new RewardDefinition(money, jobXp, xpLevels, gems, items);
    }

    /**
     * Generate random item rewards based on quest difficulty and category.
     * Daily quests: ~40% chance of 1 item reward (common items)
     * Weekly quests: ~70% chance of 1-2 item rewards (better items)
     * Challenge quests: ~90% chance of 2-3 item rewards (rare items)
     */
    private static List<RewardDefinition.ItemReward> generateItemRewards(
            double difficultyScore, QuestCategory category, double tier, Random rng) {

        List<RewardDefinition.ItemReward> items = new ArrayList<>();

        // Chance and count based on category
        double chance;
        int maxItems;
        switch (category) {
            case JOB_DAILY -> { chance = 0.35; maxItems = 1; }
            case GENERAL_DAILY -> { chance = 0.40; maxItems = 1; }
            case WEEKLY -> { chance = 0.70; maxItems = 2; }
            case CHALLENGE -> { chance = 0.90; maxItems = 3; }
            default -> { chance = 0.3; maxItems = 1; }
        }

        // Tier increases chance slightly
        chance = Math.min(1.0, chance + tier * 0.02);

        if (rng.nextDouble() > chance) return items;

        int itemCount = 1 + rng.nextInt(maxItems);

        // Item pools by tier/difficulty bracket
        Material[][] commonItems = {
                {Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN,
                 Material.ARROW, Material.TORCH, Material.OAK_PLANKS, Material.COAL,
                 Material.IRON_INGOT, Material.BONE_MEAL, Material.LEATHER, Material.STRING},
        };
        Material[][] midItems = {
                {Material.IRON_INGOT, Material.GOLD_INGOT, Material.LAPIS_LAZULI, Material.REDSTONE,
                 Material.EXPERIENCE_BOTTLE, Material.GOLDEN_APPLE, Material.BOOK,
                 Material.EMERALD, Material.ENDER_PEARL, Material.BLAZE_ROD,
                 Material.NAME_TAG, Material.SADDLE, Material.HONEY_BOTTLE},
        };
        Material[][] rareItems = {
                {Material.DIAMOND, Material.EMERALD, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
                 Material.NETHERITE_SCRAP, Material.TOTEM_OF_UNDYING, Material.ELYTRA,
                 Material.HEART_OF_THE_SEA, Material.NETHER_STAR, Material.SHULKER_SHELL,
                 Material.TRIDENT, Material.WITHER_SKELETON_SKULL},
        };

        for (int i = 0; i < itemCount; i++) {
            Material[] pool;
            int amount;

            if (tier >= 7.0 && category == QuestCategory.CHALLENGE && rng.nextDouble() < 0.3) {
                // Rare items for high-tier challenges
                pool = rareItems[0];
                amount = 1 + rng.nextInt(2);
            } else if (tier >= 3.0 || category == QuestCategory.WEEKLY) {
                // Mid-tier items
                pool = midItems[0];
                amount = 1 + rng.nextInt(Math.max(1, (int)(tier * 0.5)));
                amount = Math.min(amount, 16);
            } else {
                // Common items for low tier
                pool = commonItems[0];
                amount = 2 + rng.nextInt(Math.max(1, 4 + (int)(tier)));
                amount = Math.min(amount, 32);
            }

            Material chosen = pool[rng.nextInt(pool.length)];

            // Don't duplicate items already in the list
            boolean duplicate = items.stream().anyMatch(ir -> ir.material() == chosen);
            if (duplicate) continue;

            items.add(new RewardDefinition.ItemReward(chosen, amount));
        }

        return items;
    }
}
