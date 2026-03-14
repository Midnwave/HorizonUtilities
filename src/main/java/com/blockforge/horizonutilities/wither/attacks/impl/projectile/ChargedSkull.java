package com.blockforge.horizonutilities.wither.attacks.impl.projectile;

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
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 3-second charge phase with a growing dark orb (COAL_BLOCK display), escalating
 * bass sounds. After charge, fires a single large charged WitherSkull that deals
 * 45 HP damage. Purple explosion particles on impact.
 */
public class ChargedSkull implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "charged_skull";
    }

    @Override
    public String getDisplayName() {
        return "Charged Skull";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 18.0;
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
        Location witherLoc = boss.getWither().getLocation().add(0, 1.5, 0);

        // Spawn growing dark orb using block display
        BlockDisplay orb = boss.getDisplayPool().spawnBlockDisplay(
                witherLoc.clone().add(-0.25, -0.25, -0.25),
                Material.COAL_BLOCK.createBlockData(),
                70 // lifetime slightly longer than charge phase
        );

        // Start with small scale
        float initialScale = 0.3f;
        orb.setTransformation(new Transformation(
                new Vector3f(-initialScale / 2, -initialScale / 2, -initialScale / 2),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(initialScale, initialScale, initialScale),
                new AxisAngle4f(0, 0, 1, 0)
        ));

        Particle.DustOptions darkPurple = new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.5f);

        // Charge phase: 60 ticks (3 seconds) — grow orb and escalate sound
        final int chargeTicks = 60;
        final int[] tick = {0};

        BukkitTask chargeTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            tick[0]++;
            if (boss.isDead()) return;

            // Update orb position to follow wither
            Location currentLoc = boss.getWither().getLocation().add(0, 1.5, 0);
            orb.teleport(currentLoc.clone().add(-0.25, -0.25, -0.25));

            // Scale grows from 0.3 to 2.0 over the charge duration
            float progress = (float) tick[0] / chargeTicks;
            float scale = initialScale + (2.0f - initialScale) * progress;
            orb.setTransformation(new Transformation(
                    new Vector3f(-scale / 2, -scale / 2, -scale / 2),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 1, 0)
            ));

            // Swirling dark particles around the orb
            world.spawnParticle(Particle.DUST, currentLoc, 5, 0.5, 0.5, 0.5, 0, darkPurple);
            world.spawnParticle(Particle.SMOKE, currentLoc, 3, 0.3, 0.3, 0.3, 0.02);

            // Escalating bass sound — pitch increases with progress
            if (tick[0] % 10 == 0) {
                float pitch = 0.5f + progress * 1.5f;
                world.playSound(currentLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 2.0f, pitch);
            }
        }, 1L, 1L);
        activeTasks.add(chargeTask);

        // Fire after charge completes
        BukkitTask fireTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            chargeTask.cancel();
            activeTasks.remove(chargeTask);

            if (boss.isDead()) return;

            // Remove orb
            if (orb.isValid()) {
                boss.getDisplayPool().remove(orb);
            }

            Player currentTarget = boss.getNearestPlayer(64);
            if (currentTarget == null) return;

            Location fireOrigin = boss.getWither().getLocation().add(0, 1.5, 0);
            Vector direction = currentTarget.getLocation().add(0, 1, 0).toVector()
                    .subtract(fireOrigin.toVector()).normalize().multiply(1.8);

            // Fire charged skull
            WitherSkull skull = boss.getWither().launchProjectile(WitherSkull.class, direction);
            skull.setCharged(true);
            skull.setYield(0);
            boss.getDisplayPool().track(skull);

            // Launch sound
            world.playSound(fireOrigin, Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.5f);

            // Purple trail
            Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(160, 0, 255), 2.0f);
            BukkitTask trailTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                if (!skull.isValid() || skull.isDead()) return;
                skull.getWorld().spawnParticle(Particle.DUST, skull.getLocation(), 5,
                        0.2, 0.2, 0.2, 0, purpleDust);
                skull.getWorld().spawnParticle(Particle.SMOKE, skull.getLocation(), 3,
                        0.15, 0.15, 0.15, 0.01);
            }, 1L, 1L);
            activeTasks.add(trailTask);

            // Track skull for impact
            BukkitTask impactCheck = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                if (!skull.isValid() || skull.isDead()) {
                    onImpact(boss, skull.getLocation());
                    trailTask.cancel();
                    activeTasks.remove(trailTask);
                }
            }, 1L, 2L);
            activeTasks.add(impactCheck);

            // Auto-cancel on skull death
            BukkitTask deathCheck = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
                if (!skull.isValid() || skull.isDead()) {
                    impactCheck.cancel();
                    activeTasks.remove(impactCheck);
                }
            }, 1L, 2L);
            activeTasks.add(deathCheck);

            // Timeout after 5 seconds
            BukkitTask timeout = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
                trailTask.cancel();
                impactCheck.cancel();
                deathCheck.cancel();
                activeTasks.remove(trailTask);
                activeTasks.remove(impactCheck);
                activeTasks.remove(deathCheck);
                if (skull.isValid() && !skull.isDead()) {
                    Location impactLoc = skull.getLocation();
                    skull.remove();
                    onImpact(boss, impactLoc);
                }
            }, 100L);
            activeTasks.add(timeout);

        }, chargeTicks);
        activeTasks.add(fireTask);
    }

    private void onImpact(WitherBossEntity boss, Location impact) {
        World world = impact.getWorld();

        // Purple explosion particles
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(160, 0, 255), 2.0f);
        world.spawnParticle(Particle.EXPLOSION, impact, 5, 1.0, 1.0, 1.0, 0);
        world.spawnParticle(Particle.DUST, impact, 40, 2.0, 2.0, 2.0, 0, purpleDust);
        WitherParticles.sphere(impact, 3.0, 40, Particle.SMOKE, null);

        // 45 HP damage in 5-block radius
        double radius = 5.0;
        double radiusSq = radius * radius;

        for (Player player : boss.getPlayersInRange(64)) {
            if (player.getLocation().distanceSquared(impact) <= radiusSq) {
                player.damage(45.0, boss.getWither());
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
