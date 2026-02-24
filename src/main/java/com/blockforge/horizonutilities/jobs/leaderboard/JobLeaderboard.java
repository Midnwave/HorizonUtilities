package com.blockforge.horizonutilities.jobs.leaderboard;

import com.blockforge.horizonutilities.jobs.JobPlayer;
import com.blockforge.horizonutilities.jobs.JobStorageManager;

import java.util.List;
import java.util.Map;

/**
 * Thin facade over {@link JobStorageManager} for leaderboard queries.
 */
public class JobLeaderboard {

    private final JobStorageManager storage;

    public JobLeaderboard(JobStorageManager storage) {
        this.storage = storage;
    }

    /**
     * Returns the top {@code limit} players in the given job, ordered by
     * prestige desc, level desc, xp desc.
     */
    public List<JobPlayer> getTopByJob(String jobId, int limit) {
        return storage.getTopPlayers(jobId, limit);
    }

    /**
     * Returns the top {@code limit} players across all jobs, ranked by their
     * total combined level. Each entry is playerName -&gt; totalLevel.
     */
    public List<Map.Entry<String, Integer>> getTopOverall(int limit) {
        return storage.getTopOverall(limit);
    }
}
