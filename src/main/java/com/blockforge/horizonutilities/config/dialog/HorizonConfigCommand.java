package com.blockforge.horizonutilities.config.dialog;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.dialog.DialogUtil;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /horizonconfig [section] [set <key> <value>]
 *
 * Provides a clickable in-chat config editor. Each value is shown with
 * a click-to-edit suggestion. Changes are written back to the YAML file
 * and the relevant config object is reloaded.
 */
public class HorizonConfigCommand implements CommandExecutor, TabCompleter {

    private final HorizonUtilitiesPlugin plugin;

    /** Maps section name -> relative path of the YAML file */
    private static final Map<String, String> SECTIONS = new LinkedHashMap<>();
    static {
        SECTIONS.put("jobs",          "jobs-config.yml");
        SECTIONS.put("auction",       "auction-house.yml");
        SECTIONS.put("chatgames",     "chatgames.yml");
        SECTIONS.put("chat",          "chat-placeholders.yml");
        SECTIONS.put("chatbubbles",   "chat-bubbles.yml");
        SECTIONS.put("bounty",        "bounty.yml");
        SECTIONS.put("lottery",       "lottery.yml");
        SECTIONS.put("trade",         "trade.yml");
        SECTIONS.put("blackmarket",   "blackmarket.yml");
        SECTIONS.put("warps",         "admin-warps.yml");
        SECTIONS.put("playerwarps",   "player-warps.yml");
        SECTIONS.put("combat",        "combat.yml");
        SECTIONS.put("customitems",   "custom-items.yml");
        SECTIONS.put("tournaments",   "tournaments.yml");
        SECTIONS.put("crafting",      "crafting-tables.yml");
        SECTIONS.put("maintenance",   "maintenance.yml");
    }

    public HorizonConfigCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("horizonutilities.config.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        // /horizonconfig set <section> <key> <value...>
        if (args.length >= 4 && args[0].equalsIgnoreCase("set")) {
            handleSet(sender, args);
            return true;
        }

        // /horizonconfig edit <section> <key> — opens dialog for editing
        if (args.length == 3 && args[0].equalsIgnoreCase("edit") && sender instanceof Player player) {
            handleEdit(player, args[1].toLowerCase(Locale.ROOT), args[2]);
            return true;
        }

        // /horizonconfig <section>
        if (args.length >= 1 && !args[0].equalsIgnoreCase("set") && !args[0].equalsIgnoreCase("edit")) {
            String section = args[0].toLowerCase(Locale.ROOT);
            showSection(sender, section);
            return true;
        }

        // /horizonconfig — show menu
        showMenu(sender);
        return true;
    }

    // -------------------------------------------------------------------------
    // Menu display
    // -------------------------------------------------------------------------

