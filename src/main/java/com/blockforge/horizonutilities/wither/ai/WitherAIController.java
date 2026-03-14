package com.blockforge.horizonutilities.wither.ai;

import com.blockforge.horizonutilities.wither.WitherBossConfig;
import com.blockforge.horizonutilities.wither.WitherBossEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Controls the Wither Sovereign's AI: target switching, altitude management,
 * and lateral strafing around its current target.
 */
public class WitherAIController {

    private final WitherBossEntity boss;
    private final WitherBossConfig config;

    private Player currentTarget;
    private Player forcedTarget;
    private long nextTargetSwitchTick;
    private int tickCounter;
    private double strafeAngle;

    /** Max range to search for targets */
    private static final double TARGET_RANGE = 64.0;

    public WitherAIController(WitherBossEntity boss, WitherBossConfig config) {
        this.boss = boss;
        this.config = config;
        this.tickCounter = 0;
        this.strafeAngle = 0.0;
        scheduleNextTargetSwitch();
    }

    /**
     * Called every tick from the boss entity's main loop.
     * Handles target switching, altitude control, and lateral strafing.
     */
    public void tick() {
        if (boss.isDead() || !boss.getWither().isValid()) {
            return;
        }

        Wither wither = boss.getWither();
        wither.setAI(true);

        tickCounter++;

        // Check target switching every 20 ticks (1 second) to avoid expensive operations each tick
        if (tickCounter % 20 == 0) {
            updateTarget();
        }

        // If we have a target, manage movement every tick
        if (currentTarget != null && currentTarget.isOnline() && !currentTarget.isDead()) {
            wither.setTarget(currentTarget);
            manageAltitude(wither);
            manageStrafeMovement(wither);
        } else {
            // Lost target, try to find a new one immediately
            currentTarget = null;
            forcedTarget = null;
            findNewTarget();
            if (currentTarget != null) {
                wither.setTarget(currentTarget);
            }
        }
    }

    /**
     * Checks if it's time to switch targets, and if so, picks a new one.
     */
    private void updateTarget() {
        long currentTick = boss.getWither().getWorld().getGameTime();

        if (currentTick >= nextTargetSwitchTick) {
            // If we have a forced target that's still valid, keep it
            if (forcedTarget != null && forcedTarget.isOnline() && !forcedTarget.isDead()) {
                currentTarget = forcedTarget;
                forcedTarget = null;
                scheduleNextTargetSwitch();
                return;
            }

            findNewTarget();
            scheduleNextTargetSwitch();
        }
    }

    /**
     * Selects a new target: highest aggro player first, fallback to nearest player.
     */
    private void findNewTarget() {
        // Try highest aggro target first
        Player aggroTarget = boss.getHighestAggroTarget(TARGET_RANGE);
        if (aggroTarget != null) {
            currentTarget = aggroTarget;
            return;
        }

        // Fallback: nearest player
        Player nearest = boss.getNearestPlayer(TARGET_RANGE);
        if (nearest != null) {
            currentTarget = nearest;
        }
    }

    /**
     * Maintains the wither's preferred altitude above the ground.
     * Nudges the wither up or down toward the target altitude band.
     */
    private void manageAltitude(Wither wither) {
        Location witherLoc = wither.getLocation();
        Location ground = witherLoc.clone();

        // Find ground level below the wither
        int groundY = witherLoc.getBlockY();
        for (int y = witherLoc.getBlockY(); y > witherLoc.getWorld().getMinHeight(); y--) {
            ground.setY(y);
            if (ground.getBlock().getType().isSolid()) {
                groundY = y;
                break;
            }
        }

        double currentAltitude = witherLoc.getY() - groundY;
        int targetAltitudeMin = config.getAltitudeMin();
        int targetAltitudeMax = config.getAltitudeMax();

        // Nudge velocity if outside preferred altitude band
        if (currentAltitude < targetAltitudeMin) {
            Vector velocity = wither.getVelocity();
            velocity.setY(Math.max(velocity.getY(), 0.15));
            wither.setVelocity(velocity);
        } else if (currentAltitude > targetAltitudeMax) {
            Vector velocity = wither.getVelocity();
            velocity.setY(Math.min(velocity.getY(), -0.1));
            wither.setVelocity(velocity);
        }
    }

    /**
     * Strafes the wither laterally around the current target.
     */
    private void manageStrafeMovement(Wither wither) {
        if (currentTarget == null) return;

        Location witherLoc = wither.getLocation();
        Location targetLoc = currentTarget.getLocation();

        // Calculate direction from wither to target
        Vector toTarget = targetLoc.toVector().subtract(witherLoc.toVector());
        double distanceXZ = Math.sqrt(toTarget.getX() * toTarget.getX() + toTarget.getZ() * toTarget.getZ());

        // Only strafe if we're within reasonable range
        if (distanceXZ < 5.0 || distanceXZ > 40.0) {
            return;
        }

        // Increment strafe angle
        strafeAngle += config.getStrafeSpeed() * 0.05;
        if (strafeAngle > Math.PI * 2) {
            strafeAngle -= Math.PI * 2;
        }

        // Calculate lateral offset perpendicular to the target direction
        double perpX = -toTarget.getZ() / distanceXZ;
        double perpZ = toTarget.getX() / distanceXZ;

        double strafeForce = Math.sin(strafeAngle) * config.getStrafeSpeed() * 0.05;

        Vector velocity = wither.getVelocity();
        velocity.setX(velocity.getX() + perpX * strafeForce);
        velocity.setZ(velocity.getZ() + perpZ * strafeForce);
        wither.setVelocity(velocity);
    }

    /**
     * Schedules the next automatic target switch at a random interval
     * between targetSwitchMin and targetSwitchMax seconds from now.
     */
    private void scheduleNextTargetSwitch() {
        int minTicks = config.getTargetSwitchMin() * 20;
        int maxTicks = config.getTargetSwitchMax() * 20;
        int delayTicks = ThreadLocalRandom.current().nextInt(minTicks, maxTicks + 1);
        nextTargetSwitchTick = boss.getWither().getWorld().getGameTime() + delayTicks;
    }

    /**
     * Returns the current target player, or null if none.
     */
    public Player getCurrentTarget() {
        return currentTarget;
    }

    /**
     * Forces the wither to target a specific player temporarily.
     * This override lasts until the next scheduled target switch.
     */
    public void forceTarget(Player player) {
        this.forcedTarget = player;
        this.currentTarget = player;
        if (boss.getWither().isValid()) {
            boss.getWither().setTarget(player);
        }
    }
}
