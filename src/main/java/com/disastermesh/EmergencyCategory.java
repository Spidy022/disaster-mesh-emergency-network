package com.disastermesh;

/**
 * Standard disaster response categories for triage and dispatch.
 */
public enum EmergencyCategory {
    RESCUE("Search & Rescue"),
    MEDICAL("Medical Emergency"),
    FIRE("Active Fire"),
    TRAPPED_PERSON("Trapped Person"),
    SUPPLY_REQUEST("Supplies & Rations"),
    GENERAL_SOS("General SOS");

    private final String label;

    EmergencyCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static EmergencyCategory fromString(String val) {
        if (val == null) return GENERAL_SOS;
        String clean = val.trim().replace(" ", "_").toUpperCase();
        for (EmergencyCategory c : values()) {
            if (c.name().equalsIgnoreCase(clean) || c.label.equalsIgnoreCase(val.trim())) return c;
        }
        return GENERAL_SOS;
    }
}
