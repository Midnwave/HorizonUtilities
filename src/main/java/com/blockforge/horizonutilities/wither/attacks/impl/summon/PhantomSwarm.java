package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns 6-8 Phantoms above the wither (Y+10). Each phantom targets the nearest player.
 * Dark portal particles above the wither during spawn.
 */
public class PhantomSwarm implements WitherAttack {

    @Override
    public String getId() {
        return "phantom_swarm";
    }

    @Override
    public String getDisplayName() {
        return "Phantom Swarm";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 18.0;
    }

    @Override
    public double getWeight(int phase) {
        return 1.5;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Location witherLoc = boss.getWither().getLocation();
        Location spawnCenter = witherLoc.clone().add(0, 10, 0);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = rng.nextInt(6, 9); // 6-8 phantoms

        // Dark portal particles above the wither
        spawnCenter.getWorld().spawnParticle(Particle.PORTAL, spawnCenter, 80,
                2.0, 1.0, 2.0, 0.5);

        Player nearest = boss.getNearestPlayer(64);

        for (int i = 0; i < count; i++) {
            double offsetX = rng.nextDouble(-3.0, 3.0);
            double offsetZ = rng.nextDouble(-3.0, 3.0);
            Location phantomLoc = spawnCenter.clone().add(offsetX, rng.nextDouble(-1.0, 1.0), offsetZ);

            Phantom phantom = (Phantom) phantomLoc.getWorld().spawnEntity(phantomLoc, EntityType.PHANTOM);
            phantom.setSize(2);
            phantom.setPersistent(false);
            phantom.setRemoveWhenFarAway(false);

            if (nearest != null) {
                phantom.setTarget(nearest);
            }

            boss.addMinion(phantom.getUniqueId());
        }

        witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_PHANTOM_AMBIENT, 2.0f, 0.7f);
    }

    @Override
    public void cleanup() {
        // Minions are tracked by boss entity and cleaned up on boss death
    }
}
