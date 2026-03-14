package com.blockforge.horizonutilities.wither.attacks.impl.debuff;

import com.blockforge.horizonutilities.wither.WitherBossEntity;
import com.blockforge.horizonutilities.wither.attacks.WitherAttack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Curses nearby players so that healing damages them instead for 5 seconds.
 * Tracks each player's health every tick; if health increases, the increase
 * is reversed as damage. Skull particle aura + red dust around cursed players.
 */
public class AntiHealCurse implements WitherAttack {

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    @Override
    public String getId() {
        return "anti_heal_curse";
    }

    @Override
    public String getDisplayName() {
        return "Anti-Heal Curse";
    }

    @Override
    public Set<Integer> getActivePhases() {
        return Set.of(3, 4);
    }

    @Override
    public double getCooldownSeconds() {
        return 22.0;
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
        List<Player> targets = boss.getPlayersInRange(15);
        if (targets.isEmpty()) return;

        boss.getWither().getWorld().playSound(boss.getWither().getLocation(),
                Sound.ENTITY_WITHER_HURT, 2.0f, 0.6f);

        // Track health of each cursed player
        Map<UUID, Double> lastHealth = new HashMap<>();
        Set<UUID> cursedPlayers = new HashSet<>();

        for (Player player : targets) {
            cursedPlayers.add(player.getUniqueId());
            lastHealth.put(player.getUniqueId(), player.getHealth());

            // Warning message
            player.sendActionBar(Component.text("Your healing is cursed!", NamedTextColor.DARK_RED));
        }

        int durationTicks = 100; // 5 seconds
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.0f);

        BukkitTask curseTask = Bukkit.getScheduler().runTaskTimer(boss.getPlugin(), () -> {
            for (UUID playerId : cursedPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || player.isDead()) continue;

                double previousHealth = lastHealth.getOrDefault(playerId, player.getHealth());
                double currentHealth = player.getHealth();

                // If player healed, reverse it as damage
                if (currentHealth > previousHealth) {
                    double healAmount = currentHealth - previousHealth;
                    player.damage(healAmount * 2.0, boss.getWither());
                }

                lastHealth.put(playerId, player.getHealth());

                // Red dust particles around cursed players
                player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.5, 0),
                        5, 0.4, 0.5, 0.4, 0, redDust);
                if (player.getWorld().getGameTime() % 10 == 0) {
                    player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 2, 0),
                            2, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }, 0L, 1L);
        activeTasks.add(curseTask);

        // Cancel after duration
        Bukkit.getScheduler().runTaskLater(boss.getPlugin(), () -> {
            curseTask.cancel();
            activeTasks.remove(curseTask);

            // Notify players the curse ended
            for (UUID playerId : cursedPlayers) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendActionBar(Component.text("The curse has lifted.", NamedTextColor.GREEN));
                }
            }
        }, durationTicks);
    }

    @Override
    public void cleanup() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
