package com.blockforge.horizonutilities.jobs;

/**
 * Represents the reward configuration for a single action+material combination
 * within a job definition (e.g. BREAK STONE gives 0.5 money and 1.0 XP).
 */
public class JobActionEntry {

    private final String material;
    private final double money;
    private final double xp;
    private final boolean spawnerAllowed;

    public JobActionEntry(String material, double money, double xp, boolean spawnerAllowed) {
        this.material = material;
        this.money = money;
        this.xp = xp;
        this.spawnerAllowed = spawnerAllowed;
    }

    /** Convenience constructor — spawnerAllowed defaults to true. */
    public JobActionEntry(String material, double money, double xp) {
        this(material, money, xp, true);
    }

    public String getMaterial() {
        return material;
    }

    public double getMoney() {
        return money;
    }

    public double getXp() {
        return xp;
    }

    public boolean isSpawnerAllowed() {
        return spawnerAllowed;
    }

    @Override
    public String toString() {
        return "JobActionEntry{material='" + material + "', money=" + money
                + ", xp=" + xp + ", spawnerAllowed=" + spawnerAllowed + '}';
    }
}
