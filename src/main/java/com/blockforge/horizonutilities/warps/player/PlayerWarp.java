package com.blockforge.horizonutilities.warps.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

/** Immutable snapshot of a player warp row from the database. */
public class PlayerWarp {

    private final int id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String name;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private final long createdAt;
    private int visits;
    private double averageRating;
    private int ratingCount;

    public PlayerWarp(int id, UUID ownerUuid, String ownerName, String name,
                      String world, double x, double y, double z, float yaw, float pitch,
                      long createdAt, int visits, double averageRating, int ratingCount) {
        this.id            = id;
        this.ownerUuid     = ownerUuid;
        this.ownerName     = ownerName;
        this.name          = name;
        this.world         = world;
        this.x             = x;
        this.y             = y;
        this.z             = z;
        this.yaw           = yaw;
        this.pitch         = pitch;
        this.createdAt     = createdAt;
        this.visits        = visits;
        this.averageRating = averageRating;
        this.ratingCount   = ratingCount;
    }

    public Location toLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    /** Returns a star display string like "★★★☆☆ (12)" */
    public String getStarDisplay() {
        int stars = (int) Math.round(averageRating);
        String filled = "★".repeat(Math.max(0, stars));
        String empty  = "☆".repeat(Math.max(0, 5 - stars));
        return filled + empty + " (" + ratingCount + ")";
    }

    public int getId()               { return id; }
    public UUID getOwnerUuid()       { return ownerUuid; }
    public String getOwnerName()     { return ownerName; }
    public String getName()          { return name; }
    public String getWorld()         { return world; }
    public double getX()             { return x; }
    public double getY()             { return y; }
    public double getZ()             { return z; }
    public float getYaw()            { return yaw; }
    public float getPitch()          { return pitch; }
    public long getCreatedAt()       { return createdAt; }
    public int getVisits()           { return visits; }
    public double getAverageRating() { return averageRating; }
    public int getRatingCount()      { return ratingCount; }

    public void incrementVisits()    { visits++; }
    public void setAverageRating(double r) { averageRating = r; }
    public void setRatingCount(int c)      { ratingCount = c; }
}
