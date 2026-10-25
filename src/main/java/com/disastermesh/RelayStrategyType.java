package com.disastermesh;

public enum RelayStrategyType {
    DUAL_TRAJECTORY_PREDICTIVE("Dual-Trajectory Predictive (Proposed)", "Joint battery drain and relative mobility trajectory prediction."),
    SHORTEST_PATH_BASELINE("Shortest-Path Baseline", "Greedy geometric progress towards destination, ignoring node health."),
    BATTERY_ONLY_BASELINE("Battery-Only Selection", "Selects candidate with highest battery level, ignoring link mobility."),
    MOBILITY_ONLY_BASELINE("Mobility-Only Selection", "Selects candidate with longest link survival time, ignoring battery.");

    private final String title;
    private final String description;

    RelayStrategyType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }

    public static RelayStrategyType fromString(String val) {
        if (val == null || val.isBlank()) return DUAL_TRAJECTORY_PREDICTIVE;
        String clean = val.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (RelayStrategyType t : values()) {
            if (t.name().equalsIgnoreCase(clean) || t.title.equalsIgnoreCase(val.trim())) return t;
        }
        if (clean.contains("SHORT")) return SHORTEST_PATH_BASELINE;
        if (clean.contains("BATT")) return BATTERY_ONLY_BASELINE;
        if (clean.contains("MOBIL")) return MOBILITY_ONLY_BASELINE;
        if (clean.contains("DUAL") || clean.contains("PREDICT")) return DUAL_TRAJECTORY_PREDICTIVE;
        return DUAL_TRAJECTORY_PREDICTIVE;
    }
}
