package com.blockforge.horizonutilities.config.dialog;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chest GUI fallback for the config editor when the Dialog API is unavailable
 * (e.g. ViaBackwards, Bedrock clients via Geyser, older protocol versions).
 */
public class ConfigChestGUI implements Listener {

    private final HorizonUtilitiesPlugin plugin;

    /** Tracks what GUI a player has open: "menu" or "section:<name>" */
    private final Map<UUID, String> openGUIs = new ConcurrentHashMap<>();
    /** Section key lists per player for mapping slot -> config key */
    private final Map<UUID, List<String>> sectionKeys = new ConcurrentHashMap<>();

    private static final Map<String, String> SECTIONS = HorizonConfigCommand.getSections();
    private static final Map<String, String> SECTION_DESCRIPTIONS = HorizonConfigCommand.getSectionDescriptions();

    /** Materials for each section to make the GUI visually distinct */
    private static final Map<String, Material> SECTION_ICONS = new LinkedHashMap<>();
    static {
        SECTION_ICONS.put("jobs",        Material.IRON_PICKAXE);
        SECTION_ICONS.put("auction",     Material.GOLD_INGOT);
        SECTION_ICONS.put("chatgames",   Material.WRITTEN_BOOK);
        SECTION_ICONS.put("chat",        Material.OAK_SIGN);
        SECTION_ICONS.put("chatbubbles", Material.NAME_TAG);
        SECTION_ICONS.put("bounty",      Material.DIAMOND_SWORD);
        SECTION_ICONS.put("lottery",     Material.EMERALD);
        SECTION_ICONS.put("trade",       Material.CHEST);
        SECTION_ICONS.put("blackmarket", Material.ENDER_CHEST);
        SECTION_ICONS.put("warps",       Material.ENDER_PEARL);
        SECTION_ICONS.put("playerwarps", Material.COMPASS);
        SECTION_ICONS.put("combat",      Material.SHIELD);
        SECTION_ICONS.put("customitems", Material.NETHER_STAR);
        SECTION_ICONS.put("tournaments", Material.GOLDEN_APPLE);
        SECTION_ICONS.put("crafting",    Material.CRAFTING_TABLE);
    }

