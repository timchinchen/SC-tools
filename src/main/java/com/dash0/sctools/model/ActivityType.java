package com.dash0.sctools.model;

/**
 * Enum representing the types of activities a Solution Consultant can log time against.
 */
public enum ActivityType {
    DEMO("Demo"),
    DISCOVERY("Discovery"),
    POV_WORK("POV Work"),
    TECHNICAL_DEEP_DIVE("Technical Deep Dive"),
    WORKSHOP("Workshop"),
    INTERNAL("Internal"),
    TRAINING("Training"),
    ADMIN("Admin"),
    OTHER("Other");

    private final String displayName;

    ActivityType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns a human-readable display name for the activity type.
     */
    public String getDisplayName() {
        return displayName;
    }
}
