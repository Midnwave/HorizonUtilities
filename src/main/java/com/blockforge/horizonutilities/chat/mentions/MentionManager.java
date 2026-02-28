package com.blockforge.horizonutilities.chat.mentions;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.config.ChatPlaceholdersConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class MentionManager {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w{3,16})");

    private final HorizonUtilitiesPlugin plugin;

    public MentionManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Process a chat message for @mentions. Returns the modified message component
     * and notifies any mentioned players with sound + subtitle.
     */
    public Component process(Player sender, Component message) {
        ChatPlaceholdersConfig cfg = plugin.getChatPlaceholdersConfig();
        if (!cfg.isMentionsEnabled()) return message;

        TextColor highlightColor = resolveColor(cfg.getMentionHighlightColor());
        Sound sound = resolveSound(cfg.getMentionSound());

        Set<Player> mentioned = new HashSet<>();

        // Replace @name patterns in the message
        Component result = message.replaceText(TextReplacementConfig.builder()
                .match(MENTION_PATTERN)
                .replacement((match, builder) -> {
                    String name = match.group(1);
                    Player target = Bukkit.getPlayerExact(name);
                    if (target != null && target.isOnline() && !target.equals(sender)) {
                        mentioned.add(target);
                        return Component.text("@" + target.getName(), highlightColor);
                    }
                    // Not a valid online player — leave as-is
                    return Component.text(match.group());
                })
                .build());

        // Notify each mentioned player
        for (Player target : mentioned) {
            if (sound != null) {
                target.playSound(target.getLocation(), sound, 1.0f, 1.0f);
            }

            String subtitleMsg = plugin.getMessagesManager().getRaw("mention-subtitle");
            if (subtitleMsg != null) {
                subtitleMsg = subtitleMsg.replace("<player>", sender.getName());
                Component subtitleComp = plugin.getMessagesManager().getMiniMessage().deserialize(subtitleMsg);
                target.showTitle(Title.title(
                        Component.empty(),
                        subtitleComp,
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
            }
        }

        return result;
    }

    /**
     * Adds @completions for all online players to the given player.
     */
    public void addCompletions(Player player) {
        List<String> completions = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .map(p -> "@" + p.getName())
                .toList();
        if (!completions.isEmpty()) {
            player.addCustomChatCompletions(completions);
        }
    }

    /**
     * Removes all @completions from a player (used on quit cleanup).
     */
    public void removeCompletions(Player player) {
        List<String> completions = Bukkit.getOnlinePlayers().stream()
                .map(p -> "@" + p.getName())
                .toList();
        if (!completions.isEmpty()) {
            player.removeCustomChatCompletions(completions);
        }
    }

    /**
     * Broadcast a new @completion to all online players (when someone joins).
     */
    public void broadcastAddCompletion(Player joined) {
        List<String> completion = List.of("@" + joined.getName());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(joined)) {
                online.addCustomChatCompletions(completion);
            }
        }
    }

    /**
     * Remove a @completion from all online players (when someone quits).
     */
    public void broadcastRemoveCompletion(Player quitter) {
        List<String> completion = List.of("@" + quitter.getName());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(quitter)) {
                online.removeCustomChatCompletions(completion);
            }
        }
    }

    private TextColor resolveColor(String colorName) {
        if (colorName == null || colorName.isBlank()) return NamedTextColor.GOLD;
        NamedTextColor named = NamedTextColor.NAMES.value(colorName.toLowerCase());
        if (named != null) return named;
        try {
            return TextColor.fromHexString(colorName);
        } catch (Exception e) {
            return NamedTextColor.GOLD;
        }
    }

    private Sound resolveSound(String soundName) {
        if (soundName == null || soundName.isBlank()) return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Mentions] Unknown sound: " + soundName + ", falling back to ENTITY_EXPERIENCE_ORB_PICKUP");
            return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
    }
}
