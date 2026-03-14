package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fires 12 skulls in a tight cone (15 degree spread) toward the nearest player.
 * Short range focus. CAMPFIRE_COSY_SMOKE particles at origin. Each skull has
 * slightly randomized direction.
 */
public class ShotgunBlast implements WitherAttack {

    @Override
    public String getId() {
        return "shotgun_blast";
    }

    @Override
    public String getDisplayName() {
        return "Shotgun Blast";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(1, 2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 5.0;
    }

    @Override
    public double getWeight(int phase) {
        return 3.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Player target = boss.getNearestPlayer(64);
        if (target == null) return;

        Location origin = boss.getWither().getLocation().add(0, 1, 0);
        Vector baseDirection = target.getLocation().add(0, 1, 0).toVector()
                .subtract(origin.toVector()).normalize();

        // Smoke effect at origin
        origin.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, origin, 15, 0.5, 0.3, 0.5, 0.02);

        // Build orthogonal basis for cone spread
        Vector up = new Vector(0, 1, 0);
        Vector right = baseDirection.clone().crossProduct(up).normalize();
        if (right.lengthSquared() < 0.01) {
            right = baseDirection.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        }
        Vector actualUp = right.clone().crossProduct(baseDirection).normalize();

        double halfSpread = Math.toRadians(15);
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int skullCount = 12;

        for (int i = 0; i < skullCount; i++) {
            double yawOffset = rand.nextDouble(-halfSpread, halfSpread);
            double pitchOffset = rand.nextDouble(-halfSpread, halfSpread);

            Vector direction = baseDirection.clone()
                    .add(right.clone().multiply(Math.sin(yawOffset)))
                    .add(actualUp.clone().multiply(Math.sin(pitchOffset)))
                    .normalize()
                    .multiply(1.3);

            WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction);
            skull.setYield(0);
            boss.getDisplayPool().track(skull);
        }
    }

    @Override
    public void cleanup() {
        // No lingering tasks — skulls are fire-and-forget
    }
}
