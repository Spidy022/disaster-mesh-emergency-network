package com.disastermesh;

/**
 * Physical wireless transport modes supported by the disaster mesh network.
 * Allows testing and visual simulation across Wi-Fi Direct, Bluetooth 5.0 Mesh, and LoRa Radio.
 */
public enum TransportMode {
    WIFI_DIRECT(
            "Wi-Fi Direct (P2P)",
            42.0,
            0.85,
            54.0,
            "High throughput (54 Mbps), medium range (42m). High battery consumption."
    ),
    BLUETOOTH_MESH(
            "Bluetooth 5.0 Mesh",
            36.0,
            0.35,
            2.0,
            "Low energy (2 Mbps), mesh range (36m). Ultra-low drain, high node density."
    ),
    LORA_LONG_RANGE(
            "LoRa Long-Range RF (915 MHz)",
            72.0,
            0.15,
            0.025,
            "Extended disaster coverage (72m simulated), penetrates structural debris. Low bitrate (25 kbps)."
    );

    private final String label;
    private final double rangeUnits;
    private final double drainMultiplier;
    private final double bandwidthMbps;
    private final String description;

    TransportMode(String label, double rangeUnits, double drainMultiplier, double bandwidthMbps, String description) {
        this.label = label;
        this.rangeUnits = rangeUnits;
        this.drainMultiplier = drainMultiplier;
        this.bandwidthMbps = bandwidthMbps;
        this.description = description;
    }

    public String getLabel() { return label; }
    public double getRangeUnits() { return rangeUnits; }
    public double getDrainMultiplier() { return drainMultiplier; }
    public double getBandwidthMbps() { return bandwidthMbps; }
    public String getDescription() { return description; }

    public static TransportMode fromString(String val) {
        if (val == null) return WIFI_DIRECT;
        for (TransportMode m : values()) {
            if (m.name().equalsIgnoreCase(val.trim()) || m.label.equalsIgnoreCase(val.trim())) {
                return m;
            }
        }
        return WIFI_DIRECT;
    }
}
