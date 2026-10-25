package com.disastermesh;

/**
 * Priority levels for emergency messages.
 * Critical messages receive scheduling priority over normal traffic.
 */
public enum EmergencyPriority {
    CRITICAL(1),
    HIGH(2),
    NORMAL(3);

    private final int level;

    EmergencyPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static EmergencyPriority fromString(String val) {
        if (val == null) return NORMAL;
        for (EmergencyPriority p : values()) {
            if (p.name().equalsIgnoreCase(val.trim())) return p;
        }
        return NORMAL;
    }
}
