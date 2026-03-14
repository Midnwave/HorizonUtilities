package com.blockforge.horizonutilities.wither;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.wither.ai.WitherAIController;
import com.blockforge.horizonutilities.wither.ai.WitherPhaseManager;
import com.blockforge.horizonutilities.wither.attacks.AttackScheduler;
import com.blockforge.horizonutilities.wither.display.DisplayEntityPool;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps a vanilla Wither entity with custom boss state: phases, attacks, AI, boss bar.
 */
public class WitherBossEntity {

    private final HorizonUtilitiesPlugin plugin;
    private final WitherBossConfig config;
    private final Wither wither;
    private final Location spawnLocation;
    private final UUID fightId;

    // State
    private int currentPhase = 1;
    private boolean enraged = false;
    private boolean dead = false;
    private long spawnTimeMillis;
    private double maxHealth;

    // Aggro table: player UUID -> total damage dealt
    private final Map<UUID, Double> aggroTable = new ConcurrentHashMap<>();

    // Participants (players who dealt damage)
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();

    // Summoned minion tracking
    private final Set<UUID> activeMinions = ConcurrentHashMap.newKeySet();

    // Death locations for Resurrection attack
    private final List<MinionDeathRecord> minionDeaths = Collections.synchronizedList(new ArrayList<>());

    // Systems
    private WitherPhaseManager phaseManager;
    private WitherAIController aiController;
    private AttackScheduler attackScheduler;
    private DisplayEntityPool displayPool;
    private BossBar bossBar;

    // Tasks
    private BukkitTask mainLoopTask;
    private BukkitTask regenTask;

    public WitherBossEntity(HorizonUtilitiesPlugin plugin, WitherBossConfig config, Wither wither, Location spawnLocation) {
        this.plugin = plugin;
        this.config = config;
        this.wither = wither;
        this.spawnLocation = spawnLocation;
        this.fightId = UUID.randomUUID();
        this.spawnTimeMillis = System.currentTimeMillis();
    }

