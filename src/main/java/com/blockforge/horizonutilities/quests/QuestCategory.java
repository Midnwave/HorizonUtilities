package com.blockforge.horizonutilities.quests;

public enum QuestCategory {
    JOB_DAILY("Daily Jobs", "\u26CF"),
    GENERAL_DAILY("Daily General", "\u2694"),
    WEEKLY("Weekly", "\uD83D\uDCC5"),
    CHALLENGE("Challenge", "\uD83C\uDFC6");

    private final String displayName;
    private final String icon;

    QuestCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon()        { return icon; }

    public boolean isDaily() {
        return this == JOB_DAILY || this == GENERAL_DAILY;
    }

    public boolean isWeekly() {
        return this == WEEKLY || this == CHALLENGE;
    }
}
