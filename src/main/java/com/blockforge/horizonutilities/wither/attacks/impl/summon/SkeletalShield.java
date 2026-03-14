package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Spawns 4 wither skeletons that orbit the boss in a protective circle.
 * Skeletons are teleported each tick to maintain their orbital positions.
 * Duration: 15 seconds, after which skeletons die.
 */
public class SkeletalShield implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final List<UUID> shieldSkeletons = new ArrayList<>();

    @Override
    public String getId() {
        return "skeletal_shield";
    }

    @Override
    public String getDisplayName() {
        return "Skeletal Shield";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 20.0;
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
        double orbitRadius = 3.0;
        int skeletonCount = 4;

        // Create dark leather armor set
        ItemStack helmet = createDarkLeatherArmor(Material.LEATHER_HELMET);
        ItemStack chestplate = createDarkLeatherArmor(Material.LEATHER_CHESTPLATE);
        ItemStack leggings = createDarkLeatherArmor(Material.LEATHER_LEGGINGS);
        ItemStack boots = createDarkLeatherArmor(Material.LEATHER_BOOTS);

        // Spawn skeletons
        for (int i = 0; i < skeletonCount; i++) {
            double angle = (2 * Math.PI / skeletonCount) * i;
            double x = witherLoc.getX() + orbitRadius * Math.cos(angle);
            double z = witherLoc.getZ() + orbitRadius * Math.sin(angle);
            Location spawnLoc = new Location(witherLoc.getWorld(), x, witherLoc.getY(), z);

            WitherSkeleton skeleton = (WitherSkeleton) witherLoc.getWorld().spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
            skeleton.getEquipment().setHelmet(helmet.clone());
            skeleton.getEquipment().setChestplate(chestplate.clone());
            skeleton.getEquipment().setLeggings(leggings.clone());
            skeleton.getEquipment().setBoots(boots.clone());
            skeleton.getEquipment().setItemInMainHandDropChance(0.0f);
            skeleton.getEquipment().setHelmetDropChance(0.0f);
            skeleton.getEquipment().setChestplateDropChance(0.0f);
            skeleton.getEquipment().setLeggingsDropChance(0.0f);
            skeleton.getEquipment().setBootsDropChance(0.0f);
            skeleton.setPersistent(false);
            skeleton.setRemoveWhenFarAway(false);
            skeleton.setAI(false); // Disable AI so they orbit smoothly

            boss.addMinion(skeleton.getUniqueId());
            shieldSkeletons.add(skeleton.getUniqueId());
        }

        witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 2.0f, 0.6f);

        // Orbit task — teleport skeletons each tick
        BukkitTask orbitTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                if (boss.isDead()) return;

                Location center = boss.getWither().getLocation();
                double angleOffset = tick * 0.05; // Rotation speed

                for (int i = 0; i < shieldSkeletons.size(); i++) {
                    UUID skeletonId = shieldSkeletons.get(i);
                    org.bukkit.entity.Entity entity = Bukkit.getEntity(skeletonId);
                    if (entity == null || !entity.isValid()) continue;

                    double angle = angleOffset + (2 * Math.PI / skeletonCount) * i;
                    double x = center.getX() + orbitRadius * Math.cos(angle);
                    double z = center.getZ() + orbitRadius * Math.sin(angle);
                    Location orbitLoc = new Location(center.getWorld(), x, center.getY(), z,
                            (float) Math.toDegrees(-angle + Math.PI / 2), 0f);

                    entity.teleport(orbitLoc);

                    // Trail particles behind each skeleton
                    center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, orbitLoc, 2,
                            0.1, 0.1, 0.1, 0.01);
                }

                tick++;
            }
        }, 0L, 1L);
        activeTasks.add(orbitTask);

        // Kill skeletons and cancel after 300 ticks (15 seconds)
        BukkitTask cancelTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            orbitTask.cancel();
            activeTasks.remove(orbitTask);

            for (UUID skeletonId : shieldSkeletons) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(skeletonId);
                if (entity != null && entity.isValid()) {
                    // Death particles
                    entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation(), 15,
                            0.3, 0.5, 0.3, 0.05);
                    entity.remove();
                }
                boss.removeMinion(skeletonId);
            }
            shieldSkeletons.clear();
        }, 300L);
        activeTasks.add(cancelTask);
    }

    private ItemStack createDarkLeatherArmor(Material material) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(30, 30, 30));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();

        // Remove remaining shield skeletons
        for (UUID skeletonId : shieldSkeletons) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(skeletonId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        shieldSkeletons.clear();
    }
}
