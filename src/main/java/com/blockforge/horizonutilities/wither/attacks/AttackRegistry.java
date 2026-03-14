package com.blockforge.horizonutilities.wither.attacks;

import com.blockforge.horizonutilities.wither.attacks.impl.aoe.*;
import com.blockforge.horizonutilities.wither.attacks.impl.debuff.*;
import com.blockforge.horizonutilities.wither.attacks.impl.movement.*;
import com.blockforge.horizonutilities.wither.attacks.impl.phase.*;
import com.blockforge.horizonutilities.wither.attacks.impl.projectile.*;
import com.blockforge.horizonutilities.wither.attacks.impl.summon.*;
import com.blockforge.horizonutilities.wither.attacks.impl.terrain.*;

/**
 * Registers all 56 Wither Sovereign attack implementations with the AttackScheduler.
 */
public final class AttackRegistry {

    private AttackRegistry() {}

    public static void registerAll(AttackScheduler scheduler) {
        // === Projectile Attacks (16) ===
        scheduler.registerAttack(new WitherBarrage());
        scheduler.registerAttack(new HomingSkull());
        scheduler.registerAttack(new ClusterBomb());
        scheduler.registerAttack(new GroundSlamSkull());
        scheduler.registerAttack(new SpiralSkulls());
        scheduler.registerAttack(new BouncingSkull());
        scheduler.registerAttack(new DelayedMine());
        scheduler.registerAttack(new SkullRain());
        scheduler.registerAttack(new PiercingSkull());
        scheduler.registerAttack(new SoulDrainSkull());
        scheduler.registerAttack(new ShotgunBlast());
        scheduler.registerAttack(new MortarStrike());
        scheduler.registerAttack(new TripleHelix());
        scheduler.registerAttack(new ChargedSkull());
        scheduler.registerAttack(new SkullWall());
        scheduler.registerAttack(new SeekingMines());

        // === AoE Attacks (9) ===
        scheduler.registerAttack(new DeathPulse());
        scheduler.registerAttack(new CorruptionZone());
        scheduler.registerAttack(new RepulsionBlast());
        scheduler.registerAttack(new WitherFog());
        scheduler.registerAttack(new NecroticAura());
        scheduler.registerAttack(new SoulScream());
        scheduler.registerAttack(new ShadowEruption());
        scheduler.registerAttack(new WitheringGeyser());
        scheduler.registerAttack(new CorpseExplosion());

        // === Summon Attacks (9) ===
        scheduler.registerAttack(new RaiseUndead());
        scheduler.registerAttack(new PhantomSwarm());
        scheduler.registerAttack(new BoneCage());
        scheduler.registerAttack(new WitherTotem());
        scheduler.registerAttack(new SkeletalShield());
        scheduler.registerAttack(new NecroHorde());
        scheduler.registerAttack(new ShadeClone());
        scheduler.registerAttack(new SoulAnchor());
        scheduler.registerAttack(new NecroticBarrier());

        // === Movement Attacks (9) ===
        scheduler.registerAttack(new DashStrike());
        scheduler.registerAttack(new TeleportStrike());
        scheduler.registerAttack(new AerialDive());
        scheduler.registerAttack(new PhaseShiftAttack());
        scheduler.registerAttack(new SpiralDescent());
        scheduler.registerAttack(new ShadowStep());
        scheduler.registerAttack(new EarthquakeSlam());
        scheduler.registerAttack(new EvasiveDash());
        scheduler.registerAttack(new VoidTether());

        // === Terrain Attacks (5) ===
        scheduler.registerAttack(new TerrainDecay());
        scheduler.registerAttack(new PillarTrap());
        scheduler.registerAttack(new WitherWeb());
        scheduler.registerAttack(new GroundFissure());
        scheduler.registerAttack(new AbyssalGrasp());

        // === Debuff Attacks (3) ===
        scheduler.registerAttack(new SoulSiphon());
        scheduler.registerAttack(new AntiHealCurse());
        scheduler.registerAttack(new SoulShackle());

        // === Phase / Ultimate Attacks (5) ===
        scheduler.registerAttack(new SkullStorm());
        scheduler.registerAttack(new DeathsEmbrace());
        scheduler.registerAttack(new ResurrectionAttack());
        scheduler.registerAttack(new Cataclysm());
        scheduler.registerAttack(new DarkMeteor());
    }
}
