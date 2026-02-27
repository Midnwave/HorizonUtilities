package com.blockforge.horizonutilities.combat;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;

import java.util.Locale;
import java.util.UUID;

public class CombatListener implements Listener {

    private final HorizonUtilitiesPlugin plugin;
    private final CombatManager manager;

    public CombatListener(HorizonUtilitiesPlugin plugin, CombatManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!manager.getConfig().isEnabled()) return;
        Player victim   = resolvePlayer(event.getEntity());
        Player attacker = resolvePlayer(event.getDamager());
        if (victim == null || attacker == null || victim.equals(attacker)) return;
        if (attacker.hasPermission("horizonutilities.combat.bypass")) return;
        manager.tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!manager.getConfig().isEnabled()) return;
        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;
        if (player.hasPermission("horizonutilities.combat.bypass")) return;

        String cmd = event.getMessage().toLowerCase(Locale.ROOT);
        // Strip args - just compare the base command
        String base = cmd.split(" ")[0];
        for (String blocked : manager.getConfig().getBlockedCommands()) {
            if (base.equals(blocked) || base.startsWith(blocked + ":")) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You can't use that command while in combat!", NamedTextColor.RED));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!manager.getConfig().isEnabled() || !manager.getConfig().isBlockEnderchest()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!manager.isInCombat(player.getUniqueId())) return;
        if (player.hasPermission("horizonutilities.combat.bypass")) return;
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You can't open your Ender Chest while in combat!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (!manager.getConfig().isEnabled() || !manager.getConfig().isBlockFly()) return;
        Player player = event.getPlayer();
        if (!event.isFlying()) return;
        if (!manager.isInCombat(player.getUniqueId())) return;
        if (player.hasPermission("horizonutilities.combat.bypass")) return;
        event.setCancelled(true);
        player.setFlying(false);
        player.sendMessage(Component.text("You can't fly while in combat!", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!manager.getConfig().isEnabled() || !manager.getConfig().isBlockTeleport()) return;
        Player player = event.getPlayer();
        if (!manager.isInCombat(player.getUniqueId())) return;
        if (player.hasPermission("horizonutilities.combat.bypass")) return;

        switch (event.getCause()) {
            case PLUGIN, COMMAND, UNKNOWN -> {
                event.setCancelled(true);
                player.sendMessage(Component.text("You can't teleport while in combat!", NamedTextColor.RED));
            }
            default -> {} // Allow ENDER_PEARL, death, etc.
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!manager.isInCombat(uuid)) return;

        if (manager.getConfig().isKillOnLogout()) {
            // Drop items if configured
            if (manager.getConfig().isDropItems()) {
                for (var item : player.getInventory().getContents()) {
                    if (item != null) player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                player.getInventory().clear();
            }
            if (manager.getConfig().isDropXp()) {
                player.getWorld().spawn(player.getLocation(), org.bukkit.entity.ExperienceOrb.class,
                        orb -> orb.setExperience(player.getTotalExperience()));
                player.setTotalExperience(0);
            }
            player.getWorld().getPlayers().forEach(p ->
                    p.sendMessage(Component.text(player.getName(), NamedTextColor.RED)
                            .append(Component.text(" combat-logged and lost their items!", NamedTextColor.GRAY))));
        }
        manager.untag(uuid);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.untag(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        manager.untag(event.getPlayer().getUniqueId());
    }

    private Player resolvePlayer(Entity entity) {
        return entity instanceof Player p ? p : null;
    }
}
