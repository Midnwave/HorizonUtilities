package com.blockforge.horizonutilities.jobs.commands;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import com.blockforge.horizonutilities.jobs.JobPlayer;
import com.blockforge.horizonutilities.jobs.boost.BoostEvent;
import com.blockforge.horizonutilities.jobs.gui.*;
import com.blockforge.horizonutilities.jobs.leaderboard.JobLeaderboardGUI;
import com.blockforge.horizonutilities.jobs.quests.daily.ActiveQuest;
import com.blockforge.horizonutilities.jobs.quests.daily.DailyQuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles all {@code /jobs} subcommands.
 */
public class JobsCommand implements CommandExecutor {

    private static final String ADMIN_PERM = "horizonutilities.jobs.admin";

    private final HorizonUtilitiesPlugin plugin;

    public JobsCommand(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("[Jobs] Console must specify a subcommand.", NamedTextColor.RED));
                return true;
            }
            new JobBrowseGUI(plugin, player).open();
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "join"     -> cmdJoin(sender, args);
            case "leave"    -> cmdLeave(sender, args);
            case "info"     -> cmdInfo(sender, args);
            case "list"     -> cmdList(sender);
            case "stats"    -> cmdStats(sender, args);
            case "top"      -> cmdTop(sender, args);
            case "quests"   -> cmdQuests(sender);
            case "prestige" -> cmdPrestige(sender, args);
            case "admin"    -> cmdAdmin(sender, args);
            default -> sender.sendMessage(Component.text(
                    "[Jobs] Unknown subcommand. Try /jobs for the GUI.", NamedTextColor.RED));
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    private void cmdJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { noConsole(sender); return; }
        if (args.length < 2) { sender.sendMessage(usage("join <job>")); return; }
        plugin.getJobManager().joinJob(player, args[1].toLowerCase(Locale.ROOT));
    }

    private void cmdLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { noConsole(sender); return; }
        if (args.length < 2) { sender.sendMessage(usage("leave <job>")); return; }
        plugin.getJobManager().leaveJob(player, args[1].toLowerCase(Locale.ROOT));
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { noConsole(sender); return; }

        String jobId;
        if (args.length >= 2) {
            jobId = args[1].toLowerCase(Locale.ROOT);
        } else {
            // Default: show first enrolled job
            List<JobPlayer> jobs = plugin.getJobManager().getPlayerJobs(player.getUniqueId());
            if (jobs.isEmpty()) {
                player.sendMessage(Component.text("[Jobs] You are not enrolled in any jobs.", NamedTextColor.RED));
                return;
            }
            jobId = jobs.get(0).getJobId();
        }

        Job job = plugin.getJobManager().getJob(jobId);
        if (job == null) {
            player.sendMessage(Component.text("[Jobs] Unknown job: " + jobId, NamedTextColor.RED));
            return;
        }

        Optional<JobPlayer> jp = plugin.getJobManager().getPlayerJobs(player.getUniqueId())
                .stream().filter(j -> j.getJobId().equalsIgnoreCase(jobId)).findFirst();

        if (jp.isEmpty()) {
            // Show text-only info if not enrolled
            player.sendMessage(Component.text("[Jobs] " + job.getDisplayName(), NamedTextColor.GOLD));
            player.sendMessage(Component.text(job.getDescription(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("Max level: " + job.getMaxLevel(), NamedTextColor.AQUA));
            return;
        }

        new JobInfoGUI(plugin, player, job, jp.get()).open();
    }

    private void cmdList(CommandSender sender) {
        sender.sendMessage(Component.text("[Jobs] Available jobs:", NamedTextColor.GOLD));
        for (Job job : plugin.getJobManager().getAllJobs()) {
            sender.sendMessage(Component.text("  - ", NamedTextColor.GRAY)
                    .append(Component.text(job.getDisplayName(), NamedTextColor.YELLOW))
                    .append(Component.text(" (" + job.getId() + ")", NamedTextColor.DARK_GRAY)));
        }
    }

    private void cmdStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player self)) { noConsole(sender); return; }

        Player target = args.length >= 2
                ? plugin.getServer().getPlayerExact(args[1])
                : self;

        if (target == null) {
            sender.sendMessage(Component.text("[Jobs] Player not found.", NamedTextColor.RED));
            return;
        }

        List<JobPlayer> jobs = plugin.getJobManager().getPlayerJobs(target.getUniqueId());
        if (jobs.isEmpty()) {
            sender.sendMessage(Component.text("[Jobs] " + target.getName() + " has no jobs.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("[Jobs] Stats for " + target.getName() + ":", NamedTextColor.GOLD));
        for (JobPlayer jp : jobs) {
            sender.sendMessage(Component.text("  " + jp.getJobId(), NamedTextColor.AQUA)
                    .append(Component.text(" | Lv " + jp.getLevel(), NamedTextColor.GREEN))
                    .append(Component.text(" | Prestige " + jp.getPrestige(), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" | Earned: " + plugin.getVaultHook().format(jp.getTotalEarned()),
                            NamedTextColor.YELLOW)));
        }
    }

    private void cmdTop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { noConsole(sender); return; }
        String jobId = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
        new JobLeaderboardGUI(plugin, player, jobId).open();
    }

    private void cmdQuests(CommandSender sender) {
        if (!(sender instanceof Player player)) { noConsole(sender); return; }

        DailyQuestManager qm = plugin.getDailyQuestManager();
        if (qm == null || !qm.getConfig().isEnabled()) {
            player.sendMessage(Component.text("[Jobs] Daily quests are currently disabled.", NamedTextColor.GRAY));
            return;
        }

        java.util.List<ActiveQuest> quests = qm.getPlayerQuests(player.getUniqueId());
        if (quests.isEmpty()) {
            player.sendMessage(Component.text("[Jobs] You have no daily quests. Join a job first!", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("      Daily Quests", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("  ─────────────────────", NamedTextColor.DARK_GRAY));

        for (ActiveQuest q : quests) {
            NamedTextColor statusColor = q.isCompleted() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            String statusIcon = q.isCompleted() ? "✔" : "○";
            String progress = q.getCurrentProgress() + "/" + q.getTargetAmount();
            int pct = (int) (q.getProgressPercent() * 100);

            // Build progress bar
            int barLen = 15;
            int filled = (int) (q.getProgressPercent() * barLen);
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barLen; i++) bar.append(i < filled ? "█" : "░");

            String moneyStr = plugin.getVaultHook().isAvailable()
                    ? plugin.getVaultHook().format(q.getRewardMoney())
                    : String.format("$%.2f", q.getRewardMoney());

            player.sendMessage(Component.text("  " + statusIcon + " ", statusColor)
                    .append(Component.text(q.getDescription(), NamedTextColor.WHITE))
                    .append(Component.text(" [" + q.getJobId() + "]", NamedTextColor.DARK_GRAY)));

            player.sendMessage(Component.text("    ", NamedTextColor.GRAY)
                    .append(Component.text(bar.toString(), statusColor))
                    .append(Component.text(" " + progress + " (" + pct + "%)", NamedTextColor.GRAY)));

            if (!q.isCompleted()) {
                player.sendMessage(Component.text("    Rewards: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(moneyStr, NamedTextColor.GREEN))
                        .append(Component.text(" + ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.format("%.0f XP", q.getRewardXp()), NamedTextColor.AQUA)));
            }
        }

        long completed = quests.stream().filter(ActiveQuest::isCompleted).count();
        player.sendMessage(Component.text("  ─────────────────────", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  Completed: ", NamedTextColor.GRAY)
                .append(Component.text(completed + "/" + quests.size(), NamedTextColor.GREEN)));
        player.sendMessage(Component.empty());
    }

    private void cmdPrestige(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { noConsole(sender); return; }
        if (args.length < 2) { sender.sendMessage(usage("prestige <job>")); return; }
        plugin.getJobManager().prestige(player, args[1].toLowerCase(Locale.ROOT));
    }

    // -------------------------------------------------------------------------
    // Admin subcommands
    // -------------------------------------------------------------------------

    private void cmdAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(Component.text("[Jobs] No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "[Jobs] Admin subcommands: setlevel, addxp, reset, forcejoin, boost, reload",
                    NamedTextColor.YELLOW));
            return;
        }
        String adminSub = args[1].toLowerCase(Locale.ROOT);
        switch (adminSub) {
            case "reload" -> {
                plugin.getJobManager().loadJobDefinitions();
                sender.sendMessage(Component.text("[Jobs] Job definitions reloaded.", NamedTextColor.GREEN));
            }
            case "forcejoin" -> {
                if (args.length < 4) { sender.sendMessage(usage("admin forcejoin <player> <job>")); return; }
                Player target = plugin.getServer().getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage(noPlayer(args[2])); return; }
                plugin.getJobManager().joinJob(target, args[3].toLowerCase(Locale.ROOT));
                sender.sendMessage(Component.text("[Jobs] Force-joined " + target.getName()
                        + " to " + args[3], NamedTextColor.GREEN));
            }
            case "reset" -> {
                if (args.length < 4) { sender.sendMessage(usage("admin reset <player> <job>")); return; }
                Player target = plugin.getServer().getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage(noPlayer(args[2])); return; }
                plugin.getJobManager().leaveJob(target, args[3]);
                plugin.getJobManager().joinJob(target, args[3]);
                sender.sendMessage(Component.text("[Jobs] Reset " + target.getName()
                        + "'s " + args[3] + " job.", NamedTextColor.GREEN));
            }
            case "setlevel" -> {
                if (args.length < 5) { sender.sendMessage(usage("admin setlevel <player> <job> <level>")); return; }
                Player target = plugin.getServer().getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage(noPlayer(args[2])); return; }
                try {
                    int level = Integer.parseInt(args[4]);
                    plugin.getJobManager().getPlayerJobs(target.getUniqueId()).stream()
                            .filter(jp -> jp.getJobId().equalsIgnoreCase(args[3]))
                            .findFirst().ifPresent(jp -> {
                                jp.setLevel(level);
                                plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                                        () -> plugin.getJobManager().getStorage().savePlayerJob(jp));
                                sender.sendMessage(Component.text("[Jobs] Set " + target.getName()
                                        + "'s " + args[3] + " level to " + level, NamedTextColor.GREEN));
                            });
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("[Jobs] Invalid level number.", NamedTextColor.RED));
                }
            }
            case "addxp" -> {
                if (args.length < 5) { sender.sendMessage(usage("admin addxp <player> <job> <amount>")); return; }
                Player target = plugin.getServer().getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage(noPlayer(args[2])); return; }
                try {
                    double xp = Double.parseDouble(args[4]);
                    plugin.getJobManager().grantQuestXp(target, args[3].toLowerCase(Locale.ROOT), xp);
                    sender.sendMessage(Component.text("[Jobs] Added " + xp + " XP to "
                            + target.getName() + "'s " + args[3] + " job.", NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("[Jobs] Invalid XP amount.", NamedTextColor.RED));
                }
            }
            case "boost" -> {
                // /jobs admin boost <jobId|all> <multiplier> <durationSeconds> [reason]
                if (args.length < 5) {
                    sender.sendMessage(usage("admin boost <job|all> <multiplier> <seconds> [reason]"));
                    return;
                }
                String jobId = args[2].equalsIgnoreCase("all") ? null : args[2].toLowerCase(Locale.ROOT);
                try {
                    double mult = Double.parseDouble(args[3]);
                    long durationMs = Long.parseLong(args[4]) * 1000L;
                    String reason = args.length >= 6 ? String.join(" ",
                            Arrays.copyOfRange(args, 5, args.length)) : "Admin boost";
                    UUID startedBy = sender instanceof Player p ? p.getUniqueId() : null;
                    BoostEvent boost = plugin.getJobManager().getBoostManager()
                            .startBoost(jobId, mult, durationMs, reason, startedBy);
                    if (boost != null) {
                        sender.sendMessage(Component.text("[Jobs] Boost started: x" + mult
                                + " for " + args[4] + "s on " + (jobId != null ? jobId : "all jobs"),
                                NamedTextColor.GREEN));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("[Jobs] Invalid multiplier or duration.", NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(Component.text("[Jobs] Unknown admin subcommand.", NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void noConsole(CommandSender sender) {
        sender.sendMessage(Component.text("[Jobs] This command requires a player.", NamedTextColor.RED));
    }

    private Component usage(String usage) {
        return Component.text("[Jobs] Usage: /jobs " + usage, NamedTextColor.YELLOW);
    }

    private Component noPlayer(String name) {
        return Component.text("[Jobs] Player not found: " + name, NamedTextColor.RED);
    }
}
