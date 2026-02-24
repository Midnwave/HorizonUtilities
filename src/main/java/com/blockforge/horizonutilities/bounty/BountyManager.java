package com.blockforge.horizonutilities.bounty;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.economy.EconomyAuditLog;
import com.blockforge.horizonutilities.hooks.GPFRHook;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central manager for the bounty system.
 * Handles placement, claiming, caching, and expiry of bounties.
 */
public class BountyManager {

    private final HorizonUtilitiesPlugin plugin;
    private final BountyConfig bountyConfig;
    private final GPFRHook gpfrHook;
    private final BountyStorageManager storage;
    private final EconomyAuditLog auditLog;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /** Cache: target UUID -> list of ACTIVE bounties on that target */
    private final Map<UUID, List<Bounty>> cachedBounties = new HashMap<>();

    public BountyManager(HorizonUtilitiesPlugin plugin, BountyConfig bountyConfig, GPFRHook gpfrHook) {
        this.plugin       = plugin;
        this.bountyConfig = bountyConfig;
        this.gpfrHook     = gpfrHook;
        this.storage      = new BountyStorageManager(plugin);
        this.auditLog     = new EconomyAuditLog(plugin);
    }

    // -------------------------------------------------------------------------
    // Place bounty
    // -------------------------------------------------------------------------

