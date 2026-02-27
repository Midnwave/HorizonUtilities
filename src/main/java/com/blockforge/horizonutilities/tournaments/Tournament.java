package com.blockforge.horizonutilities.tournaments;

import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single tournament instance, from creation through completion.
 */
public class Tournament {

    public enum Status { OPEN, ACTIVE, ENDED }

    private final String id;
    private final String name;
    private final UUID creatorUuid;
    private final TournamentObjectiveType objective;
    /** Optional: specific material/entity name to target. Empty = any. */
    private final String targetMaterial;
    /** Score needed to win (first-to-target). -1 = timed, highest score wins. */
    private final int targetScore;
    /** Duration in seconds. -1 = no time limit (ends at targetScore). */
    private final int durationSeconds;
    /** Optional reward items. */
    private final List<ItemStack> rewards;
    /** Money reward. */
    private final double rewardMoney;

    private Status status;
    private long startedAt;
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();

    public Tournament(String id, String name, UUID creatorUuid,
                      TournamentObjectiveType objective, String targetMaterial,
                      int targetScore, int durationSeconds,
                      List<ItemStack> rewards, double rewardMoney) {
        this.id             = id;
        this.name           = name;
        this.creatorUuid    = creatorUuid;
        this.objective      = objective;
        this.targetMaterial = targetMaterial != null ? targetMaterial.toUpperCase(Locale.ROOT) : "";
        this.targetScore    = targetScore;
        this.durationSeconds = durationSeconds;
        this.rewards        = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>();
        this.rewardMoney    = rewardMoney;
        this.status         = Status.OPEN;
    }

    public void start() {
        this.status    = Status.ACTIVE;
        this.startedAt = System.currentTimeMillis();
    }

    public void end() { this.status = Status.ENDED; }

    public void join(UUID uuid)  { participants.add(uuid); scores.putIfAbsent(uuid, 0); }
    public void leave(UUID uuid) { participants.remove(uuid); scores.remove(uuid); }

    public void addScore(UUID uuid, int amount) {
        scores.merge(uuid, amount, Integer::sum);
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    /** Returns participants sorted by score descending. */
    public List<Map.Entry<UUID, Integer>> getLeaderboard() {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();
    }

    /** Returns the UUID with the highest score, or null if no participants. */
    public UUID getWinner() {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public boolean isExpired() {
        if (status != Status.ACTIVE || durationSeconds < 0) return false;
        return System.currentTimeMillis() - startedAt >= durationSeconds * 1000L;
    }

    public long getRemainingSeconds() {
        if (status != Status.ACTIVE || durationSeconds < 0) return -1;
        long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
        return Math.max(0, durationSeconds - elapsed);
    }

    public String getId()                      { return id; }
    public String getName()                    { return name; }
    public UUID getCreatorUuid()               { return creatorUuid; }
    public TournamentObjectiveType getObjective() { return objective; }
    public String getTargetMaterial()          { return targetMaterial; }
    public int getTargetScore()                { return targetScore; }
    public int getDurationSeconds()            { return durationSeconds; }
    public List<ItemStack> getRewards()        { return Collections.unmodifiableList(rewards); }
    public double getRewardMoney()             { return rewardMoney; }
    public Status getStatus()                  { return status; }
    public long getStartedAt()                 { return startedAt; }
    public Set<UUID> getParticipants()         { return Collections.unmodifiableSet(participants); }
    public Map<UUID, Integer> getScores()      { return Collections.unmodifiableMap(scores); }
}
