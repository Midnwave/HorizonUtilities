package com.blockforge.horizonutilities.bounty;

import java.util.List;
import java.util.UUID;

/**
 * Immutable data model representing a single bounty record.
 */
public class Bounty {

    private int id;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID setterUuid;
    private final String setterName;
    private final double amount;
    private final boolean anonymous;
    private final long createdAt;
    private final long expiresAt;
    private String status; // "ACTIVE", "CLAIMED", "EXPIRED"
    private UUID claimedByUuid;
    private String claimedByName;
    private Long claimedAt;

    public Bounty(int id, UUID targetUuid, String targetName,
                  UUID setterUuid, String setterName,
                  double amount, boolean anonymous,
                  long createdAt, long expiresAt,
                  String status,
                  UUID claimedByUuid, String claimedByName, Long claimedAt) {
        this.id           = id;
        this.targetUuid   = targetUuid;
        this.targetName   = targetName;
        this.setterUuid   = setterUuid;
        this.setterName   = setterName;
        this.amount       = amount;
        this.anonymous    = anonymous;
        this.createdAt    = createdAt;
        this.expiresAt    = expiresAt;
        this.status       = status;
        this.claimedByUuid = claimedByUuid;
        this.claimedByName = claimedByName;
        this.claimedAt    = claimedAt;
    }

    /** Convenience constructor for creating a new bounty before DB insertion. */
    public Bounty(UUID targetUuid, String targetName,
                  UUID setterUuid, String setterName,
                  double amount, boolean anonymous,
                  long createdAt, long expiresAt) {
        this(0, targetUuid, targetName, setterUuid, setterName,
             amount, anonymous, createdAt, expiresAt,
             "ACTIVE", null, null, null);
    }

    // -------------------------------------------------------------------------
    // Business logic
    // -------------------------------------------------------------------------

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status) && !isExpired();
    }

    /**
     * Returns the display name for the setter.
     * Returns "Anonymous" if the bounty was placed anonymously.
     */
    public String getDisplaySetterName() {
        return anonymous ? "Anonymous" : setterName;
    }

    /**
     * Sums the amount of all bounties in the given list.
     */
    public static double getTotalValue(List<Bounty> bounties) {
        double total = 0.0;
        for (Bounty b : bounties) {
            total += b.amount;
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public int getId()             { return id; }
    public void setId(int id)      { this.id = id; }

    public UUID getTargetUuid()    { return targetUuid; }
    public String getTargetName()  { return targetName; }
    public UUID getSetterUuid()    { return setterUuid; }
    public String getSetterName()  { return setterName; }
    public double getAmount()      { return amount; }
    public boolean isAnonymous()   { return anonymous; }
    public long getCreatedAt()     { return createdAt; }
    public long getExpiresAt()     { return expiresAt; }

    public String getStatus()            { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getClaimedByUuid()               { return claimedByUuid; }
    public void setClaimedByUuid(UUID uuid)      { this.claimedByUuid = uuid; }

    public String getClaimedByName()             { return claimedByName; }
    public void setClaimedByName(String name)    { this.claimedByName = name; }

    public Long getClaimedAt()                   { return claimedAt; }
    public void setClaimedAt(Long claimedAt)     { this.claimedAt = claimedAt; }
}
