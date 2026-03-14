package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Spawns 8 wither skeletons in a ring at 6-block radius around the wither.
 * Each skeleton has 30 HP and despawns after 12 seconds (240 ticks).
 * Spawned entities are tracked in boss.getActiveMinions().
 */
public class NecroticBarrier implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final List<UUID> barrierSkeletons = new ArrayList<>();

    @Override
    public String getId() {
        return "necrotic_barrier";
    }

    @Override
    public String getDisplayName() {
        return "Necrotic Barrier";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 25.0;
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
        double ringRadius = 6.0;
        int skeletonCount = 8;

        // Summoning sound
        witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);

        // Spawn 8 skeletons in a ring
        for (int i = 0; i < skeletonCount; i++) {
            double angle = (2 * Math.PI / skeletonCount) * i;
            double x = witherLoc.getX() + Math.cos(angle) * ringRadius;
            double z = witherLoc.getZ() + Math.sin(angle) * ringRadius;

            Location spawnLoc = new Location(witherLoc.getWorld(), x, witherLoc.getY(), z);
            // Snap to ground
            spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 1);
            // Face toward the wither
            spawnLoc.setYaw((float) Math.toDegrees(-angle + Math.PI));

            // Spawn particles at each position
            spawnLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, spawnLoc, 10,
                    0.3, 0.5, 0.3, 0.02);
            spawnLoc.getWorld().spawnParticle(Particle.SMOKE, spawnLoc, 8,
                    0.2, 0.3, 0.2, 0.01);

            WitherSkeleton skeleton = (WitherSkeleton) witherLoc.getWorld().spawnEntity(
                    spawnLoc, EntityType.WITHER_SKELETON);
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
            skeleton.getEquipment().setItemInMainHandDropChance(0.0f);
            skeleton.setPersistent(false);
            skeleton.setRemoveWhenFarAway(false);

            // Set health to 30 HP
            var healthAttr = skeleton.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(30.0);
                skeleton.setHealth(30.0);
            }

            boss.addMinion(skeleton.getUniqueId());
            barrierSkeletons.add(skeleton.getUniqueId());
        }

        // Despawn after 12 seconds (240 ticks)
        BukkitTask despawnTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            for (UUID skeletonId : barrierSkeletons) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(skeletonId);
                if (entity != null && entity.isValid()) {
                    // Death particles
                    entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation(), 15,
                            0.3, 0.5, 0.3, 0.05);
                    entity.remove();
                }
                boss.removeMinion(skeletonId);
            }
            barrierSkeletons.clear();
        }, 240L);
        activeTasks.add(despawnTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();

        // Remove remaining barrier skeletons
        for (UUID skeletonId : barrierSkeletons) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(skeletonId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        barrierSkeletons.clear();
    }
}
