package com.blockforge.horizonutilities.homes;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

/** Immutable snapshot of a player home. */
public class Home {

    private final int id;
    private final UUID ownerUuid;
    private final String name;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private final long createdAt;

    public Home(int id, UUID ownerUuid, String name,
                String world, double x, double y, double z, float yaw, float pitch,
                long createdAt) {
        this.id        = id;
        this.ownerUuid = ownerUuid;
        this.name      = name;
        this.world     = world;
        this.x         = x;
        this.y         = y;
        this.z         = z;
        this.yaw       = yaw;
        this.pitch     = pitch;
        this.createdAt = createdAt;
    }

    public Location toLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    public int getId()           { return id; }
    public UUID getOwnerUuid()   { return ownerUuid; }
    public String getName()      { return name; }
    public String getWorld()     { return world; }
    public double getX()         { return x; }
    public double getY()         { return y; }
    public double getZ()         { return z; }
    public float getYaw()        { return yaw; }
    public float getPitch()      { return pitch; }
    public long getCreatedAt()   { return createdAt; }
}
