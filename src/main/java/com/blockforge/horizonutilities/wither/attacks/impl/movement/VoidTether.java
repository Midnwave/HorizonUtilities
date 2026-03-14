package com.blockforge.horizonutilities.wither.attacks.impl.movement;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Teleports the wither 15 blocks above the nearest player, then after 30 ticks
 * slams down to the player's location. Dragon breath trail particles during descent.
 * On impact: 6-block shockwave dealing 28 damage (14 hearts) + Slowness II for 3 seconds.
 */
public class VoidTether implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "void_tether";
    }

    @Override
    public String getDisplayName() {
        return "Void Tether";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 22.0;
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
        Player target = boss.getNearestPlayer(64);
        if (target == null) return;

        World world = boss.getWither().getWorld();
        Location targetLoc = target.getLocation().clone();

        // Teleport wither 15 blocks above the player
        Location aboveLoc = targetLoc.clone().add(0, 15, 0);
        boss.getWither().teleport(aboveLoc);

        // Teleport sound + particles at arrival
        world.playSound(aboveLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.6f);
        world.spawnParticle(Particle.PORTAL, aboveLoc, 30, 0.5, 0.5, 0.5, 0.3);
        world.spawnParticle(Particle.SMOKE, aboveLoc, 15, 0.3, 0.3, 0.3, 0.05);

        // Charge-up warning sound
        world.playSound(aboveLoc, Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.5f);

        // After 30 ticks, slam down to the player's current location
        BukkitTask slamTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            if (boss.isDead()) return;

            // Snapshot the target's current position for the slam
            Player currentTarget = boss.getNearestPlayer(64);
            final Location slamTarget;
            if (currentTarget != null) {
                slamTarget = currentTarget.getLocation().clone();
                slamTarget.setY(world.getHighestBlockYAt(slamTarget) + 1);
            } else {
                slamTarget = targetLoc.clone();
                slamTarget.setY(world.getHighestBlockYAt(slamTarget) + 1);
            }

            // Descent — rapid dive over ticks
            final int[] diveTick = {0};
            final int maxDiveTicks = 15;

            BukkitTask diveTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                if (boss.isDead()) return;
                diveTick[0]++;

                Location currentWitherLoc = boss.getWither().getLocation();
                Vector toTarget = slamTarget.toVector().subtract(currentWitherLoc.toVector());

                if (toTarget.lengthSquared() > 4.0 && diveTick[0] < maxDiveTicks) {
                    // Dive toward target
                    Vector diveVelocity = toTarget.normalize().multiply(3.5);
                    boss.getWither().setVelocity(diveVelocity);

                    // Dragon breath trail particles during descent
                    world.spawnParticle(Particle.DRAGON_BREATH, currentWitherLoc, 8,
                            0.3, 0.3, 0.3, 0.05);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, currentWitherLoc, 4,
                            0.2, 0.2, 0.2, 0.02);
                } else {
                    // Impact reached — teleport to exact location and deal damage
                    boss.getWither().teleport(slamTarget);
                    onSlamImpact(boss, slamTarget);
                }
            }, 0L, 1L);
            activeTasks.add(diveTask);

            // Force impact after maxDiveTicks if still diving
            BukkitTask diveTimeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                diveTask.cancel();
                activeTasks.remove(diveTask);
                if (!boss.isDead()) {
                    boss.getWither().teleport(slamTarget);
                    onSlamImpact(boss, slamTarget);
                }
            }, maxDiveTicks + 1L);
            activeTasks.add(diveTimeout);

        }, 30L);
        activeTasks.add(slamTask);
    }

    private void onSlamImpact(WitherBossEntity boss, Location impact) {
        World world = impact.getWorld();

        // Explosion particles
        world.spawnParticle(Particle.EXPLOSION, impact, 5, 1.0, 0.5, 1.0, 0);
        world.spawnParticle(Particle.DRAGON_BREATH, impact, 50, 3.0, 0.3, 3.0, 0.08);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, impact, 40, 2.5, 0.3, 2.5, 0.06);
        world.spawnParticle(Particle.SMOKE, impact, 30, 2.0, 0.5, 2.0, 0.05);

        // Impact sounds
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(impact, Sound.ENTITY_WITHER_BREAK_BLOCK, 2.0f, 0.7f);

        // 6-block shockwave — 28 damage (14 hearts) + Slowness II 3s
        double radiusSq = 36.0; // 6^2
        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(impact) <= radiusSq) {
                // 28 damage (14 hearts)
                player.damage(28.0, boss.getWither());

                // Slowness II for 3 seconds (60 ticks)
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 60, 1, false, true, true));

                // Knockback away from impact
                Vector knockback = player.getLocation().toVector().subtract(impact.toVector());
                if (knockback.lengthSquared() < 0.01) {
                    knockback = new Vector(0, 1.0, 0);
                } else {
                    knockback.normalize().multiply(1.5);
                    knockback.setY(0.8);
                }
                player.setVelocity(player.getVelocity().add(knockback));
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
