package com.blockforge.horizonutilities.wither;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WitherBossConfig {

    private final HorizonUtilitiesPlugin plugin;
    private FileConfiguration config;

    // Stats
    private double health = 2048;
    private int armor = 12;
    private double regenPerSecond = 2.0;
    private double regenBelowHalf = 5.0;
    private boolean explosionResistant = true;
    private boolean witherImmune = true;
    private double perPlayerHealthBonus = 256;

    // Phases
    private String phase1Name = "The Awakening";
    private double phase1Threshold = 0.75;
    private String phase2Name = "Army of the Dead";
    private double phase2Threshold = 0.50;
    private String phase3Name = "Corruption Unleashed";
    private double phase3Threshold = 0.25;
    private String phase4Name = "Death's Last Stand";

    // AI
    private int targetSwitchMin = 5;
    private int targetSwitchMax = 10;
    private int altitudeMin = 8;
    private int altitudeMax = 15;
    private double strafeSpeed = 0.6;
    private double chargeSpeed = 1.8;
    private int enrageTimerSeconds = 600;

    // Attack speed per phase
    private double[] phaseAttackSpeed = {1.0, 1.3, 1.6, 2.0};
    private double globalCooldown = 1.5;

    // Vanilla
    private boolean allowVanillaWither = true;

    // Summoning
    private boolean summoningEnabled = true;
    private boolean bypassGriefProtection = true;
    private int cinematicDurationTicks = 100;

    // Music
    private boolean musicEnabled = true;
    private String musicSoundKey = "horizonsmp:boss.wither_sovereign";
    private int musicRadius = 64;
    private String victorySoundKey = "horizonsmp:boss.wither_sovereign_victory";

    // Drops
    private List<LootEntry> guaranteedDrops = new ArrayList<>();
    private List<LootEntry> randomDrops = new ArrayList<>();
    private int dropXp = 5000;
    private double dropMoney = 10000.0;
    private boolean moneyPerPlayer = true;
    private boolean scaleWithPlayers = true;

    // Block restore
    private boolean blockRestoreEnabled = true;
    private int blockRestoreTimeout = 300;
    private int maxRadius = 64;

    public WitherBossConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "wither-boss.yml");
        if (!file.exists()) {
            plugin.saveResource("wither-boss.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Stats
        health = config.getDouble("stats.health", health);
        armor = config.getInt("stats.armor", armor);
        regenPerSecond = config.getDouble("stats.regeneration-per-second", regenPerSecond);
        regenBelowHalf = config.getDouble("stats.regeneration-below-half", regenBelowHalf);
        explosionResistant = config.getBoolean("stats.explosion-resistant", explosionResistant);
        witherImmune = config.getBoolean("stats.wither-immune", witherImmune);
        perPlayerHealthBonus = config.getDouble("stats.per-player-health-bonus", perPlayerHealthBonus);

        // Phases
        phase1Name = config.getString("phases.phase1-name", phase1Name);
        phase1Threshold = config.getDouble("phases.phase1-threshold", phase1Threshold);
        phase2Name = config.getString("phases.phase2-name", phase2Name);
        phase2Threshold = config.getDouble("phases.phase2-threshold", phase2Threshold);
        phase3Name = config.getString("phases.phase3-name", phase3Name);
        phase3Threshold = config.getDouble("phases.phase3-threshold", phase3Threshold);
        phase4Name = config.getString("phases.phase4-name", phase4Name);

        // AI
        targetSwitchMin = config.getInt("ai.target-switch-min-seconds", targetSwitchMin);
        targetSwitchMax = config.getInt("ai.target-switch-max-seconds", targetSwitchMax);
        altitudeMin = config.getInt("ai.preferred-altitude-min", altitudeMin);
        altitudeMax = config.getInt("ai.preferred-altitude-max", altitudeMax);
        strafeSpeed = config.getDouble("ai.strafe-speed", strafeSpeed);
        chargeSpeed = config.getDouble("ai.charge-speed", chargeSpeed);
        enrageTimerSeconds = config.getInt("ai.enrage-timer-seconds", enrageTimerSeconds);

        // Attack speed
        phaseAttackSpeed[0] = config.getDouble("attack-speed.phase1", 1.0);
        phaseAttackSpeed[1] = config.getDouble("attack-speed.phase2", 1.3);
        phaseAttackSpeed[2] = config.getDouble("attack-speed.phase3", 1.6);
        phaseAttackSpeed[3] = config.getDouble("attack-speed.phase4", 2.0);
        globalCooldown = config.getDouble("global-cooldown", globalCooldown);

        // Vanilla
        allowVanillaWither = config.getBoolean("allow-vanilla-wither", allowVanillaWither);

        // Summoning
        summoningEnabled = config.getBoolean("summoning.enabled", summoningEnabled);
        bypassGriefProtection = config.getBoolean("summoning.bypass-grief-protection", bypassGriefProtection);
        cinematicDurationTicks = config.getInt("summoning.cinematic-duration-ticks", cinematicDurationTicks);

        // Music
        musicEnabled = config.getBoolean("music.enabled", musicEnabled);
        musicSoundKey = config.getString("music.sound-key", musicSoundKey);
        musicRadius = config.getInt("music.radius", musicRadius);
        victorySoundKey = config.getString("music.victory-sound", victorySoundKey);

        // Drops
        guaranteedDrops.clear();
        randomDrops.clear();
        loadDrops("drops.guaranteed", guaranteedDrops, false);
        loadDrops("drops.random", randomDrops, true);
        dropXp = config.getInt("drops.xp", dropXp);
        dropMoney = config.getDouble("drops.money", dropMoney);
        moneyPerPlayer = config.getBoolean("drops.money-per-player", moneyPerPlayer);
        scaleWithPlayers = config.getBoolean("drops.scale-with-players", scaleWithPlayers);

        // Block restore
        blockRestoreEnabled = config.getBoolean("block-restore.enabled", blockRestoreEnabled);
        blockRestoreTimeout = config.getInt("block-restore.timeout-seconds", blockRestoreTimeout);
        maxRadius = config.getInt("block-restore.max-radius", maxRadius);
    }

    private void loadDrops(String path, List<LootEntry> list, boolean hasChance) {
        var section = config.getMapList(path);
        for (var map : section) {
            String matName = (String) map.getOrDefault("material", null);
            String ceItem = (String) map.getOrDefault("craftengine-item", null);
            int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
            int amountMin = map.containsKey("amount-min") ? ((Number) map.get("amount-min")).intValue() : amount;
            int amountMax = map.containsKey("amount-max") ? ((Number) map.get("amount-max")).intValue() : amount;
            double chance = hasChance && map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 1.0;

            Material mat = null;
            if (matName != null) {
                try { mat = Material.valueOf(matName); } catch (IllegalArgumentException ignored) {}
            }
            list.add(new LootEntry(mat, ceItem, amountMin, amountMax, chance));
        }
    }

    // Getters
    public double getHealth() { return health; }
    public int getArmor() { return armor; }
    public double getRegenPerSecond() { return regenPerSecond; }
    public double getRegenBelowHalf() { return regenBelowHalf; }
    public boolean isExplosionResistant() { return explosionResistant; }
    public boolean isWitherImmune() { return witherImmune; }
    public double getPerPlayerHealthBonus() { return perPlayerHealthBonus; }

    public String getPhaseName(int phase) {
        return switch (phase) {
            case 1 -> phase1Name;
            case 2 -> phase2Name;
            case 3 -> phase3Name;
            case 4 -> phase4Name;
            default -> "Unknown";
        };
    }

    public double getPhaseThreshold(int phase) {
        return switch (phase) {
            case 1 -> phase1Threshold;
            case 2 -> phase2Threshold;
            case 3 -> phase3Threshold;
            default -> 0.0;
        };
    }

    public int getTargetSwitchMin() { return targetSwitchMin; }
    public int getTargetSwitchMax() { return targetSwitchMax; }
    public int getAltitudeMin() { return altitudeMin; }
    public int getAltitudeMax() { return altitudeMax; }
    public double getStrafeSpeed() { return strafeSpeed; }
    public double getChargeSpeed() { return chargeSpeed; }
    public int getEnrageTimerSeconds() { return enrageTimerSeconds; }

    public double getPhaseAttackSpeed(int phase) {
        int idx = Math.max(0, Math.min(3, phase - 1));
        return phaseAttackSpeed[idx];
    }

    public double getGlobalCooldown() { return globalCooldown; }
    public boolean isAllowVanillaWither() { return allowVanillaWither; }

    public boolean isSummoningEnabled() { return summoningEnabled; }
    public boolean isBypassGriefProtection() { return bypassGriefProtection; }
    public int getCinematicDurationTicks() { return cinematicDurationTicks; }

    public boolean isMusicEnabled() { return musicEnabled; }
    public String getMusicSoundKey() { return musicSoundKey; }
    public int getMusicRadius() { return musicRadius; }
    public String getVictorySoundKey() { return victorySoundKey; }

    public List<LootEntry> getGuaranteedDrops() { return guaranteedDrops; }
    public List<LootEntry> getRandomDrops() { return randomDrops; }
    public int getDropXp() { return dropXp; }
    public double getDropMoney() { return dropMoney; }
    public boolean isMoneyPerPlayer() { return moneyPerPlayer; }
    public boolean isScaleWithPlayers() { return scaleWithPlayers; }

    public boolean isBlockRestoreEnabled() { return blockRestoreEnabled; }
    public int getBlockRestoreTimeout() { return blockRestoreTimeout; }
    public int getMaxRadius() { return maxRadius; }

    public record LootEntry(Material material, String craftEngineItem, int amountMin, int amountMax, double chance) {
        public boolean isCraftEngine() { return craftEngineItem != null; }
    }
}
