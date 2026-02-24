package com.blockforge.horizonutilities.jobs.quests.rewards;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import me.pikamug.quests.module.BukkitCustomReward;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * PikaMug/Quests custom reward that grants experience to a specific job.
 * Quest admins configure "Job ID" and "XP Amount" when creating quests.
 */
public class JobXpReward extends BukkitCustomReward {

    public JobXpReward() {
        setName("Job XP Reward");
        setAuthor("HorizonUtilities");
        addStringPrompt("Job ID",    "Job to grant XP to (e.g. miner)",  "miner");
        addStringPrompt("XP Amount", "Amount of job XP to grant",         "100");
    }

    @Override
    public void giveReward(UUID uuid, Map<String, Object> data) {
        String jobId = String.valueOf(data.getOrDefault("Job ID", "miner"));
        double xp;
        try {
            xp = Double.parseDouble(String.valueOf(data.getOrDefault("XP Amount", "100")));
        } catch (NumberFormatException e) {
            xp = 100;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        HorizonUtilitiesPlugin.getInstance().getJobManager().grantQuestXp(player, jobId, xp);
    }
}
