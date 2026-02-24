package com.blockforge.horizonutilities.bounty.listeners;

import com.blockforge.horizonutilities.bounty.BountyManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Listens for player deaths and triggers bounty claiming when applicable.
 */
public class BountyKillListener implements Listener {

    private final BountyManager bountyManager;

    public BountyKillListener(BountyManager bountyManager) {
        this.bountyManager = bountyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = resolveKiller(victim);

        if (killer == null) return;
        if (killer.getUniqueId().equals(victim.getUniqueId())) return;

        bountyManager.claimBounties(killer, victim);
    }

    /**
     * Resolves the actual player killer, handling direct kills and projectile kills.
     * Returns null if the killer cannot be determined or is not a player.
     */
    private Player resolveKiller(Player victim) {
        // Direct kill
        if (victim.getKiller() instanceof Player directKiller) {
            return directKiller;
        }

        // Projectile kill (arrow, trident, etc.)
        var lastDamageCause = victim.getLastDamageCause();
        if (lastDamageCause == null) return null;

        if (lastDamageCause instanceof org.bukkit.event.entity.EntityDamageByEntityEvent damageByEntity) {
            if (damageByEntity.getDamager() instanceof Projectile projectile) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Player shooterPlayer) {
                    return shooterPlayer;
                }
            }
        }

        return null;
    }
}
