package com.blockforge.horizonutilities.games.content;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class WordList {

    public static final List<String> MOBS = List.of(
            "zombie", "skeleton", "creeper", "spider", "enderman", "blaze", "ghast",
            "witch", "slime", "magma cube", "wither skeleton", "guardian", "elder guardian",
            "shulker", "phantom", "drowned", "pillager", "ravager", "vindicator", "evoker",
            "vex", "hoglin", "piglin", "zoglin", "strider", "warden", "allay", "sniffer",
            "camel", "iron golem", "snow golem", "bee", "fox", "panda", "dolphin",
            "axolotl", "glow squid", "frog", "tadpole", "goat", "wolf", "cat", "parrot",
            "breeze", "bogged", "creaking"
    );

    public static final List<String> ITEMS = List.of(
            "diamond sword", "iron pickaxe", "golden apple", "enchanted book",
            "ender pearl", "blaze rod", "nether star", "elytra", "trident",
            "crossbow", "shield", "totem of undying", "shulker box", "spyglass",
            "recovery compass", "echo shard", "disc fragment", "netherite ingot",
            "amethyst shard", "copper ingot", "goat horn", "brush",
            "mace", "wind charge", "breeze rod", "trial key", "ominous trial key",
            "heavy core", "ominous bottle", "bundle", "resin clump"
    );

    public static final List<String> BLOCKS = List.of(
            "diamond ore", "emerald ore", "obsidian", "bedrock", "netherrack",
            "end stone", "purpur block", "prismarine", "sea lantern", "glowstone",
            "redstone lamp", "observer", "dropper", "dispenser", "hopper",
            "sculk sensor", "sculk shrieker", "sculk catalyst", "reinforced deepslate",
            "suspicious sand", "decorated pot", "calibrated sculk sensor",
            "crafter", "trial spawner", "vault", "copper bulb", "copper grate",
            "copper door", "copper trapdoor", "chiseled copper", "tuff bricks",
            "polished tuff", "creaking heart", "eyeblossom", "pale oak log",
            "pale oak planks", "pale moss block", "pale hanging moss",
            "resin block", "resin bricks", "chiseled resin bricks"
    );

    public static final List<String> ENCHANTMENTS = List.of(
            "sharpness", "smite", "bane of arthropods", "fire aspect", "looting",
            "sweeping edge", "unbreaking", "efficiency", "fortune", "silk touch",
            "protection", "fire protection", "blast protection", "projectile protection",
            "thorns", "respiration", "aqua affinity", "depth strider", "frost walker",
            "feather falling", "power", "flame", "infinity", "punch",
            "luck of the sea", "lure", "mending", "swift sneak", "soul speed",
            "density", "breach", "wind burst"
    );

    public static final List<String> BIOMES = List.of(
            "plains", "forest", "desert", "taiga", "savanna", "jungle",
            "swamp", "mushroom fields", "badlands", "dark forest", "birch forest",
            "ocean", "deep ocean", "frozen ocean", "warm ocean", "beach",
            "snowy plains", "ice spikes", "meadow", "grove", "cherry grove",
            "stony peaks", "frozen peaks", "jagged peaks", "deep dark",
            "lush caves", "dripstone caves", "nether wastes", "soul sand valley",
            "crimson forest", "warped forest", "basalt deltas", "the end",
            "end highlands", "end midlands", "pale garden"
    );

    public static String randomWord() {
        List<List<String>> all = List.of(MOBS, ITEMS, BLOCKS, ENCHANTMENTS, BIOMES);
        List<String> chosen = all.get(ThreadLocalRandom.current().nextInt(all.size()));
        return chosen.get(ThreadLocalRandom.current().nextInt(chosen.size()));
    }

    public static String randomFrom(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
