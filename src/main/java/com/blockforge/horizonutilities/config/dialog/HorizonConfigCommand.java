package com.blockforge.horizonutilities.config.dialog;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.dialog.DialogUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
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
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /horizonconfig [section] [set <key> <value>]
 *
 * Full dialog-based config editor for players. Console senders get a chat
 * fallback with clickable text. Each config value displays a description
 * read from the YAML file comments.
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

    /** Human-readable description for each config section */
    private static final Map<String, String> SECTION_DESCRIPTIONS = new LinkedHashMap<>();
    static {
        SECTION_DESCRIPTIONS.put("jobs",        "Jobs system \u2014 XP, income, leveling & tax settings");
        SECTION_DESCRIPTIONS.put("auction",     "Auction house \u2014 fees, durations & listing limits");
        SECTION_DESCRIPTIONS.put("chatgames",   "Chat games \u2014 trivia, math & unscramble settings");
        SECTION_DESCRIPTIONS.put("chat",        "Chat placeholders & formatting");
        SECTION_DESCRIPTIONS.put("chatbubbles", "Chat bubbles \u2014 floating text above players");
        SECTION_DESCRIPTIONS.put("bounty",      "Bounty system \u2014 min/max amounts & cooldowns");
        SECTION_DESCRIPTIONS.put("lottery",     "Lottery \u2014 ticket prices & draw intervals");
        SECTION_DESCRIPTIONS.put("trade",       "Player trading \u2014 distance & cooldowns");
        SECTION_DESCRIPTIONS.put("blackmarket", "Black market \u2014 rotation & pricing");
        SECTION_DESCRIPTIONS.put("warps",       "Admin warps \u2014 teleport locations");
        SECTION_DESCRIPTIONS.put("playerwarps", "Player warps \u2014 limits & costs");
        SECTION_DESCRIPTIONS.put("combat",      "Combat \u2014 PvP settings & combat tag");
        SECTION_DESCRIPTIONS.put("customitems", "Custom items \u2014 recipes & attributes");
        SECTION_DESCRIPTIONS.put("tournaments", "Tournaments \u2014 schedules & rewards");
        SECTION_DESCRIPTIONS.put("crafting",    "Crafting tables \u2014 custom crafting stations");
        SECTION_DESCRIPTIONS.put("maintenance", "Maintenance mode \u2014 MOTD & kick settings");
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
            if (sender instanceof Player player) {
                showSectionDialog(player, section);
            } else {
                showSectionChat(sender, section);
            }
            return true;
        }

        // /horizonconfig — show menu
        if (sender instanceof Player player) {
            showMenuDialog(player);
        } else {
            showMenuChat(sender);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Dialog-based menu (players)
    // -------------------------------------------------------------------------

    private void showMenuDialog(Player player) {
        try {
            List<ActionButton> buttons = new ArrayList<>();
            for (Map.Entry<String, String> entry : SECTIONS.entrySet()) {
                String section = entry.getKey();
                String desc = SECTION_DESCRIPTIONS.getOrDefault(section, entry.getValue());
                buttons.add(ActionButton.builder(
                        Component.text(capitalize(section), NamedTextColor.AQUA))
                    .tooltip(Component.text(desc, NamedTextColor.GRAY))
                    .width(150)
                    .action(DialogAction.customClick(
                        (response, audience) -> {
                            if (audience instanceof Player p) {
                                plugin.getServer().getScheduler().runTask(plugin,
                                    () -> showSectionDialog(p, section));
                            }
                        },
                        ClickCallback.Options.builder().uses(1).build()
                    ))
                    .build());
            }

            Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(
                        Component.text("HorizonUtilities Config", NamedTextColor.GOLD))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(
                        Component.text("Select a section to configure:", NamedTextColor.GRAY))))
                    .build())
                .type(DialogType.multiAction(buttons, null, 2))
            );
            player.showDialog(dialog);
        } catch (Exception e) {
            // Dialog API not available — fall back to chat
            showMenuChat(player);
        }
    }

    private void showSectionDialog(Player player, String section) {
        String fileName = SECTIONS.get(section);
        if (fileName == null) {
            player.sendMessage(Component.text("Unknown section: " + section +
                    ". Valid: " + String.join(", ", SECTIONS.keySet()), NamedTextColor.RED));
            return;
        }

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            player.sendMessage(Component.text("Config file not found: " + fileName, NamedTextColor.RED));
            return;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<String, String> comments = readYamlComments(file);

        try {
            List<ActionButton> buttons = new ArrayList<>();
            for (String key : getAllLeafKeys(cfg, "")) {
                Object val = cfg.get(key);
                if (val == null || val instanceof org.bukkit.configuration.ConfigurationSection) continue;
                String valStr = val.toString();

                // Look up description from YAML comments
                String desc = lookupComment(comments, key);

                Component label = Component.text(key + ": ", NamedTextColor.YELLOW)
                    .append(Component.text(valStr, NamedTextColor.WHITE));
                Component tooltip = desc != null
                    ? Component.text(desc, NamedTextColor.GRAY)
                    : Component.text("Click to edit", NamedTextColor.GRAY);

                buttons.add(ActionButton.builder(label)
                    .tooltip(tooltip)
                    .width(300)
                    .action(DialogAction.customClick(
                        (response, audience) -> {
                            if (audience instanceof Player p) {
                                plugin.getServer().getScheduler().runTask(plugin,
                                    () -> handleEdit(p, section, key));
                            }
                        },
                        ClickCallback.Options.builder().uses(1).build()
                    ))
                    .build());
            }

            // Back button
            buttons.add(ActionButton.builder(
                    Component.text("\u2190 Back to Menu", NamedTextColor.GRAY))
                .width(300)
                .action(DialogAction.customClick(
                    (response, audience) -> {
                        if (audience instanceof Player p) {
                            plugin.getServer().getScheduler().runTask(plugin,
                                () -> showMenuDialog(p));
                        }
                    },
                    ClickCallback.Options.builder().uses(1).build()
                ))
                .build());

            String sectionDesc = SECTION_DESCRIPTIONS.getOrDefault(section, fileName);
            Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(
                        Component.text(capitalize(section) + " Config", NamedTextColor.GOLD))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(
                        Component.text(sectionDesc + "\n" + fileName, NamedTextColor.GRAY))))
                    .build())
                .type(DialogType.multiAction(buttons, null, 1))
            );
            player.showDialog(dialog);
        } catch (Exception e) {
            showSectionChat(player, section);
        }
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

        // Get description for this key
        Map<String, String> comments = readYamlComments(file);
        String desc = lookupComment(comments, key);
        String descLine = desc != null ? desc + "\n\n" : "";

        // For boolean values, use a simple confirmation toggle instead of text input
        if (current instanceof Boolean boolVal) {
            boolean newVal = !boolVal;
            DialogUtil.showConfirmation(player,
                    Component.text("Edit: " + key),
                    Component.text(descLine + "Current: " + currentVal + "\nSet to: " + newVal + "?"),
                    Component.text("Set to " + newVal),
                    Component.text("Cancel"),
                    () -> {
                        applyConfigValue(player, section, fileName, key, current, String.valueOf(newVal));
                        // Re-open section dialog so they can keep editing
                        showSectionDialog(player, section);
                    }
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
                    Component.text(descLine + "Current: " + currentVal +
                            "\nType: " + current.getClass().getSimpleName()),
                    List.of(valueInput),
                    Component.text("Save"),
                    Component.text("Cancel"),
                    (response) -> {
                        String newVal = response.getText("config_value");
                        if (newVal != null && !newVal.trim().isEmpty()) {
                            applyConfigValue(player, section, fileName, key, current, newVal.trim());
                        }
                        // Re-open section dialog so they can keep editing
                        showSectionDialog(player, section);
                    }
            );
        } catch (Exception e) {
            // Dialog API not available (Bedrock client / old version) — fall back to chat suggestion
            String suggestCmd = "/horizonconfig set " + section + " " + key + " " + currentVal;
            player.sendMessage(Component.text("Edit ", NamedTextColor.GOLD)
                    .append(Component.text(key, NamedTextColor.YELLOW))
                    .append(Component.text(" \u2014 click to edit in chat:", NamedTextColor.GRAY)));
            if (desc != null) {
                player.sendMessage(Component.text(desc, NamedTextColor.DARK_GRAY));
            }
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
    // Chat fallbacks (console / Bedrock)
    // -------------------------------------------------------------------------

    private void showMenuChat(CommandSender sender) {
        sender.sendMessage(Component.text("\u2501\u2501\u2501 HorizonUtilities Config \u2501\u2501\u2501", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("Click a section to edit:", NamedTextColor.GRAY));
        for (Map.Entry<String, String> entry : SECTIONS.entrySet()) {
            String section = entry.getKey();
            String desc = SECTION_DESCRIPTIONS.getOrDefault(section, entry.getValue());
            sender.sendMessage(
                    Component.text("  [", NamedTextColor.DARK_GRAY)
                    .append(Component.text(section, NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.runCommand("/horizonconfig " + section))
                            .hoverEvent(HoverEvent.showText(
                                Component.text(desc, NamedTextColor.YELLOW))))
                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(desc, NamedTextColor.GRAY)));
        }
    }

    private void showSectionChat(CommandSender sender, String section) {
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
        Map<String, String> comments = readYamlComments(file);

        String sectionDesc = SECTION_DESCRIPTIONS.getOrDefault(section, fileName);
        sender.sendMessage(Component.text("\u2501\u2501\u2501 " + section + " (" + fileName + ") \u2501\u2501\u2501", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text(sectionDesc, NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Click a value to edit it.", NamedTextColor.GRAY));

        boolean isPlayer = sender instanceof Player;
        for (String key : getAllLeafKeys(cfg, "")) {
            Object val = cfg.get(key);
            if (val == null || val instanceof org.bukkit.configuration.ConfigurationSection) continue;
            String valStr = val.toString();

            String desc = lookupComment(comments, key);

            ClickEvent click = isPlayer
                    ? ClickEvent.runCommand("/horizonconfig edit " + section + " " + key)
                    : ClickEvent.suggestCommand("/horizonconfig set " + section + " " + key + " " + valStr);
            Component hoverText = desc != null
                    ? Component.text(desc + "\n", NamedTextColor.GRAY)
                        .append(Component.text("Click to edit", NamedTextColor.YELLOW))
                    : Component.text("Click to edit\n", NamedTextColor.GRAY)
                        .append(Component.text(key, NamedTextColor.AQUA));

            sender.sendMessage(
                    Component.text("  " + key + ": ", NamedTextColor.YELLOW)
                    .append(Component.text(valStr, NamedTextColor.WHITE)
                            .clickEvent(click)
                            .hoverEvent(HoverEvent.showText(hoverText))));

            // Show description below key in chat
            if (desc != null) {
                sender.sendMessage(Component.text("    " + desc, NamedTextColor.DARK_GRAY));
            }
        }
        sender.sendMessage(Component.text("Usage: /horizonconfig set " + section + " <key> <value>",
                NamedTextColor.DARK_GRAY));
    }

    // -------------------------------------------------------------------------
    // YAML comment reader
    // -------------------------------------------------------------------------

    /**
     * Reads a YAML file line-by-line and maps each key to the comment block
     * directly above it. Decorative separator lines (all dashes/equals) are
     * ignored. Blank lines reset the pending comment so header blocks don't
     * bleed into the first key.
     */
    private Map<String, String> readYamlComments(File file) {
        Map<String, String> comments = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            StringBuilder pending = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    String text = trimmed.substring(1).trim();
                    // Skip decorative lines (all dashes, equals, box-drawing, etc.)
                    if (!text.isEmpty() && !text.matches("^[-=\u2500\u2501\u2550]+$")) {
                        if (pending.length() > 0) pending.append(" ");
                        pending.append(text);
                    }
                } else if (!trimmed.isEmpty() && trimmed.contains(":") && !trimmed.startsWith("-")) {
                    String key = trimmed.split(":")[0].trim();
                    if (pending.length() > 0) {
                        comments.put(key, pending.toString());
                        pending.setLength(0);
                    }
                } else if (trimmed.isEmpty()) {
                    pending.setLength(0);
                }
            }
        } catch (IOException ignored) {}
        return comments;
    }

    /**
     * Looks up a YAML comment for the given config key path.
     * First tries an exact match, then falls back to the leaf key name.
     */
    private String lookupComment(Map<String, String> comments, String keyPath) {
        String desc = comments.get(keyPath);
        if (desc == null && keyPath.contains(".")) {
            desc = comments.get(keyPath.substring(keyPath.lastIndexOf('.') + 1));
        }
        return desc;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

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

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
