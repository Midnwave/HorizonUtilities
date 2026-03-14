package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Summons a large wave of 15-20 mixed undead (zombies, skeletons, wither skeletons)
 * in random positions around the wither. Each mob gets random basic gear.
 * Ground eruption particles at each spawn point.
 */
public class NecroHorde implements WitherAttack {

    @Override
    public String getId() {
        return "necro_horde";
    }

    @Override
    public String getDisplayName() {
        return "Necro Horde";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 35.0;
    }

    @Override
    public double getWeight(int phase) {
        return 1.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Location witherLoc = boss.getWither().getLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = rng.nextInt(15, 21); // 15-20 undead

        // War horn sound
        witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_RAVAGER_ROAR, 2.5f, 0.6f);

        EntityType[] undeadTypes = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITHER_SKELETON};
        Material[] weapons = {Material.STONE_SWORD, Material.IRON_SWORD, Material.STONE_AXE, Material.IRON_AXE};
        Material[] helmets = {Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET};

        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double distance = rng.nextDouble(5.0, 10.0);
            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;

            Location spawnLoc = witherLoc.clone().add(offsetX, 0, offsetZ);
            // Find ground level
            spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 1);

            // Ground eruption particles with soul sand data
            spawnLoc.getWorld().spawnParticle(Particle.BLOCK, spawnLoc, 25,
                    0.5, 0.2, 0.5, 0.1, Material.SOUL_SAND.createBlockData());

            // Rising soul particles
            spawnLoc.getWorld().spawnParticle(Particle.SOUL, spawnLoc, 5,
                    0.3, 0.5, 0.3, 0.02);

            // Spawn random undead type
            EntityType type = undeadTypes[rng.nextInt(undeadTypes.length)];
            LivingEntity mob = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, type);

            // Random gear
            if (rng.nextDouble() < 0.6) {
                mob.getEquipment().setItemInMainHand(new ItemStack(weapons[rng.nextInt(weapons.length)]));
                mob.getEquipment().setItemInMainHandDropChance(0.0f);
            }
            if (rng.nextDouble() < 0.4) {
                mob.getEquipment().setHelmet(new ItemStack(helmets[rng.nextInt(helmets.length)]));
                mob.getEquipment().setHelmetDropChance(0.0f);
            }

            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(false);

            boss.addMinion(mob.getUniqueId());
        }
    }

    @Override
    public void cleanup() {
        // Minions are tracked by boss entity and cleaned up on boss death
    }
}
