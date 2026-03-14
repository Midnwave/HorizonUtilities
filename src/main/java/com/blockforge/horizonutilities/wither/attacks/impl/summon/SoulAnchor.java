package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Targets the highest-aggro player and spawns a Soul Lantern anchor at their position.
 * Chain beam particles connect the lantern to the player. Player gets Slowness III.
 * If the player moves more than 8 blocks from the anchor, they take 10 HP damage.
 * Duration: 100 ticks (5 seconds).
 */
public class SoulAnchor implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "soul_anchor";
    }

    @Override
    public String getDisplayName() {
        return "Soul Anchor";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 16.0;
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
        Player target = boss.getHighestAggroTarget(64);
        if (target == null) target = boss.getNearestPlayer(64);
        if (target == null) return;

        final Player finalTarget = target;
        Location anchorLoc = finalTarget.getLocation().clone();

        // Spawn Soul Lantern item display at the player's position (tracked + auto-removed by pool)
        boss.getDisplayPool().spawnItemDisplay(
                anchorLoc.clone().add(0, 1.5, 0),
                new ItemStack(Material.SOUL_LANTERN),
                100 // 5 second lifetime
        );

        // Chain placement sound
        anchorLoc.getWorld().playSound(anchorLoc, Sound.BLOCK_CHAIN_PLACE, 2.0f, 0.8f);

        // Apply Slowness III for 5 seconds
        finalTarget.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, 100, 2, false, true, true));

        // Chain check task — every 5 ticks
        BukkitTask chainTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead() || !finalTarget.isOnline() || finalTarget.isDead()) return;

            Location playerLoc = finalTarget.getLocation().add(0, 1, 0);
            Location lanternLoc = anchorLoc.clone().add(0, 1.5, 0);

            // Draw chain beam from lantern to player
            WitherParticles.beam(lanternLoc, playerLoc, Particle.ENCHANTED_HIT, null, 3.0);

            // Ambient particles around the lantern
            lanternLoc.getWorld().spawnParticle(Particle.SOUL, lanternLoc, 5,
                    0.3, 0.3, 0.3, 0.02);

            // Check distance — if player moves more than 8 blocks from anchor, deal damage
            double distanceSq = finalTarget.getLocation().distanceSquared(anchorLoc);
            if (distanceSq > 64.0) { // 8^2
                finalTarget.damage(10.0, boss.getWither());
                // Visual feedback for chain break damage
                finalTarget.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                        finalTarget.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
            }
        }, 0L, 5L);
        activeTasks.add(chainTask);

        // Cancel after duration
        BukkitTask cancelTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            chainTask.cancel();
            activeTasks.remove(chainTask);
        }, 100L);
        activeTasks.add(cancelTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
