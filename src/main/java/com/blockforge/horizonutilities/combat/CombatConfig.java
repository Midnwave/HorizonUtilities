package com.blockforge.horizonutilities.combat;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class CombatConfig {

    private final HorizonUtilitiesPlugin plugin;

    private boolean enabled;
    private int combatTimerSeconds;
    private boolean blockTeleport;
    private boolean blockEnderchest;
    private boolean blockFly;
    private List<String> blockedCommands;
    private boolean killOnLogout;
    private boolean dropItems;
    private boolean dropXp;
    private boolean anvilCapEnabled;
    private int anvilMaxRepairCost;

    public CombatConfig(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "combat.yml");
        if (!file.exists()) plugin.saveResource("combat.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        enabled             = cfg.getBoolean("enabled", true);
        combatTimerSeconds  = cfg.getInt("combat-timer-seconds", 15);
        blockTeleport       = cfg.getBoolean("restrictions.block-teleport", true);
        blockEnderchest     = cfg.getBoolean("restrictions.block-enderchest", true);
        blockFly            = cfg.getBoolean("restrictions.block-fly", true);
        blockedCommands     = cfg.getStringList("restrictions.block-commands")
                .stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        killOnLogout        = cfg.getBoolean("combat-log.kill-on-logout", true);
        dropItems           = cfg.getBoolean("combat-log.drop-items", true);
        dropXp              = cfg.getBoolean("combat-log.drop-xp", false);
        anvilCapEnabled     = cfg.getBoolean("anvil.enabled", true);
        anvilMaxRepairCost  = cfg.getInt("anvil.max-repair-cost", 200);
    }

    public boolean isEnabled()              { return enabled; }
    public int getCombatTimerSeconds()      { return combatTimerSeconds; }
    public boolean isBlockTeleport()        { return blockTeleport; }
    public boolean isBlockEnderchest()      { return blockEnderchest; }
    public boolean isBlockFly()             { return blockFly; }
    public List<String> getBlockedCommands(){ return blockedCommands; }
    public boolean isKillOnLogout()         { return killOnLogout; }
    public boolean isDropItems()            { return dropItems; }
    public boolean isDropXp()               { return dropXp; }
    public boolean isAnvilCapEnabled()      { return anvilCapEnabled; }
    public int getAnvilMaxRepairCost()      { return anvilMaxRepairCost; }
}
