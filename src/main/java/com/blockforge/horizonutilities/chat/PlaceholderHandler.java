package com.blockforge.horizonutilities.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface PlaceholderHandler {

    String getToken();

    String getPermission();

    Component resolve(Player player);
}
