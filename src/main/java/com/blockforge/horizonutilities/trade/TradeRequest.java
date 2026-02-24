package com.blockforge.horizonutilities.trade;

import java.util.UUID;

/**
 * Represents a pending trade request from one player to another.
 * Expires after 60 seconds.
 */
public class TradeRequest {

    private final UUID senderUuid;
    private final String senderName;
    private final UUID targetUuid;
    private final String targetName;
    private final long createdAt;
    private final long expiresAt;

    public TradeRequest(UUID senderUuid, String senderName, UUID targetUuid, String targetName) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + 60_000L;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public UUID getSenderUuid() { return senderUuid; }
    public String getSenderName() { return senderName; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
}
