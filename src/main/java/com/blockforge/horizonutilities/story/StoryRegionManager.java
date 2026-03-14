package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cuboid protection regions for story structures.
 * Prevents block break/place within regions unless admin bypass is active.
 */
public class StoryRegionManager implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final Set<UUID> bypassing = ConcurrentHashMap.newKeySet();

    private File regionsFile;
    private FileConfiguration regionsConfig;

    public StoryRegionManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        loadRegions();
    }

    private void loadRegions() {
        regionsFile = new File(plugin.getDataFolder(), "story-regions.yml");
        if (!regionsFile.exists()) {
            try { regionsFile.createNewFile(); } catch (IOException ignored) {}
        }
        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);

        regions.clear();
        ConfigurationSection section = regionsConfig.getConfigurationSection("regions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection rs = section.getConfigurationSection(key);
                if (rs != null) {
                    String world = rs.getString("world", "world");
                    regions.put(key, new Region(
                        world,
                        rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"),
                        rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2")
                    ));
                }
            }
        }
        plugin.getLogger().info("[Story] Loaded " + regions.size() + " protection region(s).");
    }

    public void saveRegion(String stageKey, Location pos1, Location pos2) {
        String world = pos1.getWorld() != null ? pos1.getWorld().getName() : "world";
        int x1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int y1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int z1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int x2 = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y2 = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int z2 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        regions.put(stageKey, new Region(world, x1, y1, z1, x2, y2, z2));

        String path = "regions." + stageKey;
        regionsConfig.set(path + ".world", world);
        regionsConfig.set(path + ".x1", x1);
        regionsConfig.set(path + ".y1", y1);
        regionsConfig.set(path + ".z1", z1);
        regionsConfig.set(path + ".x2", x2);
        regionsConfig.set(path + ".y2", y2);
        regionsConfig.set(path + ".z2", z2);

        try {
            regionsConfig.save(regionsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Story] Failed to save regions: " + e.getMessage());
        }
    }

    /**
     * Grants a player 30-second bypass for region protection.
     */
    public void grantBypass(Player player) {
        bypassing.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            bypassing.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.sendMessage(Component.text("[Chronicle] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Region bypass expired.", NamedTextColor.YELLOW)));
            }
        }, 600L); // 30 seconds
    }

    public boolean isInRegion(Location loc) {
        if (loc.getWorld() == null) return false;
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        for (Region r : regions.values()) {
            if (r.world.equals(worldName) && r.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    // --- Events ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (bypassing.contains(event.getPlayer().getUniqueId())) return;
        if (event.getPlayer().hasPermission("horizonutilities.story.admin") && event.getPlayer().isSneaking()) return;

        if (isInRegion(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("[Chronicle] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("This area is protected by the chronicle.", NamedTextColor.RED)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (bypassing.contains(event.getPlayer().getUniqueId())) return;
        if (event.getPlayer().hasPermission("horizonutilities.story.admin") && event.getPlayer().isSneaking()) return;

        if (isInRegion(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("[Chronicle] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("This area is protected by the chronicle.", NamedTextColor.RED)));
        }
    }

    private record Region(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        boolean contains(int x, int y, int z) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }
}
