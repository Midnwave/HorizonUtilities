package com.blockforge.horizonutilities.games;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.games.content.RecipeData;
import com.blockforge.horizonutilities.games.types.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChatGameManager {

    private final HorizonUtilitiesPlugin plugin;
    private final GameScheduler scheduler;
    private ChatGame activeGame;
    private BukkitTask timeoutTask;

    public ChatGameManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = new GameScheduler(plugin, this);
    }

    public void startScheduler() {
        RecipeData.loadRecipes();
        plugin.getLogger().info("Loaded " + RecipeData.getRecipeCount() + " recipes for chat games.");
        scheduler.start();
    }

    public void shutdown() {
        scheduler.stop();
        if (timeoutTask != null) timeoutTask.cancel();
        activeGame = null;
    }

    public boolean startRandomGame() {
        if (activeGame != null) return false;

        List<String> enabledTypes = new ArrayList<>();
        if (plugin.getChatGamesConfig().isGameEnabled("unscramble")) enabledTypes.add("unscramble");
        if (plugin.getChatGamesConfig().isGameEnabled("retype")) enabledTypes.add("retype");
        if (plugin.getChatGamesConfig().isGameEnabled("math")) enabledTypes.add("math");
        if (plugin.getChatGamesConfig().isGameEnabled("unreverse")) enabledTypes.add("unreverse");
        if (plugin.getChatGamesConfig().isGameEnabled("recipe-guess")) enabledTypes.add("recipe-guess");

        if (enabledTypes.isEmpty()) {
            scheduler.scheduleNext();
            return false;
        }

        String type = enabledTypes.get(ThreadLocalRandom.current().nextInt(enabledTypes.size()));
        return startGame(type);
    }

    public boolean startGame(String type) {
        if (activeGame != null) return false;

        activeGame = switch (type.toLowerCase()) {
            case "unscramble" -> new UnscrambleGame(plugin);
            case "retype" -> new RetypeGame(plugin);
            case "math" -> new MathGame(plugin);
            case "unreverse" -> new UnreverseGame(plugin);
            case "recipe-guess" -> new RecipeGuessGame(plugin);
            default -> null;
        };

        if (activeGame == null) return false;

        Component question = activeGame.getQuestion();
        Component message = plugin.getMessagesManager().format("game-started",
                Placeholder.unparsed("type", activeGame.getTypeName()),
                Placeholder.component("question", question));

        Bukkit.getOnlinePlayers().forEach(p -> {
            if (p.hasPermission("horizonutilities.chatgames.play")) {
                p.sendMessage(message);
            }
        });

        int timeout = plugin.getChatGamesConfig().getTimeoutSeconds();
        timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (activeGame != null) {
                Component timeoutMsg = plugin.getMessagesManager().format("game-timeout",
                        Placeholder.unparsed("answer", activeGame.getAnswer()));
                Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(timeoutMsg));
                activeGame = null;
                scheduler.scheduleNext();
            }
        }, timeout * 20L);

        return true;
    }

    public boolean handleAnswer(Player player, String message) {
        if (activeGame == null) return false;
        if (!activeGame.checkAnswer(message)) return false;

        long timeMs = activeGame.getElapsedMs();
        double timeSec = timeMs / 1000.0;
        String gameType = activeGame.getTypeKey();
        double reward = plugin.getChatGamesConfig().getRewardMoney(gameType);

        if (plugin.getVaultHook().isAvailable() && reward > 0) {
            plugin.getVaultHook().deposit(player, reward);
        }

        List<String> rewardCmds = plugin.getChatGamesConfig().getRewardCommands(gameType);
        for (String cmd : rewardCmds) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("<player>", player.getName()));
        }

        plugin.getLeaderboardManager().recordWin(
                player.getUniqueId(), player.getName(), timeMs);

        Component winMsg = plugin.getMessagesManager().format("game-winner",
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("time", String.format("%.2f", timeSec)),
                Placeholder.unparsed("answer", activeGame.getAnswer()),
                Placeholder.unparsed("reward", plugin.getVaultHook().format(reward)));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(winMsg));

        if (timeoutTask != null) timeoutTask.cancel();
        activeGame = null;
        scheduler.scheduleNext();

        return true;
    }

    public void stopCurrentGame() {
        if (timeoutTask != null) timeoutTask.cancel();
        activeGame = null;
    }

    public boolean isGameActive() { return activeGame != null; }
    public ChatGame getActiveGame() { return activeGame; }
}