    private void showMenu(CommandSender sender) {
        sender.sendMessage(Component.text("━━━ HorizonUtilities Config ━━━", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("Click a section to edit:", NamedTextColor.GRAY));
        for (String section : SECTIONS.keySet()) {
            sender.sendMessage(
                    Component.text("  [", NamedTextColor.DARK_GRAY)
                    .append(Component.text(section, NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.runCommand("/horizonconfig " + section))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to edit " + section, NamedTextColor.YELLOW))))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY)));
        }
    }

    private void showSection(CommandSender sender, String section) {
        String fileName = SECTIONS.get(section);
        if (fileName == null) {
            sender.sendMessage(Component.text("Unknown section: " + section +
                    ". Valid: " + String.join(", ", SECTIONS.keySet()), NamedTextColor.RED));
            return;
        }

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            sender.sendMessage(Component.text("Config file not found: " + fileName, NamedTextColor.RED));
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        sender.sendMessage(Component.text("━━━ " + section + " (" + fileName + ") ━━━", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("Click a value to edit it.", NamedTextColor.GRAY));

        // Show all leaf keys
        boolean isPlayer = sender instanceof Player;
        for (String key : getAllLeafKeys(cfg, "")) {
            Object val = cfg.get(key);
            if (val == null || val instanceof org.bukkit.configuration.ConfigurationSection) continue;
            String valStr = val.toString();

            // Players get a dialog edit; console falls back to chat suggestion
            ClickEvent click = isPlayer
                    ? ClickEvent.runCommand("/horizonconfig edit " + section + " " + key)
                    : ClickEvent.suggestCommand("/horizonconfig set " + section + " " + key + " " + valStr);
            sender.sendMessage(
                    Component.text("  " + key + ": ", NamedTextColor.YELLOW)
                    .append(Component.text(valStr, NamedTextColor.WHITE)
                            .clickEvent(click)
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Click to edit\n", NamedTextColor.GRAY)
                                    .append(Component.text(key, NamedTextColor.AQUA))))));
        }
        sender.sendMessage(Component.text("Usage: /horizonconfig set " + section + " <key> <value>",
                NamedTextColor.DARK_GRAY));
    }

    private List<String> getAllLeafKeys(FileConfiguration cfg, String prefix) {
        List<String> keys = new ArrayList<>();
        var section = prefix.isEmpty() ? cfg : cfg.getConfigurationSection(prefix);
        if (section == null) return keys;
        for (String key : section.getKeys(false)) {
            String full = prefix.isEmpty() ? key : prefix + "." + key;
            Object val = cfg.get(full);
            if (val instanceof org.bukkit.configuration.ConfigurationSection || val instanceof Map) {
                keys.addAll(getAllLeafKeys(cfg, full));
            } else {
                keys.add(full);
            }
        }
        return keys;
    }

    // -------------------------------------------------------------------------
    // Dialog-based editing
    // -------------------------------------------------------------------------

    private void handleEdit(Player player, String section, String key) {
        String fileName = SECTIONS.get(section);
        if (fileName == null) {
            player.sendMessage(Component.text("Unknown section: " + section, NamedTextColor.RED));
            return;
        }

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            player.sendMessage(Component.text("Config file not found: " + fileName, NamedTextColor.RED));
            return;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Object current = cfg.get(key);
        if (current == null || current instanceof org.bukkit.configuration.ConfigurationSection) {
            player.sendMessage(Component.text("Key not found: " + key, NamedTextColor.RED));
            return;
        }

        String currentVal = current.toString();

        // For boolean values, use a simple confirmation toggle instead of text input
        if (current instanceof Boolean boolVal) {
            boolean newVal = !boolVal;
            DialogUtil.showConfirmation(player,
                    Component.text("Edit: " + key),
                    Component.text("Current value: " + currentVal + "\nSet to: " + newVal + "?"),
                    Component.text("Set to " + newVal),
                    Component.text("Cancel"),
                    () -> applyConfigValue(player, section, fileName, key, current, String.valueOf(newVal))
            );
            return;
        }

        // For other types, use text input dialog
        try {
            DialogInput valueInput = DialogInput.text("config_value", Component.text(key))
                    .width(300)
                    .initial(currentVal)
                    .maxLength(200)
                    .build();

            DialogUtil.showConfirmationWithInput(player,
                    Component.text("Edit: " + section + "." + key),
                    Component.text("Current: " + currentVal + "\nType: " + current.getClass().getSimpleName()),
                    List.of(valueInput),
                    Component.text("Save"),
                    Component.text("Cancel"),
                    (response) -> {
                        String newVal = response.getText("config_value");
                        if (newVal != null && !newVal.trim().isEmpty()) {
                            applyConfigValue(player, section, fileName, key, current, newVal.trim());
                        }
                    }
            );
        } catch (Exception e) {
            // Dialog API not available (Bedrock client / old version) — fall back to chat suggestion
            String suggestCmd = "/horizonconfig set " + section + " " + key + " " + currentVal;
            player.sendMessage(Component.text("Edit ", NamedTextColor.GOLD)
                    .append(Component.text(key, NamedTextColor.YELLOW))
                    .append(Component.text(" — click to edit in chat:", NamedTextColor.GRAY)));
            player.sendMessage(Component.text(suggestCmd, NamedTextColor.WHITE)
                    .clickEvent(ClickEvent.suggestCommand(suggestCmd))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to edit"))));
        }
    }

    private void applyConfigValue(Player player, String section, String fileName,
                                   String key, Object current, String rawValue) {
        File file = new File(plugin.getDataFolder(), fileName);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Object parsed = parseValue(current, rawValue);
        cfg.set(key, parsed);
        try {
            cfg.save(file);
            plugin.reloadAllConfigs();
            player.sendMessage(Component.text("Set ", NamedTextColor.GREEN)
                    .append(Component.text(section + "." + key, NamedTextColor.YELLOW))
                    .append(Component.text(" = ", NamedTextColor.GRAY))
                    .append(Component.text(parsed.toString(), NamedTextColor.WHITE))
                    .append(Component.text(" (saved + reloaded)", NamedTextColor.DARK_GRAY)));
        } catch (IOException e) {
            player.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Set value (chat-based fallback)
    // -------------------------------------------------------------------------

    private void handleSet(CommandSender sender, String[] args) {
        // args: set <section> <key> <value...>
        String section = args[1].toLowerCase(Locale.ROOT);
        String key     = args[2];
        String value   = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        String fileName = SECTIONS.get(section);
        if (fileName == null) {
            sender.sendMessage(Component.text("Unknown section: " + section, NamedTextColor.RED));
            return;
        }

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            sender.sendMessage(Component.text("Config file not found: " + fileName, NamedTextColor.RED));
            return;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Object current = cfg.get(key);
        if (current == null) {
            sender.sendMessage(Component.text("Key not found: " + key, NamedTextColor.RED));
            return;
        }

        // Parse to matching type
        Object parsed = parseValue(current, value);
        cfg.set(key, parsed);

        try {
            cfg.save(file);
            plugin.reloadAllConfigs();
            sender.sendMessage(Component.text("Set ", NamedTextColor.GREEN)
                    .append(Component.text(section + "." + key, NamedTextColor.YELLOW))
                    .append(Component.text(" = ", NamedTextColor.GRAY))
                    .append(Component.text(parsed.toString(), NamedTextColor.WHITE))
                    .append(Component.text(" (saved + reloaded)", NamedTextColor.DARK_GRAY)));
        } catch (IOException e) {
            sender.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private Object parseValue(Object current, String raw) {
        if (current instanceof Boolean) {
            return raw.equalsIgnoreCase("true") || raw.equals("1");
        }
        if (current instanceof Integer) {
            try { return Integer.parseInt(raw); } catch (Exception e) { return current; }
        }
        if (current instanceof Double || current instanceof Float) {
            try { return Double.parseDouble(raw); } catch (Exception e) { return current; }
        }
        if (current instanceof Long) {
            try { return Long.parseLong(raw); } catch (Exception e) { return current; }
        }
        return raw; // String fallback
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("set", "edit"));
            opts.addAll(SECTIONS.keySet());
            return opts.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("edit"))) {
            return SECTIONS.keySet().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("edit"))) {
            String section = args[1].toLowerCase();
            String fileName = SECTIONS.get(section);
            if (fileName == null) return List.of();
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) return List.of();
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            return getAllLeafKeys(cfg, "").stream()
                    .filter(k -> k.startsWith(args[2])).collect(Collectors.toList());
        }
        return List.of();
    }
}
