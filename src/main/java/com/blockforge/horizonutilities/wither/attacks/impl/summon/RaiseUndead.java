package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns 4-6 Wither Skeletons near the wither in random positions 3-5 blocks away.
 * Each skeleton gets a stone sword. Ground crack particles at spawn locations.
 */
public class RaiseUndead implements WitherAttack {

    @Override
    public String getId() {
        return "raise_undead";
    }

    @Override
    public String getDisplayName() {
        return "Raise Undead";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 15.0;
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
        Location witherLoc = boss.getWither().getLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = rng.nextInt(4, 7); // 4-6 skeletons

        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double distance = rng.nextDouble(3.0, 5.0);
            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;

            Location spawnLoc = witherLoc.clone().add(offsetX, 0, offsetZ);
            // Find the highest solid block at the spawn location
            spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 1);

            // Ground crack particles
            spawnLoc.getWorld().spawnParticle(Particle.BLOCK, spawnLoc, 20,
                    0.5, 0.1, 0.5, 0.1, Material.SOUL_SAND.createBlockData());

            WitherSkeleton skeleton = (WitherSkeleton) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
            skeleton.getEquipment().setItemInMainHandDropChance(0.0f);
            skeleton.setPersistent(false);
            skeleton.setRemoveWhenFarAway(false);

            boss.addMinion(skeleton.getUniqueId());
        }

        witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 2.0f, 0.8f);
    }

    @Override
    public void cleanup() {
        // Minions are tracked by boss entity and cleaned up on boss death
    }
}
