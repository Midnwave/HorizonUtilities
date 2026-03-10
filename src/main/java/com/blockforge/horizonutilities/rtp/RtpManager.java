package com.blockforge.horizonutilities.rtp;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.config.MessagesManager;
import com.blockforge.horizonutilities.quests.QuestStorage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles random teleportation logic including safe location finding,
 * warmup, cooldown, and cost.
 */
public class RtpManager {

    private final HorizonUtilitiesPlugin plugin;
    private final RtpConfig config;
    private final QuestStorage storage;
    private final MessagesManager msg;

    /** Players currently in warmup */
    private final Map<UUID, Integer> warmupTasks = new ConcurrentHashMap<>();

    public RtpManager(HorizonUtilitiesPlugin plugin, RtpConfig config, QuestStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.msg = plugin.getMessagesManager();
    }

    /**
     * Initiate RTP for a player.
     */
    public void teleport(Player player, World world) {
        UUID uuid = player.getUniqueId();
        String worldName = world.getName();

        if (!config.isWorldEnabled(worldName)) {
            msg.send(player, "rtp-disabled-world");
            return;
        }

        long lastUsed = storage.getRtpCooldown(uuid);
        long cooldownMs = config.getCooldownSeconds() * 1000L;
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed < cooldownMs) {
            long remaining = (cooldownMs - elapsed) / 1000;
            msg.send(player, "rtp-cooldown", Placeholder.parsed("seconds", String.valueOf(remaining)));
            return;
        }

        if (warmupTasks.containsKey(uuid)) {
            msg.send(player, "rtp-already-teleporting");
            return;
        }

        double cost = config.getCost(worldName);
        if (cost > 0 && plugin.getVaultHook().isAvailable()) {
            if (!plugin.getVaultHook().has(player, cost)) {
                msg.send(player, "rtp-insufficient-funds",
                    Placeholder.parsed("cost", plugin.getVaultHook().format(cost)));
                return;
            }
        }

        msg.send(player, "rtp-searching");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location safeLoc = findSafeLocation(world, worldName);

            if (safeLoc == null) {
                Bukkit.getScheduler().runTask(plugin, () -> msg.send(player, "rtp-search-failed"));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> startWarmup(player, safeLoc, cost));
        });
    }

    private void startWarmup(Player player, Location destination, double cost) {
        UUID uuid = player.getUniqueId();
        int warmup = config.getWarmupSeconds();

        if (warmup <= 0) {
            executeTeleport(player, destination, cost);
            return;
        }

        Location startLoc = player.getLocation().clone();

        msg.send(player, "rtp-warmup-start", Placeholder.parsed("seconds", String.valueOf(warmup)));

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int countdown = warmup;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelWarmup(uuid);
                    return;
                }

                Location current = player.getLocation();
                if (current.getBlockX() != startLoc.getBlockX() ||
                    current.getBlockY() != startLoc.getBlockY() ||
                    current.getBlockZ() != startLoc.getBlockZ()) {
                    msg.send(player, "rtp-cancelled-move");
                    cancelWarmup(uuid);
                    return;
                }

                if (config.isParticles()) {
                    player.getWorld().spawnParticle(Particle.PORTAL,
                        player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                }

                countdown--;
                if (countdown <= 0) {
                    cancelWarmup(uuid);
                    executeTeleport(player, destination, cost);
                } else if (countdown <= 3) {
                    msg.send(player, "rtp-warmup-countdown",
                        Placeholder.parsed("seconds", String.valueOf(countdown)));
                }
            }
        }, 20L, 20L).getTaskId();

        warmupTasks.put(uuid, taskId);
    }

    private void executeTeleport(Player player, Location destination, double cost) {
        UUID uuid = player.getUniqueId();

        if (cost > 0 && plugin.getVaultHook().isAvailable()) {
            if (!plugin.getVaultHook().withdraw(player, cost)) {
                msg.send(player, "rtp-insufficient-funds",
                    Placeholder.parsed("cost", plugin.getVaultHook().format(cost)));
                return;
            }
        }

        player.teleport(destination);
        storage.saveRtpCooldown(uuid, System.currentTimeMillis());

        msg.send(player, "rtp-success",
            Placeholder.parsed("x", String.valueOf(destination.getBlockX())),
            Placeholder.parsed("y", String.valueOf(destination.getBlockY())),
            Placeholder.parsed("z", String.valueOf(destination.getBlockZ())));

        player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        if (config.isParticles()) {
            player.getWorld().spawnParticle(Particle.PORTAL, destination, 50, 1, 1, 1, 0.5);
        }
    }

    public void cancelWarmup(UUID uuid) {
        Integer taskId = warmupTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    public boolean isInWarmup(UUID uuid) {
        return warmupTasks.containsKey(uuid);
    }

    private Location findSafeLocation(World world, String worldName) {
        int minRange = config.getMinRange(worldName);
        int maxRange = config.getMaxRange(worldName);
        int maxAttempts = config.getMaxAttempts();
        Random rng = new Random();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int distance = minRange + rng.nextInt(maxRange - minRange + 1);
            double angle = rng.nextDouble() * 2 * Math.PI;
            int x = (int) (distance * Math.cos(angle));
            int z = (int) (distance * Math.sin(angle));

            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                world.getChunkAt(x >> 4, z >> 4);
            }

            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight()) continue;

            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            Block ground = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);

            if (config.getBiomeBlacklist().contains(world.getBiome(x, y, z))) continue;
            if (config.getUnsafeBlocks().contains(ground.getType())) continue;
            if (ground.getType() == Material.AIR || ground.getType() == Material.VOID_AIR) continue;
            if (!feet.getType().isAir() && !feet.isLiquid()) continue;
            if (!head.getType().isAir() && !head.isLiquid()) continue;
            if (feet.isLiquid()) continue;

            return loc;
        }

        return null;
    }

    public void firstJoinRtp(Player player) {
        World world = Bukkit.getWorld(config.getFirstJoinWorld());
        if (world == null) world = Bukkit.getWorlds().get(0);

        World finalWorld = world;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location safeLoc = findSafeLocation(finalWorld, finalWorld.getName());
            if (safeLoc != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.teleport(safeLoc);
                    msg.send(player, "rtp-first-join");
                    player.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                });
            }
        });
    }

    public RtpConfig getConfig() { return config; }

    public void shutdown() {
        for (Integer taskId : warmupTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        warmupTasks.clear();
    }
}
