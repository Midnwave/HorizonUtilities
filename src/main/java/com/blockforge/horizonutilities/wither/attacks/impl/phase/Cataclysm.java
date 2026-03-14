package com.blockforge.horizonutilities.wither.attacks.impl.phase;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase 4 ultimate attack: The Wither flies upward, charges a dark orb that grows
 * in scale, then slams down with a devastating 20-block radius AoE dealing 30 HP
 * damage with massive knockback. Creates a crater ring of cracked deepslate bricks.
 */
public class Cataclysm implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "cataclysm";
    }

    @Override
    public String getDisplayName() {
        return "Cataclysm";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(4);
    }

    @Override
    public double getCooldownSeconds() {
        return 60.0;
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
        World world = boss.getWither().getWorld();
        Location startLoc = boss.getWither().getLocation().clone();

        // Phase 1: Fly upward and charge (60 ticks = 3 seconds)
        // Charging sound
        world.playSound(startLoc, Sound.BLOCK_BEACON_ACTIVATE, 3.0f, 0.5f);

        // Spawn the dark orb block display at the wither's location
        BlockDisplay orbDisplay = world.spawn(
                boss.getWither().getLocation().clone().add(0, 2, 0),
                BlockDisplay.class,
                entity -> {
                    entity.setBlock(Material.COAL_BLOCK.createBlockData());
                    entity.setPersistent(false);
                    // Initial small scale
                    entity.setTransformation(new Transformation(
                            new Vector3f(-0.25f, -0.25f, -0.25f), // translation (center the block)
                            new AxisAngle4f(0, 0, 1, 0),           // left rotation
                            new Vector3f(0.5f, 0.5f, 0.5f),        // scale
                            new AxisAngle4f(0, 0, 1, 0)            // right rotation
                    ));
                }
        );
        boss.getDisplayPool().track(orbDisplay);

        // Fly upward task: move wither toward target Y over 60 ticks
        BukkitTask flyTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 60 || boss.isDead()) return;

                // Move wither upward
                Location currentLoc = boss.getWither().getLocation();
                double progress = tick / 60.0;
                double targetY = startLoc.getY() + 30.0 * progress;
                if (currentLoc.getY() < targetY) {
                    boss.getWither().setVelocity(new Vector(0, 0.8, 0));
                }

                // Growing orb scale: 0.5 to 4.0 over 60 ticks
                float scale = 0.5f + (3.5f * (tick / 60.0f));
                float halfScale = -scale / 2.0f;
                if (orbDisplay.isValid()) {
                    orbDisplay.teleport(boss.getWither().getLocation().clone().add(0, 2, 0));
                    orbDisplay.setTransformation(new Transformation(
                            new Vector3f(halfScale, halfScale, halfScale),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(scale, scale, scale),
                            new AxisAngle4f(0, 0, 1, 0)
                    ));
                }

                // Dark particle vortex spiraling around the orb
                Location orbLoc = boss.getWither().getLocation().clone().add(0, 2, 0);
                double spiralAngle = tick * 0.4;
                double spiralRadius = 1.0 + scale * 0.5;
                for (int i = 0; i < 6; i++) {
                    double angle = spiralAngle + (i * Math.PI / 3.0);
                    double yOffset = (tick % 20) / 20.0 * 2.0;
                    Location vortexLoc = orbLoc.clone().add(
                            Math.cos(angle) * spiralRadius,
                            yOffset - 1.0,
                            Math.sin(angle) * spiralRadius
                    );
                    world.spawnParticle(Particle.SMOKE, vortexLoc, 2, 0.1, 0.1, 0.1, 0.02);
                    world.spawnParticle(Particle.SOUL, vortexLoc, 1, 0.1, 0.1, 0.1, 0.01);
                }

                // Escalating bass note sound every 10 ticks
                if (tick % 10 == 0) {
                    float pitch = 0.5f + (tick / 60.0f) * 1.5f;
                    world.playSound(orbLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 2.0f, pitch);
                }

                tick++;
            }
        }, 0L, 1L);
        activeTasks.add(flyTask);

        // Auto-cancel fly task after 60 ticks
        BukkitTask cancelFly = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            flyTask.cancel();
            activeTasks.remove(flyTask);
        }, 62L);
        activeTasks.add(cancelFly);

        // Phase 2: Slam down after 60 ticks
        BukkitTask slamTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            if (boss.isDead()) return;

            // Remove the orb display
            if (orbDisplay.isValid()) {
                boss.getDisplayPool().remove(orbDisplay);
            }

            // Slam the wither down to ground level
            Location witherLoc = boss.getWither().getLocation();
            double groundY = world.getHighestBlockYAt(witherLoc) + 1;
            Location slamTarget = witherLoc.clone();
            slamTarget.setY(groundY);

            // Rapid downward velocity
            boss.getWither().setVelocity(new Vector(0, -4.0, 0));

            // Delay the impact effects by a few ticks for the slam to reach ground
            BukkitTask impactTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                if (boss.isDead()) return;

                Location impactLoc = boss.getWither().getLocation().clone();
                impactLoc.setY(world.getHighestBlockYAt(impactLoc) + 1);

                // 20-block radius AoE: 30 HP damage + massive knockback
                double aoeRadius = 20.0;
                double aoeRadiusSq = aoeRadius * aoeRadius;

                for (Player player : boss.getPlayersInRange(aoeRadius + 5)) {
                    double distSq = player.getLocation().distanceSquared(impactLoc);
                    if (distSq <= aoeRadiusSq) {
                        // Damage
                        player.damage(30.0, boss.getWither());

                        // Massive knockback: outward horizontal + upward
                        Vector knockback = player.getLocation().toVector()
                                .subtract(impactLoc.toVector());
                        if (knockback.lengthSquared() < 0.01) {
                            knockback = new Vector(0.1, 0, 0.1);
                        }
                        knockback.normalize().multiply(1.5).setY(2.0);
                        player.setVelocity(knockback);
                    }
                }

                // Massive particle explosion
                WitherParticles.explosion(impactLoc, 60, Particle.EXPLOSION);
                world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, impactLoc, 100,
                        8.0, 2.0, 8.0, 0.1);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, impactLoc, 60,
                        5.0, 1.0, 5.0, 0.08);

                // Crater ring of cracked deepslate bricks (block displays)
                int craterPoints = 24;
                double craterRadius = 8.0;
                for (int i = 0; i < craterPoints; i++) {
                    double angle = (2 * Math.PI / craterPoints) * i;
                    Location craterLoc = impactLoc.clone().add(
                            Math.cos(angle) * craterRadius,
                            0,
                            Math.sin(angle) * craterRadius
                    );
                    craterLoc.setY(world.getHighestBlockYAt(craterLoc));
                    boss.getDisplayPool().spawnBlockDisplay(
                            craterLoc,
                            Material.CRACKED_DEEPSLATE_BRICKS.createBlockData(),
                            200 // 10 second lifetime
                    );
                }

                // Explosion sound at max volume
                world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);

                // Screen shake effect: cycle Blindness (1 tick on, 1 tick off) for 10 ticks
                BukkitTask shakeTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
                    int shakeTick = 0;

                    @Override
                    public void run() {
                        if (shakeTick >= 10 || boss.isDead()) return;

                        if (shakeTick % 2 == 0) {
                            // Apply 2-tick blindness (just enough to flicker)
                            for (Player player : boss.getPlayersInRange(aoeRadius + 5)) {
                                player.addPotionEffect(new PotionEffect(
                                        PotionEffectType.BLINDNESS, 2, 0, false, false, false));
                            }
                        }

                        shakeTick++;
                    }
                }, 0L, 1L);
                activeTasks.add(shakeTask);

                // Cancel shake after 10 ticks
                BukkitTask cancelShake = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                    shakeTask.cancel();
                    activeTasks.remove(shakeTask);
                }, 12L);
                activeTasks.add(cancelShake);

            }, 5L); // 5 tick delay for slam to reach ground
            activeTasks.add(impactTask);

        }, 60L);
        activeTasks.add(slamTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
