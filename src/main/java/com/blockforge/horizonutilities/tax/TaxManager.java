package com.blockforge.horizonutilities.tax;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.economy.EconomyAuditLog;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles depositing job tax amounts to the claim owner when a player
 * earns job income inside someone else's GP claim.
 *
 * The deduction from the earner is handled in JobManager.processAction().
 * This class is responsible only for the deposit side.
 */
public class TaxManager {

    private final HorizonUtilitiesPlugin plugin;

    public TaxManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Deposits the tax amount to the GriefPrevention claim owner at the given location.
     * Does nothing if:
     * - GriefPrevention is not loaded
     * - There is no claim at the location
     * - The claim owner is the same as the earner (own-claim tax disabled by nature)
     * - Vault is not available
     *
     * @param location   where the earning happened
     * @param taxAmount  amount to deposit to the claim owner
     * @param earner     the player who earned the income (to skip self-owned claims)
     * @param jobId      job ID for audit logging
     */
    public void depositTaxToClaimOwner(Location location, double taxAmount, Player earner, String jobId) {
        if (taxAmount <= 0) return;
        if (!plugin.getVaultHook().isAvailable()) return;

        try {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            if (claim == null) return;

            UUID ownerUuid = claim.ownerID;
            if (ownerUuid == null) return;

            // Don't tax earnings in your own claim
            if (ownerUuid.equals(earner.getUniqueId())) return;

            // Deposit to claim owner (works for offline players via Vault)
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                    if (ownerName == null) ownerName = ownerUuid.toString();

                    plugin.getVaultHook().depositOffline(ownerUuid, ownerName, taxAmount);

                    // Audit log
                    EconomyAuditLog auditLog = new EconomyAuditLog(plugin);
                    auditLog.log(ownerUuid, ownerName,
                            EconomyAuditLog.TAX_RECEIVED, taxAmount, null,
                            "job-tax:" + jobId + ":from:" + earner.getName(),
                            earner.getUniqueId());

                    // Notify if online + configured
                    if (plugin.getJobManager() != null
                            && plugin.getJobManager().getConfig().isTaxNotifyOwner()) {
                        Player ownerPlayer = Bukkit.getPlayer(ownerUuid);
                        if (ownerPlayer != null && ownerPlayer.isOnline()) {
                            ownerPlayer.sendMessage(
                                Component.text("[Jobs] Tax received: ", NamedTextColor.GREEN)
                                    .append(Component.text(
                                        plugin.getVaultHook().format(taxAmount),
                                        NamedTextColor.GOLD))
                                    .append(Component.text(
                                        " from " + earner.getName() + " (" + jobId + ")",
                                        NamedTextColor.GRAY)));
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to deposit tax to claim owner", e);
                }
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "TaxManager: failed to resolve claim at " + location, e);
        }
    }
}
