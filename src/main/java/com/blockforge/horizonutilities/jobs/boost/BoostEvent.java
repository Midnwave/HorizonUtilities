package com.blockforge.horizonutilities.jobs.boost;

/**
 * Represents a server-wide or per-job income multiplier boost that was started
 * by an admin (or a scheduled event) and expires at a fixed timestamp.
 */
public class BoostEvent {

    private final int id;
    /** Null means the boost applies to ALL jobs. */
    private final String jobId;
    private final double multiplier;
    private final String startedBy;
    private final long startedAt;
    private final long endsAt;
    private final String reason;

    public BoostEvent(int id,
                      String jobId,
                      double multiplier,
                      String startedBy,
                      long startedAt,
                      long endsAt,
                      String reason) {
        this.id = id;
        this.jobId = jobId;
        this.multiplier = multiplier;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    /** Returns true if this boost is currently active. */
    public boolean isActive() {
        return System.currentTimeMillis() < endsAt;
    }

    /** Returns true if this boost has expired. */
    public boolean isExpired() {
        return !isActive();
    }

    /**
     * Returns true if this boost applies to the given job id. A boost with
     * {@code jobId == null} applies to all jobs.
     */
    public boolean appliesTo(String targetJobId) {
        return jobId == null || jobId.equalsIgnoreCase(targetJobId);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int getId()          { return id; }
    public String getJobId()    { return jobId; }
    public double getMultiplier() { return multiplier; }
    public String getStartedBy() { return startedBy; }
    public long getStartedAt()  { return startedAt; }
    public long getEndsAt()     { return endsAt; }
    public String getReason()   { return reason; }

    /** Remaining time in milliseconds (0 if expired). */
    public long getRemainingMs() {
        return Math.max(0, endsAt - System.currentTimeMillis());
    }

    @Override
    public String toString() {
        return "BoostEvent{id=" + id + ", jobId=" + jobId
                + ", multiplier=" + multiplier + ", endsAt=" + endsAt + '}';
    }
}
