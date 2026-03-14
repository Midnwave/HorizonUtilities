package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spawns a floating skull totem that pulses Wither II to nearby players.
 * The totem is an ItemDisplay with a wither skeleton skull that lasts 15 seconds.
 * Every second, it applies Wither II and purple particle effects.
 */
public class WitherTotem implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "wither_totem";
    }

    @Override
    public String getDisplayName() {
        return "Wither Totem";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 25.0;
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
        Location witherLoc = boss.getWither().getLocation().clone();
        // Spawn the totem slightly above the wither
        Location totemLoc = witherLoc.clone().add(0, 2, 0);

        // Spawn the floating skull totem as an ItemDisplay
        ItemDisplay totem = boss.getDisplayPool().spawnItemDisplay(
                totemLoc,
                new ItemStack(Material.WITHER_SKELETON_SKULL),
                300 // 15 seconds lifetime
        );

        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(128, 0, 200), 1.5f);

        // Pulse every 20 ticks (1 second) for the 15 second duration
        BukkitTask pulseTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            if (boss.isDead() || !totem.isValid()) return;

            Location currentTotemLoc = totem.getLocation();

            // Purple particle sphere around the totem
            WitherParticles.sphere(currentTotemLoc, 2.0, 30, Particle.DUST, purpleDust);

            // Ambient soul particles
            currentTotemLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    currentTotemLoc, 10, 0.5, 0.5, 0.5, 0.02);

            // Sound on each pulse
            currentTotemLoc.getWorld().playSound(currentTotemLoc, Sound.ENTITY_WITHER_AMBIENT, 1.5f, 1.2f);

            // Apply Wither II to all players within 10 blocks
            for (Player player : boss.getPlayersInRange(10)) {
                if (player.getLocation().distanceSquared(currentTotemLoc) <= 100.0) { // 10^2
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.WITHER, 200, 1, false, true, true));
                }
            }
        }, 0L, 20L);
        activeTasks.add(pulseTask);

        // Auto-cancel pulse task after 300 ticks
        BukkitTask cancelTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            pulseTask.cancel();
            activeTasks.remove(pulseTask);
        }, 300L);
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