    public ConfigChestGUI(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Menu GUI
    // -------------------------------------------------------------------------

    public void openMenu(Player player) {
        int size = 27; // 3 rows
        Inventory inv = Bukkit.createInventory(null, size,
                Component.text("HorizonUtilities Config", NamedTextColor.GOLD));

        int slot = 0;
        for (Map.Entry<String, String> entry : SECTIONS.entrySet()) {
            if (slot >= size) break;
            String section = entry.getKey();
            String desc = SECTION_DESCRIPTIONS.getOrDefault(section, entry.getValue());
            Material icon = SECTION_ICONS.getOrDefault(section, Material.PAPER);

            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(capitalize(section), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(desc, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Click to configure", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        openGUIs.put(player.getUniqueId(), "menu");
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Section GUI
    // -------------------------------------------------------------------------

    public void openSection(Player player, String section) {
        String fileName = SECTIONS.get(section);
        if (fileName == null) return;

        File file = getConfigFile(fileName);
        if (!file.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<String, String> comments = readYamlComments(file);
        List<String> leafKeys = getAllLeafKeys(cfg, "");

        int size = Math.min(54, ((leafKeys.size() + 1) / 9 + 1) * 9); // round up to next row
        size = Math.max(9, size);
        String sectionDesc = SECTION_DESCRIPTIONS.getOrDefault(section, fileName);
        Inventory inv = Bukkit.createInventory(null, size,
                Component.text(capitalize(section) + " Config", NamedTextColor.GOLD));

        List<String> keys = new ArrayList<>();
        int slot = 0;
        for (String key : leafKeys) {
            if (slot >= size - 1) break; // leave last slot for back button
            Object val = cfg.get(key);
            if (val == null || val instanceof org.bukkit.configuration.ConfigurationSection) continue;
            String valStr = val.toString();

            String desc = lookupComment(comments, key);

            Material mat = Material.PAPER;
            if (val instanceof Boolean b) mat = b ? Material.LIME_DYE : Material.GRAY_DYE;
            else if (val instanceof Number) mat = Material.CLOCK;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(key, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Value: " + valStr, NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            if (desc != null) {
                lore.add(Component.text(desc, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            if (val instanceof Boolean) {
                lore.add(Component.text("Click to toggle", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Click for edit command", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
            keys.add(key);
        }

        // Back button in last slot
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("\u2190 Back to Menu", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(size - 1, back);
        keys.add("__BACK__");

        openGUIs.put(player.getUniqueId(), "section:" + section);
        sectionKeys.put(player.getUniqueId(), keys);
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String guiType = openGUIs.get(player.getUniqueId());
        if (guiType == null) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (guiType.equals("menu")) {
            handleMenuClick(player, slot);
        } else if (guiType.startsWith("section:")) {
            String section = guiType.substring("section:".length());
            handleSectionClick(player, section, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openGUIs.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            cleanup(player.getUniqueId());
        }
    }

    private void handleMenuClick(Player player, int slot) {
        List<String> sectionNames = new ArrayList<>(SECTIONS.keySet());
        if (slot >= 0 && slot < sectionNames.size()) {
            String section = sectionNames.get(slot);
            plugin.getServer().getScheduler().runTask(plugin, () -> openSection(player, section));
        }
    }

    private void handleSectionClick(Player player, String section, int slot) {
        List<String> keys = sectionKeys.get(player.getUniqueId());
        if (keys == null || slot < 0 || slot >= keys.size()) return;

        String key = keys.get(slot);
        if ("__BACK__".equals(key)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openMenu(player));
            return;
        }

        String fileName = SECTIONS.get(section);
        if (fileName == null) return;

        File file = getConfigFile(fileName);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Object current = cfg.get(key);
        if (current == null) return;

        // Boolean: toggle directly
        if (current instanceof Boolean boolVal) {
            boolean newVal = !boolVal;
            cfg.set(key, newVal);
            try {
                cfg.save(file);
                plugin.reloadAllConfigs();
                player.sendMessage(plugin.getMessagesManager().format("prefix")
                        .append(Component.text(" Set ", NamedTextColor.GREEN))
                        .append(Component.text(section + "." + key, NamedTextColor.YELLOW))
                        .append(Component.text(" = " + newVal, NamedTextColor.WHITE))
                        .append(Component.text(" (saved + reloaded)", NamedTextColor.DARK_GRAY)));
            } catch (IOException e) {
                player.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
            }
            // Refresh the section GUI
            plugin.getServer().getScheduler().runTask(plugin, () -> openSection(player, section));
            return;
        }

        // Other types: suggest command in chat
        player.closeInventory();
        String suggestCmd = "/horizonconfig set " + section + " " + key + " " + current;
        player.sendMessage(plugin.getMessagesManager().format("prefix")
                .append(Component.text(" Type the new value for ", NamedTextColor.GRAY))
                .append(Component.text(key, NamedTextColor.YELLOW))
                .append(Component.text(":", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(suggestCmd, NamedTextColor.WHITE)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(suggestCmd))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        Component.text("Click to edit in chat"))));
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    public void cleanup(UUID uuid) {
        openGUIs.remove(uuid);
        sectionKeys.remove(uuid);
    }

    public boolean hasOpenGUI(UUID uuid) {
        return openGUIs.containsKey(uuid);
    }

    // -------------------------------------------------------------------------
    // Utility (mirrors HorizonConfigCommand)
    // -------------------------------------------------------------------------

    private File getConfigFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try { plugin.saveResource(fileName, false); } catch (Exception ignored) {}
        }
        return file;
    }

    private Map<String, String> readYamlComments(File file) {
        Map<String, String> comments = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            StringBuilder pending = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    String text = trimmed.substring(1).trim();
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

    private String lookupComment(Map<String, String> comments, String keyPath) {
        String desc = comments.get(keyPath);
        if (desc == null && keyPath.contains(".")) {
            desc = comments.get(keyPath.substring(keyPath.lastIndexOf('.') + 1));
        }
        return desc;
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

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
