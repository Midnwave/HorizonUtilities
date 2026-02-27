package com.blockforge.horizonutilities.combat;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks PvP combat tags and enforces restrictions on tagged players.
 * A player is "in combat" until their timer expires or they die.
 */
public class CombatManager {

    private final HorizonUtilitiesPlugin plugin;
    private final CombatConfig config;

    /** UUID -> timestamp when combat ends (epoch ms) */
    private final Map<UUID, Long> combatExpiry = new ConcurrentHashMap<>();

    /** UUID of the last attacker, used for combat-log kill credit */
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();

    public CombatManager(HorizonUtilitiesPlugin plugin, CombatConfig config) {
        this.plugin  = plugin;
        this.config  = config;
        startActionBarTask();
    }

    // -------------------------------------------------------------------------
    // Tag / untag
    // -------------------------------------------------------------------------

    public void tag(Player attacker, Player victim) {
        if (!config.isEnabled()) return;
        long expiry = System.currentTimeMillis() + config.getCombatTimerSeconds() * 1000L;
        combatExpiry.put(attacker.getUniqueId(), expiry);
        combatExpiry.put(victim.getUniqueId(), expiry);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());
    }

    public void untag(UUID uuid) {
        combatExpiry.remove(uuid);
        lastAttacker.remove(uuid);
    }

    public boolean isInCombat(UUID uuid) {
        Long expiry = combatExpiry.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            combatExpiry.remove(uuid);
            lastAttacker.remove(uuid);
            return false;
        }
        return true;
    }

    /** Returns remaining combat seconds, or 0 if not in combat. */
    public int getRemainingSeconds(UUID uuid) {
        Long expiry = combatExpiry.get(uuid);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return (int) Math.max(0, (remaining + 999) / 1000);
    }

    public UUID getLastAttacker(UUID uuid) {
        return lastAttacker.get(uuid);
    }

    // -------------------------------------------------------------------------
    // Action bar countdown
    // -------------------------------------------------------------------------

    private void startActionBarTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : new ArrayList<>(combatExpiry.keySet())) {
                if (!isInCombat(uuid)) continue;
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) continue;
                int secs = getRemainingSeconds(uuid);
                p.sendActionBar(Component.text("⚔ Combat: ", NamedTextColor.RED)
                        .append(Component.text(secs + "s", NamedTextColor.YELLOW)));
            }
        }, 0L, 20L);
    }

    public CombatConfig getConfig() { return config; }
}
