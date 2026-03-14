package com.blockforge.horizonutilities.wither.attacks.impl.aoe;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detonates at the wither's location with a 4-block radius.
 * Only executable when boss.getActiveMinions().size() >= 2.
 * Deals 16 damage (8 hearts) and applies Wither II for 3 seconds.
 * Explosion particles and sound.
 */
public class CorpseExplosion implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "corpse_explosion";
    }

    @Override
    public String getDisplayName() {
        return "Corpse Explosion";
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
        return 2.0;
    }

    @Override
    public boolean canExecute(WitherBossEntity boss) {
        return boss.getActiveMinions().size() >= 2 && boss.getNearestPlayer(64) != null;
    }

    @Override
    public void execute(WitherBossEntity boss) {
        Location witherLoc = boss.getWither().getLocation().clone();
        World world = witherLoc.getWorld();

        // Charge-up sound
        world.playSound(witherLoc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);

        // Explosion particles
        world.spawnParticle(Particle.EXPLOSION, witherLoc, 3, 1.0, 0.5, 1.0, 0);
        world.spawnParticle(Particle.SMOKE, witherLoc, 40, 2.0, 1.0, 2.0, 0.08);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, witherLoc, 30, 2.0, 0.5, 2.0, 0.06);
        world.spawnParticle(Particle.SOUL, witherLoc, 20, 1.5, 0.5, 1.5, 0.05);

        // Explosion sound
        world.playSound(witherLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);

        // Damage players within 4-block radius
        double radiusSq = 16.0; // 4^2
        for (Player player : boss.getPlayersInRange(35)) {
            if (player.getLocation().distanceSquared(witherLoc) <= radiusSq) {
                // 16 damage (8 hearts)
                player.damage(16.0, boss.getWither());
                // Wither II for 3 seconds (60 ticks)
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WITHER, 60, 1, false, true, true));
            }
        }
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
