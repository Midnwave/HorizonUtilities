package com.blockforge.horizonutilities.wither.attacks.impl.summon;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wither;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Spawns a second Wither entity (Sovereign's Shadow) at the boss's location
 * with half the boss's current health. The clone uses vanilla AI only.
 * Dark particle aura orbits the clone for its lifetime.
 */
public class ShadeClone implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private UUID cloneId;

    @Override
    public String getId() {
        return "shade_clone";
    }

    @Override
    public String getDisplayName() {
        return "Shade Clone";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 30.0;
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
        Location witherLoc = boss.getWither().getLocation().clone();

        // Split animation — dark particle burst
        WitherParticles.explosion(witherLoc, 60, Particle.SOUL_FIRE_FLAME);
        WitherParticles.explosion(witherLoc, 40, Particle.SMOKE);
        WitherParticles.sphere(witherLoc, 3.0, 50, Particle.SOUL, null);

        // Spawn sound
        witherLoc.getWorld().playSound(witherLoc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 1.5f);

        // Spawn clone wither
        Wither clone = (Wither) witherLoc.getWorld().spawnEntity(witherLoc, EntityType.WITHER);

        // Set half the boss's current health
        double cloneHealth = boss.getWither().getHealth() / 2.0;
        var healthAttr = clone.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(cloneHealth);
            clone.setHealth(cloneHealth);
        }

        // Custom name in gray
        clone.customName(Component.text("Sovereign's Shadow").color(NamedTextColor.GRAY));
        clone.setCustomNameVisible(true);
        clone.setPersistent(false);
        clone.setRemoveWhenFarAway(false);

        cloneId = clone.getUniqueId();
        boss.addMinion(cloneId);

        // Particle aura task — orbiting SMOKE + SOUL_FIRE_FLAME around the clone
        BukkitTask auraTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                if (boss.isDead()) return;

                org.bukkit.entity.Entity entity = Bukkit.getEntity(cloneId);
                if (entity == null || !entity.isValid()) return;

                Location cloneLoc = entity.getLocation().add(0, 1.5, 0);

                // Two orbiting particles
                double angle1 = tick * 0.15;
                double angle2 = angle1 + Math.PI;
                double radius = 1.5;

                Location p1 = cloneLoc.clone().add(
                        radius * Math.cos(angle1), Math.sin(tick * 0.1) * 0.5, radius * Math.sin(angle1));
                Location p2 = cloneLoc.clone().add(
                        radius * Math.cos(angle2), Math.sin(tick * 0.1 + Math.PI) * 0.5, radius * Math.sin(angle2));

                cloneLoc.getWorld().spawnParticle(Particle.SMOKE, p1, 3, 0.05, 0.05, 0.05, 0.01);
                cloneLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p2, 3, 0.05, 0.05, 0.05, 0.01);

                tick++;
            }
        }, 0L, 1L);
        activeTasks.add(auraTask);

        // Cancel aura when clone dies or after 60 seconds max
        BukkitTask cancelTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            auraTask.cancel();
            activeTasks.remove(auraTask);
        }, 1200L);
        activeTasks.add(cancelTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();

        // Remove clone if still alive
        if (cloneId != null) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(cloneId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            cloneId = null;
        }
    }
}
