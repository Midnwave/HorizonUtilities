package com.blockforge.horizonutilities.wither.attacks.impl.terrain;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import com.blockforge.horizonutilities.wither.util.WitherGriefBypass;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sprays cobwebs in a 6-block radius around a target player.
 * Dark spray particles expand outward before cobweb placement.
 * Cobwebs are placed on air blocks at ground level and 1 above.
 * Duration: 8 seconds, then cobwebs are removed.
 */
public class WitherWeb implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final List<Block> placedWebs = new ArrayList<>();

    @Override
    public String getId() {
        return "wither_web";
    }

    @Override
    public String getDisplayName() {
        return "Wither Web";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(2, 3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 14.0;
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

        Location center = target.getLocation().clone();
        World world = center.getWorld();
        int radius = 6;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Spider sound
        world.playSound(center, Sound.ENTITY_SPIDER_AMBIENT, 2.0f, 0.7f);

        // Dark spray particles outward
        Particle.DustOptions darkDust = new Particle.DustOptions(Color.fromRGB(40, 40, 50), 1.5f);
        for (int i = 0; i < 30; i++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double dist = rng.nextDouble(1.0, radius);
            Location particleLoc = center.clone().add(
                    Math.cos(angle) * dist, rng.nextDouble(0, 2), Math.sin(angle) * dist);
            world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, darkDust);
        }

        // Place cobwebs on air blocks at ground level and 1 above
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;

                // Random scatter — don't fill every block
                if (rng.nextDouble() > 0.35) continue;

                Block surface = world.getHighestBlockAt(center.clone().add(x, 0, z));
                Block aboveSurface = surface.getRelative(BlockFace.UP);
                Block twoAboveSurface = aboveSurface.getRelative(BlockFace.UP);

                // Place web at ground+1 if it's air
                if (aboveSurface.getType().isAir()) {
                    Location webLoc = aboveSurface.getLocation();
                    if (WitherGriefBypass.canModifyBlock(webLoc, boss)) {
                        aboveSurface.setType(Material.COBWEB);
                        placedWebs.add(aboveSurface);
                    }
                }

                // Place web at ground+2 if it's air (scattered, less dense)
                if (twoAboveSurface.getType().isAir() && rng.nextDouble() < 0.4) {
                    Location webLoc = twoAboveSurface.getLocation();
                    if (WitherGriefBypass.canModifyBlock(webLoc, boss)) {
                        twoAboveSurface.setType(Material.COBWEB);
                        placedWebs.add(twoAboveSurface);
                    }
                }
            }
        }

        // Remove cobwebs after 8 seconds (160 ticks)
        BukkitTask removeTask = Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            for (Block web : placedWebs) {
                if (web.getType() == Material.COBWEB) {
                    web.setType(Material.AIR);
                }
            }
            placedWebs.clear();
        }, 160L);
        activeTasks.add(removeTask);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();

        // Remove any remaining cobwebs
        for (Block web : placedWebs) {
            if (web.getType() == Material.COBWEB) {
                web.setType(Material.AIR);
            }
        }
        placedWebs.clear();
    }
}
