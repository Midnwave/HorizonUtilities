package com.blockforge.horizonutilities.wither.attacks.impl.aoe;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;

/**
 * Instant 360-degree repulsion blast that knocks back and damages all nearby players.
 * Expanding white particle sphere animation accompanies the blast.
 */
public class RepulsionBlast implements WitherAttack {

    @Override
    public String getId() {
        return "repulsion_blast";
    }

    @Override
    public String getDisplayName() {
        return "Repulsion Blast";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(1, 2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 10.0;
    }

    @Override
    public double getWeight(int phase) {
        return 2.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Location center = boss.getWither().getLocation().add(0, 1, 0);

        // Sound effect
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 2.0f, 0.6f);

        // Expanding particle sphere animation
        WitherParticles.sphere(center, 2.0, 40, Particle.CLOUD, null);
        WitherParticles.sphere(center, 5.0, 60, Particle.CLOUD, null);
        WitherParticles.explosion(center, 30, Particle.EXPLOSION);

        // Damage and knockback all players within 15 blocks
        List<Player> players = boss.getPlayersInRange(15);
        for (Player player : players) {
            // 4 hearts = 8 HP
            player.damage(8.0, boss.getWither());

            // Knockback away from wither
            Vector direction = player.getLocation().toVector()
                    .subtract(center.toVector());
            if (direction.lengthSquared() < 0.01) {
                // Player is basically on top of wither, pick random direction
                direction = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
            }
            direction.normalize().multiply(2.5).setY(0.5);
            player.setVelocity(direction);
        }
    }

    @Override
    public void cleanup() {
        // Instant attack — no lingering effects to clean up
    }
}
