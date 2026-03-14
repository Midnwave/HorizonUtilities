package com.blockforge.horizonutilities.wither.attacks.impl.debuff;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chains the highest-aggro player with a particle tether. Applies Slowness IV for 4 seconds.
 * If the player moves 20+ blocks away during the tether, deals 16 damage (8 hearts) and cancels.
 * CRIT particles drawn between boss and player every 4 ticks for 4 seconds.
 */
public class SoulShackle implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "soul_shackle";
    }

    @Override
    public String getDisplayName() {
        return "Soul Shackle";
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
        return 3.0;
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

        // Apply Slowness IV for 4 seconds (80 ticks)
        finalTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, true, true));

        // Initial chain sound
        finalTarget.getWorld().playSound(finalTarget.getLocation(),
                Sound.BLOCK_CHAIN_PLACE, 2.0f, 0.6f);
        boss.getWither().getWorld().playSound(boss.getWither().getLocation(),
                Sound.ENTITY_EVOKER_CAST_SPELL, 1.5f, 0.8f);

        // Draw chain particles every 4 ticks for 4 seconds (20 iterations)
        BukkitTask chainTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                if (boss.isDead() || !finalTarget.isOnline() || finalTarget.isDead()) {
                    return;
                }

                Location witherLoc = boss.getWither().getLocation().add(0, 1.5, 0);
                Location playerLoc = finalTarget.getLocation().add(0, 1.0, 0);

                // Check if player moved 20+ blocks from the wither's current position
                double distance = witherLoc.distance(playerLoc);
                if (distance >= 20.0) {
                    // Snap the chain — deal 16 damage (8 hearts)
                    finalTarget.damage(16.0, boss.getWither());
                    finalTarget.getWorld().playSound(finalTarget.getLocation(),
                            Sound.BLOCK_CHAIN_BREAK, 2.0f, 0.5f);
                    finalTarget.getWorld().spawnParticle(Particle.CRIT, playerLoc, 30,
                            0.5, 0.5, 0.5, 0.3);
                    // Task will be cancelled by the cleanup scheduled below
                    return;
                }

                // Draw CRIT particle beam from wither to player
                drawChainParticles(witherLoc, playerLoc);

                // Chain ambient sound every other iteration
                if (tick % 2 == 0) {
                    finalTarget.getWorld().playSound(finalTarget.getLocation(),
                            Sound.BLOCK_CHAIN_STEP, 0.8f, 1.2f);
                }

                tick++;
            }
        }, 0L, 4L);
        activeTasks.add(chainTask);

        // Cancel chain after 4 seconds (80 ticks)
        BukkitTask cancelTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            chainTask.cancel();
            activeTasks.remove(chainTask);

            if (!finalTarget.isOnline() || finalTarget.isDead()) return;

            // Chain fade sound
            finalTarget.getWorld().playSound(finalTarget.getLocation(),
                    Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.5f);
        }, 80L);
        activeTasks.add(cancelTask);
    }

    private void drawChainParticles(Location from, Location to) {
        double distance = from.distance(to);
        int points = (int) (distance * 2);
        if (points < 2) points = 2;

        for (int i = 0; i <= points; i++) {
            double ratio = (double) i / points;
            double x = from.getX() + (to.getX() - from.getX()) * ratio;
            double y = from.getY() + (to.getY() - from.getY()) * ratio;
            double z = from.getZ() + (to.getZ() - from.getZ()) * ratio;
            Location particleLoc = new Location(from.getWorld(), x, y, z);
            from.getWorld().spawnParticle(Particle.CRIT, particleLoc, 1, 0.05, 0.05, 0.05, 0);
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
