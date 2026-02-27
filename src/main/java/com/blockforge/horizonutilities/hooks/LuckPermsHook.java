package com.blockforge.horizonutilities.hooks;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Provides access to LuckPerms prefix/suffix data.
 * Loaded as a soft-dependency; check {@link #isAvailable()} before calling other methods.
 */
public class LuckPermsHook {

    private LuckPerms luckPerms;

    /**
     * Attempts to hook into LuckPerms via the Bukkit services manager.
     *
     * @return true if LuckPerms was found and hooked successfully
     */
    public boolean setup() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            return true;
        }
        return false;
    }

    public boolean isAvailable() {
        return luckPerms != null;
    }

    /**
     * Returns the player's LuckPerms prefix (legacy-formatted), or empty string.
     */
    public String getPrefix(Player player) {
        if (luckPerms == null) return "";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        CachedMetaData meta = user.getCachedData().getMetaData();
        String prefix = meta.getPrefix();
        return prefix != null ? prefix : "";
    }

    /**
     * Returns the player's LuckPerms suffix (legacy-formatted), or empty string.
     */
    public String getSuffix(Player player) {
        if (luckPerms == null) return "";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        CachedMetaData meta = user.getCachedData().getMetaData();
        String suffix = meta.getSuffix();
        return suffix != null ? suffix : "";
    }

    /**
     * Returns the player's primary group name, or "default".
     */
    public String getPrimaryGroup(Player player) {
        if (luckPerms == null) return "default";
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        return user.getPrimaryGroup();
    }
}
