package com.blockforge.horizonutilities.wither;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages boss music playback for the Wither Sovereign fight via
 * resource pack custom sounds. Handles looping, phase-based pitch
 * changes, and victory music.
 */
public class WitherMusicManager {

    private final HorizonUtilitiesPlugin plugin;
    private final WitherBossConfig config;

    /**
     * Active music loop tasks keyed by boss fight ID.
     */
    private final Map<UUID, MusicState> activeMusicTasks = new ConcurrentHashMap<>();

    public WitherMusicManager(HorizonUtilitiesPlugin plugin, WitherBossConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Starts the boss fight music for a given boss entity.
     * Plays the configured custom sound to all players within the music radius.
     * The sound is replayed every 200 ticks (~10 seconds) to simulate looping,
     * since resource pack sounds do not natively loop.
     *
     * @param boss the boss entity to start music for
     */
    public void startMusic(WitherBossEntity boss) {
        if (!config.isMusicEnabled()) return;
        if (activeMusicTasks.containsKey(boss.getFightId())) return;

        String soundKeyStr = config.getMusicSoundKey();
        Key soundKey = Key.key(soundKeyStr);
        int radius = config.getMusicRadius();

        // Play immediately to all nearby players
        playToNearbyPlayers(boss, soundKey, 1.0f, radius);

        // Start a repeating task to re-trigger the sound every 200 ticks (10 seconds)
        // for pseudo-looping behavior with resource pack sounds
        BukkitTask loopTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (boss.isDead() || !boss.getWither().isValid()) {
                stopMusic(boss);
                return;
            }

            MusicState state = activeMusicTasks.get(boss.getFightId());
            float pitch = state != null ? state.currentPitch : 1.0f;

            playToNearbyPlayers(boss, soundKey, pitch, radius);
        }, 200L, 200L);

        activeMusicTasks.put(boss.getFightId(), new MusicState(loopTask, 1.0f));
    }

    /**
     * Stops the boss fight music for a given boss entity.
     * Cancels the looping task and silences the sound for all nearby players.
     * Optionally plays a victory sound if configured.
     *
     * @param boss the boss entity to stop music for
     */
    public void stopMusic(WitherBossEntity boss) {
        MusicState state = activeMusicTasks.remove(boss.getFightId());
        if (state == null) return;

        // Cancel the repeating loop task
        if (state.loopTask != null && !state.loopTask.isCancelled()) {
            state.loopTask.cancel();
        }

        String soundKeyStr = config.getMusicSoundKey();
        Key soundKey = Key.key(soundKeyStr);
        int radius = config.getMusicRadius();

        // Stop the boss music for all nearby players
        Location bossLoc = boss.getWither().getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(bossLoc.getWorld())) continue;
            if (player.getLocation().distanceSquared(bossLoc) > (double) radius * radius) continue;

            player.stopSound(Sound.sound(soundKey, Sound.Source.MUSIC, 1.0f, 1.0f));
        }

        // Play victory sound if configured
        String victorySoundStr = config.getVictorySoundKey();
        if (victorySoundStr != null && !victorySoundStr.isEmpty()) {
            Key victoryKey = Key.key(victorySoundStr);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().equals(bossLoc.getWorld())) continue;
                if (player.getLocation().distanceSquared(bossLoc) > (double) radius * radius) continue;

                player.playSound(Sound.sound(victoryKey, Sound.Source.MUSIC, 1.0f, 1.0f));
            }
        }
    }

    /**
     * Called when the boss transitions to a new phase.
     * Adjusts the music pitch to increase intensity:
     * Phase 1: 1.0, Phase 2: 1.1, Phase 3: 1.2, Phase 4: 1.3
     *
     * @param boss     the boss entity
     * @param newPhase the new phase number (1-4)
     */
    public void onPhaseChange(WitherBossEntity boss, int newPhase) {
        MusicState state = activeMusicTasks.get(boss.getFightId());
        if (state == null) return;

        float pitch = switch (newPhase) {
            case 1 -> 1.0f;
            case 2 -> 1.1f;
            case 3 -> 1.2f;
            case 4 -> 1.3f;
            default -> 1.0f;
        };
        state.currentPitch = pitch;

        // Restart the sound immediately with the new pitch so the transition feels responsive
        String soundKeyStr = config.getMusicSoundKey();
        Key soundKey = Key.key(soundKeyStr);
        int radius = config.getMusicRadius();

        // Stop the old pitch sound first, then play with new pitch
        Location bossLoc = boss.getWither().getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(bossLoc.getWorld())) continue;
            if (player.getLocation().distanceSquared(bossLoc) > (double) radius * radius) continue;

            player.stopSound(Sound.sound(soundKey, Sound.Source.MUSIC, 1.0f, state.currentPitch));
        }

        playToNearbyPlayers(boss, soundKey, pitch, radius);
    }

    /**
     * Shuts down all active music tasks. Called on plugin disable.
     */
    public void shutdown() {
        for (MusicState state : activeMusicTasks.values()) {
            if (state.loopTask != null && !state.loopTask.isCancelled()) {
                state.loopTask.cancel();
            }
        }
        activeMusicTasks.clear();
    }

    /**
     * Plays the boss music sound to all players within the configured radius of the boss.
     */
    private void playToNearbyPlayers(WitherBossEntity boss, Key soundKey, float pitch, int radius) {
        if (!boss.getWither().isValid()) return;

        Location bossLoc = boss.getWither().getLocation();
        double radiusSq = (double) radius * radius;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(bossLoc.getWorld())) continue;
            if (player.getLocation().distanceSquared(bossLoc) > radiusSq) continue;

            player.playSound(Sound.sound(soundKey, Sound.Source.MUSIC, 1.0f, pitch));
        }
    }

    /**
     * Internal state for an active music loop, tracking the scheduler task
     * and the current pitch (which changes with phase transitions).
     */
    private static class MusicState {
        BukkitTask loopTask;
        float currentPitch;

        MusicState(BukkitTask loopTask, float currentPitch) {
            this.loopTask = loopTask;
            this.currentPitch = currentPitch;
        }
    }
}