    /**
     * Attempts to place a bounty on the target.
     * Returns true on success, false if any validation fails.
     */
    public boolean placeBounty(Player setter, OfflinePlayer target, double amount, boolean anonymous) {
        if (!bountyConfig.isEnabled()) {
            plugin.getMessagesManager().send(setter, "bounty-disabled");
            return false;
        }
        if (!setter.hasPermission("horizonutilities.bounty.set")) {
            plugin.getMessagesManager().send(setter, "no-permission");
            return false;
        }
        if (anonymous && !setter.hasPermission("horizonutilities.bounty.anonymous")) {
            plugin.getMessagesManager().send(setter, "no-permission");
            return false;
        }
        if (target.getUniqueId().equals(setter.getUniqueId())) {
            plugin.getMessagesManager().send(setter, "bounty-self");
            return false;
        }
        if (amount < bountyConfig.getMinAmount() || amount > bountyConfig.getMaxAmount()) {
            plugin.getMessagesManager().send(setter, "bounty-invalid-amount",
                    Placeholder.unparsed("min", plugin.getVaultHook().format(bountyConfig.getMinAmount())),
                    Placeholder.unparsed("max", plugin.getVaultHook().format(bountyConfig.getMaxAmount())));
            return false;
        }
        if (storage.getCountOnTarget(target.getUniqueId()) >= bountyConfig.getMaxBountiesPerTarget()) {
            plugin.getMessagesManager().send(setter, "bounty-target-max-reached");
            return false;
        }

        double cost = anonymous ? amount * bountyConfig.getAnonymousCostMultiplier() : amount;

        if (!plugin.getVaultHook().has(setter, cost)) {
            plugin.getMessagesManager().send(setter, "bounty-insufficient-funds",
                    Placeholder.unparsed("cost", plugin.getVaultHook().format(cost)));
            return false;
        }

        // Withdraw money
        plugin.getVaultHook().withdraw(setter, cost);

        long now      = System.currentTimeMillis();
        long expiresAt = bountyConfig.computeExpiresAt();

        Bounty bounty = new Bounty(
                target.getUniqueId(),
                target.getName() != null ? target.getName() : target.getUniqueId().toString(),
                setter.getUniqueId(),
                setter.getName(),
                amount,
                anonymous,
                now,
                expiresAt
        );

        int id = storage.placeBounty(bounty);
        if (id == -1) {
            // DB insert failed — refund
            plugin.getVaultHook().deposit(setter, cost);
            plugin.getMessagesManager().send(setter, "bounty-error");
            return false;
        }
        bounty.setId(id);

        // Invalidate cache
        cachedBounties.remove(target.getUniqueId());

        // Audit log
        double balAfter = plugin.getVaultHook().getBalance(setter);
        auditLog.log(setter.getUniqueId(), setter.getName(), EconomyAuditLog.BOUNTY_SET,
                -cost, balAfter, "bounty", target.getUniqueId());

        // Notify setter
        plugin.getMessagesManager().send(setter, "bounty-placed",
                Placeholder.unparsed("target", bounty.getTargetName()),
                Placeholder.unparsed("amount", plugin.getVaultHook().format(amount)));

        // Broadcast
        if (bountyConfig.isBroadcastNewBounty()) {
            String setter_display = anonymous ? "Anonymous" : setter.getName();
            plugin.getServer().broadcast(mm.deserialize(
                    "<gold><bold>[Bounty]</bold></gold> <yellow>A bounty of " +
                    plugin.getVaultHook().format(amount) + " has been placed on <red>" +
                    bounty.getTargetName() + "</red> by " + setter_display + "!</yellow>"));
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Claim bounties
    // -------------------------------------------------------------------------

    /**
     * Called when a player kills another. Pays out all active bounties on the victim
     * if the kill location allows bounty hunting.
     */
    public void claimBounties(Player killer, Player victim) {
        Location killLocation = victim.getLocation();
        if (!gpfrHook.isBountyHuntingAllowed(killLocation)) return;

        List<Bounty> active = getActiveBounties(victim.getUniqueId());
        // Filter truly active (not expired in memory)
        active.removeIf(b -> !b.isActive());
        if (active.isEmpty()) return;

        double total = Bounty.getTotalValue(active);

        // Pay the killer
        plugin.getVaultHook().deposit(killer, total);

        // Mark as claimed in DB
        storage.claimBounties(victim.getUniqueId(), killer.getUniqueId(), killer.getName());

        // Clear cache
        cachedBounties.remove(victim.getUniqueId());

        // Audit log
        double killerBal = plugin.getVaultHook().getBalance(killer);
        auditLog.log(killer.getUniqueId(), killer.getName(), EconomyAuditLog.BOUNTY_CLAIM,
                total, killerBal, "bounty-claim", victim.getUniqueId());

        // Notify killer
        plugin.getMessagesManager().send(killer, "bounty-claimed",
                Placeholder.unparsed("target", victim.getName()),
                Placeholder.unparsed("amount", plugin.getVaultHook().format(total)));

        // Broadcast
        if (bountyConfig.isBroadcastClaim()) {
            plugin.getServer().broadcast(mm.deserialize(
                    "<gold><bold>[Bounty]</bold></gold> <red>" + killer.getName() +
                    "</red> <yellow>has claimed a bounty of " + plugin.getVaultHook().format(total) +
                    " on <red>" + victim.getName() + "</red>!</yellow>"));
        }
    }

    // -------------------------------------------------------------------------
    // Cache / query
    // -------------------------------------------------------------------------

    /** Returns active bounties on a target, using the cache when available. */
    public List<Bounty> getActiveBounties(UUID targetUuid) {
        if (cachedBounties.containsKey(targetUuid)) {
            return cachedBounties.get(targetUuid);
        }
        List<Bounty> bounties = storage.getActiveBountiesOnTarget(targetUuid);
        cachedBounties.put(targetUuid, bounties);
        return bounties;
    }

    /** Returns the total bounty value on a target. */
    public double getTotalBountyValue(UUID targetUuid) {
        return Bounty.getTotalValue(getActiveBounties(targetUuid));
    }

    /** Loads all active bounties into the cache. */
    public void loadCache() {
        cachedBounties.clear();
        List<Bounty> all = storage.getAllActiveBounties();
        for (Bounty b : all) {
            cachedBounties.computeIfAbsent(b.getTargetUuid(), k -> new ArrayList<>()).add(b);
        }
    }

    /** Expires old bounties in the DB and clears them from cache. */
    public void expireOldBounties() {
        storage.expireOldBounties();
        // Re-load cache to remove expired entries
        loadCache();
    }

    /** Returns the cached bounties map (target UUID -> list). */
    public Map<UUID, List<Bounty>> getCachedBounties() {
        return cachedBounties;
    }

    public BountyStorageManager getStorage()  { return storage; }
    public BountyConfig getBountyConfig()     { return bountyConfig; }
}
