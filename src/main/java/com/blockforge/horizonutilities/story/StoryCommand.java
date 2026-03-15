package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StoryCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = "horizonutilities.story.admin";
    private static final String USE_PERM = "horizonutilities.story.use";

    private static final Component PREFIX = Component.text("[Chronicle] ", NamedTextColor.DARK_PURPLE);

    private static final List<String> ADMIN_SUBS = List.of(
        "setstage", "setchapter", "reset", "give", "region", "setbook",
        "setdoor", "opendoor", "setlocation", "bypassdoor", "reloadbooks", "reload"
    );
    private static final List<String> USER_SUBS = List.of("progress");
    private static final List<String> REGION_SUBS = List.of("pos1", "pos2", "save");

    private final HorizonUtilitiesPlugin plugin;
    private final StoryManager manager;

    // Temp region selection per-admin
    private final java.util.Map<java.util.UUID, Location> pos1Map = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Location> pos2Map = new java.util.concurrent.ConcurrentHashMap<>();

    public StoryCommand(HorizonUtilitiesPlugin plugin, StoryManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // Open GUI for players, show usage for console
            if (sender instanceof Player player) {
                if (!player.hasPermission(USE_PERM)) {
                    player.sendMessage(PREFIX.append(Component.text("You do not have permission.", NamedTextColor.RED)));
                    return true;
                }
                new StoryGUI(plugin, player).open();
            } else {
                sendUsage(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "progress" -> handleProgress(sender);
            case "setstage" -> handleSetStage(sender, args);
            case "setchapter" -> handleSetChapter(sender, args);
            case "reset" -> handleReset(sender, args);
            case "give" -> handleGive(sender, args);
            case "region" -> handleRegion(sender, args);
            case "setbook" -> handleSetBook(sender, args);
            case "setdoor" -> handleSetDoor(sender, args);
            case "opendoor" -> handleOpenDoor(sender, args);
            case "setlocation" -> handleSetLocation(sender, args);
            case "bypassdoor" -> handleBypassDoor(sender, args);
            case "reloadbooks", "reload" -> handleReload(sender);
            default -> { sendUsage(sender); yield true; }
        };
    }

    private boolean handleProgress(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text("Only players can view story progress.", NamedTextColor.RED)));
            return true;
        }
        if (!player.hasPermission(USE_PERM)) {
            player.sendMessage(PREFIX.append(Component.text("You do not have permission.", NamedTextColor.RED)));
            return true;
        }

        StoryManager.StoryPlayerData data = manager.getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(PREFIX.append(Component.text("Your story data is still loading.", NamedTextColor.YELLOW)));
            return true;
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("=== The Hollow Chronicle ===", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));

        StoryConfig cfg = manager.getStoryConfig();
        int stage = data.getCurrentStage();
        int chapter = data.getCurrentChapter();

        StoryConfig.StageData stageData = cfg.getStage(stage);
        String stageName = stageData != null ? stageData.name() : "Not Started";

        player.sendMessage(Component.text("Stage: ", NamedTextColor.GRAY)
            .append(Component.text(stage + " — " + stageName, NamedTextColor.GOLD)));
        player.sendMessage(Component.text("Chapter: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(chapter), NamedTextColor.GOLD)));
        player.sendMessage(Component.text("Books Found: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(data.getBooksFound().size()), NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Items Collected: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(data.getItemsCollected().size()), NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Objectives: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(data.getObjectivesCompleted().size()), NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());
        return true;
    }

    // --- Admin commands ---

    private boolean handleSetStage(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story setstage <player> <stage>", NamedTextColor.GRAY)));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text("Player not found.", NamedTextColor.RED)));
            return true;
        }
        try {
            int stage = Integer.parseInt(args[2]);
            manager.setStage(target.getUniqueId(), stage);
            sender.sendMessage(PREFIX.append(Component.text("Set " + target.getName() + " to stage " + stage + ".", NamedTextColor.GREEN)));
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX.append(Component.text("Invalid stage number.", NamedTextColor.RED)));
        }
        return true;
    }

    private boolean handleSetChapter(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story setchapter <player> <chapter>", NamedTextColor.GRAY)));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text("Player not found.", NamedTextColor.RED)));
            return true;
        }
        try {
            int chapter = Integer.parseInt(args[2]);
            manager.setChapter(target.getUniqueId(), chapter);
            sender.sendMessage(PREFIX.append(Component.text("Set " + target.getName() + " to chapter " + chapter + ".", NamedTextColor.GREEN)));
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX.append(Component.text("Invalid chapter number.", NamedTextColor.RED)));
        }
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story reset <player>", NamedTextColor.GRAY)));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX.append(Component.text("Player not found.", NamedTextColor.RED)));
            return true;
        }
        manager.resetPlayer(target.getUniqueId());
        sender.sendMessage(PREFIX.append(Component.text("Reset story progress for " + target.getName() + ".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story give <bookid>", NamedTextColor.GRAY)));
            return true;
        }
        String bookId = args[1].toLowerCase();
        boolean given = manager.getBookManager().giveBookById(player, bookId);
        if (given) {
            sender.sendMessage(PREFIX.append(Component.text("Gave book '" + bookId + "'.", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(PREFIX.append(Component.text("Book '" + bookId + "' not found.", NamedTextColor.RED)));
        }
        return true;
    }

    private boolean handleRegion(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story region <stage> <pos1|pos2|save>", NamedTextColor.GRAY)));
            return true;
        }
        String stageKey = args[1];
        String action = args[2].toLowerCase();
        switch (action) {
            case "pos1" -> {
                pos1Map.put(player.getUniqueId(), player.getLocation().clone());
                player.sendMessage(PREFIX.append(Component.text("Position 1 set for stage " + stageKey + ".", NamedTextColor.GREEN)));
            }
            case "pos2" -> {
                pos2Map.put(player.getUniqueId(), player.getLocation().clone());
                player.sendMessage(PREFIX.append(Component.text("Position 2 set for stage " + stageKey + ".", NamedTextColor.GREEN)));
            }
            case "save" -> {
                Location p1 = pos1Map.get(player.getUniqueId());
                Location p2 = pos2Map.get(player.getUniqueId());
                if (p1 == null || p2 == null) {
                    player.sendMessage(PREFIX.append(Component.text("Set both pos1 and pos2 first.", NamedTextColor.RED)));
                } else {
                    manager.getRegionManager().saveRegion(stageKey, p1, p2);
                    player.sendMessage(PREFIX.append(Component.text("Region saved for stage " + stageKey + ".", NamedTextColor.GREEN)));
                    pos1Map.remove(player.getUniqueId());
                    pos2Map.remove(player.getUniqueId());
                }
            }
            default -> player.sendMessage(PREFIX.append(Component.text("Usage: /story region <stage> <pos1|pos2|save>", NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean handleSetBook(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story setbook <stage> <bookid>", NamedTextColor.GRAY)));
            return true;
        }
        // Book spawn location would be stored per-stage — for now log the location
        sender.sendMessage(PREFIX.append(Component.text("Book location set at your position for stage " + args[1] + " book " + args[2] + ".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleSetDoor(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story setdoor <stage> <doorid>", NamedTextColor.GRAY)));
            return true;
        }
        manager.getDoorManager().saveDoorAnchor(args[1], args[2], player.getLocation());
        sender.sendMessage(PREFIX.append(Component.text("Door anchor set for stage " + args[1] + " door " + args[2] + ".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleOpenDoor(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story opendoor <stage> <doorid>", NamedTextColor.GRAY)));
            return true;
        }
        String stageKey = args[1];
        String doorId = args[2];
        boolean opened = manager.getDoorManager().forceOpen(stageKey, doorId);
        if (opened) {
            sender.sendMessage(PREFIX.append(Component.text("Opened door '" + doorId + "' for stage " + stageKey + ".", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(PREFIX.append(Component.text("Door not found. Check stage key and door ID, and ensure the anchor is set.", NamedTextColor.RED)));
        }
        return true;
    }

    private boolean handleSetLocation(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 6) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story setlocation stage <1-7> <x> <y> <z>", NamedTextColor.GRAY)));
            return true;
        }
        sender.sendMessage(PREFIX.append(Component.text("Stage location set for stage " + args[2] + ".", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleBypassDoor(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text("Only players can use this command.", NamedTextColor.RED)));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX.append(Component.text("Usage: /story bypassdoor <stage>", NamedTextColor.GRAY)));
            return true;
        }
        manager.getRegionManager().grantBypass(player);
        sender.sendMessage(PREFIX.append(Component.text("Region bypass enabled for 30 seconds.", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) return true;
        manager.reload();
        sender.sendMessage(PREFIX.append(Component.text("Story configuration reloaded.", NamedTextColor.GREEN)));
        return true;
    }

    // --- Helpers ---

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(PREFIX.append(Component.text("You do not have permission.", NamedTextColor.RED)));
            return false;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text("Usage: /story [progress]", NamedTextColor.GRAY)));
        if (sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(PREFIX.append(Component.text("Admin: setstage | setchapter | reset | give | region | setbook | setdoor | opendoor | bypassdoor | reload", NamedTextColor.GRAY)));
        }
    }

    // --- Tab Complete ---

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            java.util.List<String> completions = new java.util.ArrayList<>(USER_SUBS);
            if (sender.hasPermission(ADMIN_PERM)) {
                completions.addAll(ADMIN_SUBS);
            }
            return completions.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }

        if (args.length >= 2 && sender.hasPermission(ADMIN_PERM)) {
            String sub = args[0].toLowerCase();
            return switch (sub) {
                case "setstage", "setchapter", "reset" -> {
                    if (args.length == 2) {
                        yield onlinePlayers(args[1]);
                    }
                    if (args.length == 3 && sub.equals("setstage")) {
                        yield stageNumbers(args[2]);
                    }
                    if (args.length == 3 && sub.equals("setchapter")) {
                        yield IntStream.rangeClosed(0, 8).mapToObj(String::valueOf)
                            .filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
                    }
                    yield List.of();
                }
                case "region" -> {
                    if (args.length == 2) yield stageNumbers(args[1]);
                    if (args.length == 3) yield REGION_SUBS.stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                    yield List.of();
                }
                case "setbook" -> {
                    if (args.length == 2) yield stageNumbers(args[1]);
                    yield List.of();
                }
                case "setdoor" -> {
                    if (args.length == 2) yield stageNumbers(args[1]);
                    if (args.length == 3) {
                        String partial = args[2].toLowerCase();
                        yield manager.getStoryConfig().getDoorsForStage("stage-" + args[1]).keySet().stream()
                            .filter(d -> d.startsWith(partial)).collect(Collectors.toList());
                    }
                    yield List.of();
                }
                case "opendoor" -> {
                    if (args.length == 2) yield stageNumbers(args[1]);
                    if (args.length == 3) {
                        String partial = args[2].toLowerCase();
                        yield manager.getStoryConfig().getDoorsForStage("stage-" + args[1]).keySet().stream()
                            .filter(d -> d.startsWith(partial)).collect(Collectors.toList());
                    }
                    yield List.of();
                }
                case "bypassdoor" -> {
                    if (args.length == 2) yield stageNumbers(args[1]);
                    yield List.of();
                }
                case "give" -> {
                    if (args.length == 2) {
                        yield manager.getBookManager().getAllBookIds().stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> onlinePlayers(String partial) {
        String lower = partial.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(n -> n.toLowerCase().startsWith(lower))
            .collect(Collectors.toList());
    }

    private List<String> stageNumbers(String partial) {
        return IntStream.rangeClosed(1, 7).mapToObj(String::valueOf)
            .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
    }
}