    public void initialize(int nearbyPlayers) {
        // Calculate scaled health
        maxHealth = config.getHealth() + Math.max(0, nearbyPlayers - 1) * config.getPerPlayerHealthBonus();

        // Apply stats to wither
        var healthAttr = wither.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(maxHealth);
            wither.setHealth(maxHealth);
        }
        var armorAttr = wither.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(config.getArmor());
        }

        // Custom name
        wither.customName(Component.text("☠ The Wither Sovereign ☠")
                .color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
        wither.setCustomNameVisible(true);

        // Boss bar
        bossBar = BossBar.bossBar(
                Component.text("☠ The Wither Sovereign ☠")
                        .color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD),
                1.0f,
                BossBar.Color.WHITE,
                BossBar.Overlay.PROGRESS
        );

        // Initialize subsystems
        displayPool = new DisplayEntityPool(plugin);
        phaseManager = new WitherPhaseManager(this, config);
        aiController = new WitherAIController(this, config);
        attackScheduler = new AttackScheduler(this, config, plugin);

        // Start main loop (every tick)
        mainLoopTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);

        // Start regen task (every second)
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, this::regenTick, 20L, 20L);
    }

    private void tick() {
        if (dead || !wither.isValid()) {
            cleanup();
            return;
        }

        // Update boss bar progress
        double healthPercent = wither.getHealth() / maxHealth;
        bossBar.progress(Math.max(0f, Math.min(1f, (float) healthPercent)));

        // Show/hide boss bar for nearby players
        updateBossBarViewers();

        // Phase check
        phaseManager.update();

        // AI tick
        aiController.tick();

        // Attack scheduler tick
        attackScheduler.tick();
    }

    private void regenTick() {
        if (dead || !wither.isValid()) return;

        double healthPercent = wither.getHealth() / maxHealth;
        double regenRate = healthPercent <= 0.5 ? config.getRegenBelowHalf() : config.getRegenPerSecond();
        if (enraged) regenRate *= 1.5;

        double newHealth = Math.min(maxHealth, wither.getHealth() + regenRate);
        wither.setHealth(newHealth);
    }

    private void updateBossBarViewers() {
        Location loc = wither.getLocation();
        int radius = config.getMusicRadius(); // reuse music radius for boss bar

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(loc.getWorld()) && player.getLocation().distanceSquared(loc) <= radius * radius) {
                player.showBossBar(bossBar);
            } else {
                player.hideBossBar(bossBar);
            }
        }
    }

    public void onPhaseChange(int newPhase) {
        currentPhase = newPhase;

        // Update boss bar color
        BossBar.Color color = switch (newPhase) {
            case 2 -> BossBar.Color.YELLOW;
            case 3 -> BossBar.Color.RED;
            case 4 -> BossBar.Color.PURPLE;
            default -> BossBar.Color.WHITE;
        };
        bossBar.color(color);

        // Update boss bar name with phase
        String phaseName = config.getPhaseName(newPhase);
        bossBar.name(Component.text("☠ The Wither Sovereign ☠")
                .color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                .append(Component.text(" — " + phaseName)
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
    }

    public void setEnraged(boolean enraged) {
        this.enraged = enraged;
        if (enraged) {
            bossBar.name(Component.text("☠ The Wither Sovereign ☠ ")
                    .color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)
                    .append(Component.text("ENRAGED").color(NamedTextColor.RED)));
        }
    }

    public void recordDamage(UUID playerUuid, double damage) {
        aggroTable.merge(playerUuid, damage, Double::sum);
        participants.add(playerUuid);
    }

    public void addMinion(UUID entityUuid) {
        activeMinions.add(entityUuid);
    }

    public void removeMinion(UUID entityUuid) {
        activeMinions.remove(entityUuid);
    }

    public void recordMinionDeath(Location location, String type) {
        minionDeaths.add(new MinionDeathRecord(location, type));
    }

    public void cleanup() {
        dead = true;

        if (mainLoopTask != null) mainLoopTask.cancel();
        if (regenTask != null) regenTask.cancel();

        // Hide boss bar from all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }

        // Cleanup display entities
        if (displayPool != null) displayPool.cleanup();

        // Cleanup attacks
        if (attackScheduler != null) attackScheduler.cleanup();

        // Kill remaining minions
        for (UUID minionId : activeMinions) {
            var entity = Bukkit.getEntity(minionId);
            if (entity != null && entity.isValid()) entity.remove();
        }
        activeMinions.clear();
    }

    // Target selection: highest aggro player within range
    public Player getHighestAggroTarget(double maxRange) {
        double maxRangeSq = maxRange * maxRange;
        UUID best = null;
        double bestDamage = -1;

        for (var entry : aggroTable.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline() || p.isDead()) continue;
            if (!p.getWorld().equals(wither.getWorld())) continue;
            if (p.getLocation().distanceSquared(wither.getLocation()) > maxRangeSq) continue;

            if (entry.getValue() > bestDamage) {
                bestDamage = entry.getValue();
                best = entry.getKey();
            }
        }
        return best != null ? Bukkit.getPlayer(best) : null;
    }

    public Player getNearestPlayer(double maxRange) {
        double maxRangeSq = maxRange * maxRange;
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || !p.getWorld().equals(wither.getWorld())) continue;
            double dist = p.getLocation().distanceSquared(wither.getLocation());
            if (dist <= maxRangeSq && dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    public List<Player> getPlayersInRange(double range) {
        double rangeSq = range * range;
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || !p.getWorld().equals(wither.getWorld())) continue;
            if (p.getLocation().distanceSquared(wither.getLocation()) <= rangeSq) {
                players.add(p);
            }
        }
        return players;
    }

    public long getFightDurationSeconds() {
        return (System.currentTimeMillis() - spawnTimeMillis) / 1000;
    }

    // Getters
    public HorizonUtilitiesPlugin getPlugin() { return plugin; }
    public WitherBossConfig getConfig() { return config; }
    public Wither getWither() { return wither; }
    public Location getSpawnLocation() { return spawnLocation; }
    public UUID getFightId() { return fightId; }
    public int getCurrentPhase() { return currentPhase; }
    public boolean isEnraged() { return enraged; }
    public boolean isDead() { return dead; }
    public double getMaxHealth() { return maxHealth; }
    public Map<UUID, Double> getAggroTable() { return aggroTable; }
    public Set<UUID> getParticipants() { return participants; }
    public Set<UUID> getActiveMinions() { return activeMinions; }
    public List<MinionDeathRecord> getMinionDeaths() { return minionDeaths; }
    public WitherPhaseManager getPhaseManager() { return phaseManager; }
    public WitherAIController getAiController() { return aiController; }
    public AttackScheduler getAttackScheduler() { return attackScheduler; }
    public DisplayEntityPool getDisplayPool() { return displayPool; }
    public BossBar getBossBar() { return bossBar; }

    public record MinionDeathRecord(Location location, String type) {}
}
