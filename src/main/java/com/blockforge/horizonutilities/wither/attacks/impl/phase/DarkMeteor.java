package com.blockforge.horizonutilities.wither.attacks.impl.phase;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.display.WitherParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns a 4x4 meteor made of block displays high above a target player.
 * A 3-second shadow warning appears on the ground before the meteor descends
 * rapidly, dealing 20 HP damage in a 10-block radius with fire and lava particles.
 */
public class DarkMeteor implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "dark_meteor";
    }

    @Override
    public String getDisplayName() {
        return "Dark Meteor";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
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
        Player target = boss.getHighestAggroTarget(64);
        if (target == null) target = boss.getNearestPlayer(64);
        if (target == null) return;

        World world = boss.getWither().getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Target ground position (snapshot at time of cast)
        Location groundTarget = target.getLocation().clone();
        groundTarget.setY(world.getHighestBlockYAt(groundTarget) + 0.1);

        // Meteor spawn position: Y+40 above the target
        Location meteorSpawn = groundTarget.clone().add(0, 40, 0);

        // Spawn 4x4 meteor block displays at the high position
        Material[] meteorMaterials = {Material.NETHERRACK, Material.MAGMA_BLOCK, Material.BLACKSTONE};
        List<BlockDisplay> meteorBlocks = new ArrayList<>();

        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                Material mat = meteorMaterials[rng.nextInt(meteorMaterials.length)];
                // Create a rough sphere shape — skip corner blocks
                if ((x == 0 || x == 3) && (z == 0 || z == 3)) continue;

                for (int y = 0; y < 2; y++) {
                    Location blockLoc = meteorSpawn.clone().add(x - 1.5, y, z - 1.5);
                    BlockDisplay display = boss.getDisplayPool().spawnBlockDisplay(
                            blockLoc,
                            mat.createBlockData(),
                            200 // 10 second lifetime (safety cleanup)
                    );
                    meteorBlocks.add(display);
                }
            }
        }

        // Phase 1: 3-second shadow warning on ground (60 ticks)
        Particle.DustOptions blackDust = new Particle.DustOptions(Color.fromRGB(20, 20, 20), 2.0f);

        BukkitTask shadowTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 60 || boss.isDead()) return;

                // Growing dark circle on ground
                double progress = tick / 60.0;
                double shadowRadius = 2.0 + progress * 8.0; // Grows from 2 to 10 blocks
                int points = (int) (shadowRadius * 4) + 8;
                WitherParticles.ring(groundTarget, shadowRadius, points, Particle.DUST, blackDust);

                // Inner smaller ring for depth
                if (shadowRadius > 3.0) {
                    WitherParticles.ring(groundTarget, shadowRadius * 0.5, points / 2, Particle.DUST, blackDust);
                }

                tick++;
            }
        }, 0L, 1L);
        activeTasks.add(shadowTask);

        // Shadow ground block display circle
        int groundDisplayPoints = 12;
        List<BlockDisplay> groundDisplays = new ArrayList<>();
        for (int i = 0; i < groundDisplayPoints; i++) {
            double angle = (2 * Math.PI / groundDisplayPoints) * i;
            double radius = 3.0;
            Location displayLoc = groundTarget.clone().add(
                    Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            BlockDisplay gd = boss.getDisplayPool().spawnBlockDisplay(
                    displayLoc,
                    Material.BLACK_CONCRETE.createBlockData(),
                    80 // 4 second lifetime
            );
            groundDisplays.add(gd);
        }

        // Warning sound
        world.playSound(groundTarget, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2.0f, 0.5f);

        // Auto-cancel shadow after 60 ticks
        BukkitTask cancelShadow = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            shadowTask.cancel();
            activeTasks.remove(shadowTask);
        }, 62L);
        activeTasks.add(cancelShadow);

        // Phase 2: After 3 seconds (60 ticks), meteor descends rapidly over 20 ticks
        BukkitTask descentTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            if (boss.isDead()) {
                // Clean up meteor blocks
                for (BlockDisplay bd : meteorBlocks) {
                    if (bd.isValid()) boss.getDisplayPool().remove(bd);
                }
                return;
            }

            // Calculate descent: move from current Y to ground Y over 20 ticks
            double startY = meteorSpawn.getY();
            double endY = groundTarget.getY();
            double descentPerTick = (startY - endY) / 20.0;

            BukkitTask moveTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), new Runnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (tick >= 20 || boss.isDead()) {
                        // Impact
                        onMeteorImpact(boss, groundTarget, meteorBlocks, world);
                        return;
                    }

                    // Move all meteor block displays downward
                    for (BlockDisplay bd : meteorBlocks) {
                        if (!bd.isValid()) continue;
                        Location loc = bd.getLocation();
                        loc.subtract(0, descentPerTick, 0);
                        bd.teleport(loc);
                    }

                    // Descent trail particles
                    Location trailLoc = meteorSpawn.clone().add(0, -(descentPerTick * tick), 0);
                    world.spawnParticle(Particle.FLAME, trailLoc, 10, 1.5, 0.5, 1.5, 0.05);
                    world.spawnParticle(Particle.SMOKE, trailLoc, 8, 1.0, 0.5, 1.0, 0.03);

                    // Descent sound
                    if (tick % 4 == 0) {
                        world.playSound(trailLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 0.5f);
                    }

                    tick++;
                }
            }, 0L, 1L);
            activeTasks.add(moveTask);

            // Auto-cancel move task after 20 ticks (impact forced)
            BukkitTask cancelMove = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                moveTask.cancel();
                activeTasks.remove(moveTask);
            }, 22L);
            activeTasks.add(cancelMove);

        }, 60L);
        activeTasks.add(descentTask);
    }

    private void onMeteorImpact(WitherBossEntity boss, Location impactLoc, List<BlockDisplay> meteorBlocks, World world) {
        // Remove all meteor block displays
        for (BlockDisplay bd : meteorBlocks) {
            if (bd.isValid()) {
                boss.getDisplayPool().remove(bd);
            }
        }
        meteorBlocks.clear();

        // Impact location at ground level
        Location groundImpact = impactLoc.clone();
        groundImpact.setY(world.getHighestBlockYAt(impactLoc) + 1);

        // 10-block radius explosion: 20 HP damage
        double explosionRadius = 10.0;
        double explosionRadiusSq = explosionRadius * explosionRadius;

        for (Player player : boss.getPlayersInRange(explosionRadius + 5)) {
            if (player.getLocation().distanceSquared(groundImpact) <= explosionRadiusSq) {
                player.damage(20.0, boss.getWither());

                // Knockback away from impact
                Vector knockback = player.getLocation().toVector()
                        .subtract(groundImpact.toVector());
                if (knockback.lengthSquared() < 0.01) {
                    knockback = new Vector(0.1, 0, 0.1);
                }
                knockback.normalize().multiply(1.2).setY(0.6);
                player.setVelocity(knockback);
            }
        }

        // Massive impact particles
        WitherParticles.explosion(groundImpact, 50, Particle.EXPLOSION);
        world.spawnParticle(Particle.FLAME, groundImpact, 80, 4.0, 1.0, 4.0, 0.1);
        world.spawnParticle(Particle.LAVA, groundImpact, 40, 3.0, 0.5, 3.0, 0);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, groundImpact, 50,
                5.0, 2.0, 5.0, 0.08);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, groundImpact, 30,
                3.0, 0.5, 3.0, 0.06);

        // Impact ring particles
        WitherParticles.ring(groundImpact, explosionRadius, 48, Particle.FLAME, null);

        // Impact sound
        world.playSound(groundImpact, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
