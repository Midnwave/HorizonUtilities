package com.blockforge.horizonutilities.jobs.quests.requirements;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import me.pikamug.quests.module.BukkitCustomRequirement;

import java.util.Map;
import java.util.UUID;

/**
 * PikaMug/Quests custom requirement that gates a quest behind a minimum job level.
 * Quest admins configure "Job ID" and "Min Level" when creating quests.
 */
public class JobLevelRequirement extends BukkitCustomRequirement {

    public JobLevelRequirement() {
        setName("Job Level");
        setAuthor("HorizonUtilities");
        addStringPrompt("Job ID",    "The job to check (e.g. miner)", "miner");
        addStringPrompt("Min Level", "Minimum level required",         "1");
    }

    @Override
    public boolean testRequirement(UUID uuid, Map<String, Object> data) {
        String jobId = String.valueOf(data.getOrDefault("Job ID", "miner"));
        int parsedLevel;
        try {
            parsedLevel = Integer.parseInt(String.valueOf(data.getOrDefault("Min Level", "1")));
        } catch (NumberFormatException e) {
            parsedLevel = 1;
        }
        final int minLevel = parsedLevel;
        return HorizonUtilitiesPlugin.getInstance()
                .getJobManager()
                .getPlayerJobs(uuid)
                .stream()
                .anyMatch(jp -> jp.getJobId().equalsIgnoreCase(jobId) && jp.getLevel() >= minLevel);
    }
}
