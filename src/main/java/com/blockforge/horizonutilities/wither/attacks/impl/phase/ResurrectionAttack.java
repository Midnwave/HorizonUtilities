package com.blockforge.horizonutilities.wither.attacks.impl.phase;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.WitherBossEntity.MinionDeathRecord;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase 4 one-time attack: revives ALL previously killed minions at their death locations.
 * Green soul wisps rise from each death location before the skeleton spawns.
 * Triggered by the phase manager when entering phase 4, never randomly selected.
 */
public class ResurrectionAttack implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private boolean used = false;

    @Override
    public String getId() {
        return "resurrection";
    }

    @Override
    public String getDisplayName() {
        return "Resurrection";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(4);
    }

    @Override
    public double getCooldownSeconds() {
        return 999.0;
    }

    @Override
    public double getWeight(int phase) {
        return 0.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return !used && boss.getCurrentPhase() == 4 && !boss.getMinionDeaths().isEmpty();
    }

    @Override
    public void execute(WitherBossEntity boss) {
        used = true;

        // Snapshot the death records before clearing
        List<MinionDeathRecord> deaths = new ArrayList<>(boss.getMinionDeaths());
        World world = boss.getWither().getWorld();

        // Announcement sound at wither location
        world.playSound(boss.getWither().getLocation(), Sound.ENTITY_WITHER_AMBIENT, 3.0f, 0.4f);

        // For each death record: rising soul wisps over 2 seconds, then spawn skeleton
        for (MinionDeathRecord record : deaths) {
            Location deathLoc = record.location().clone();

            // Ensure we're in the same world
            if (!deathLoc.getWorld().equals(world)) continue;

            // Phase 1: Rising green soul wisp particles over 2 seconds (40 ticks)
            BukkitTask wispTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (tick >= 40 || boss.isDead()) return;

                    double progress = tick / 40.0;
                    double yOffset = progress * 3.0; // Rise 3 blocks over 2 seconds

                    Location particleLoc = deathLoc.clone().add(0, yOffset, 0);

                    // Green soul wisps
                    world.spawnParticle(Particle.SOUL, particleLoc, 3,
                            0.2, 0.1, 0.2, 0.02);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 2,
                            0.15, 0.1, 0.15, 0.01);

                    // Spiral particles around the wisp
                    double angle = tick * 0.3;
                    double radius = 0.5;
                    Location spiralLoc = particleLoc.clone().add(
                            Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.SOUL, spiralLoc, 1, 0, 0, 0, 0);

                    tick++;
                }
            }, 0L, 1L);
            activeTasks.add(wispTask);

            // Auto-cancel wisp task after 40 ticks
            BukkitTask cancelWisp = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                wispTask.cancel();
                activeTasks.remove(wispTask);
            }, 42L);
            activeTasks.add(cancelWisp);

            // Phase 2: After 2 seconds (40 ticks) — spawn the skeleton at the death location
            BukkitTask spawnTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                if (boss.isDead()) return;

                // Snap spawn location to ground level
                Location spawnLoc = deathLoc.clone();
                spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);

                // Burst of soul particles on spawn
                WitherParticles.explosion(spawnLoc, 20, Particle.SOUL);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, spawnLoc, 15,
                        0.5, 0.3, 0.5, 0.05);

                // Spawn the wither skeleton
                WitherSkeleton skeleton = (WitherSkeleton) world.spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
                skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                skeleton.getEquipment().setItemInMainHandDropChance(0.0f);
                skeleton.setPersistent(false);
                skeleton.setRemoveWhenFarAway(false);

                // Track as minion
                boss.addMinion(skeleton.getUniqueId());

                // Resurrection sound at each location
                world.playSound(spawnLoc, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.5f, 0.7f);
            }, 40L);
            activeTasks.add(spawnTask);
        }

        // Clear the minionDeaths list after processing
        boss.getMinionDeaths().clear();
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
