package com.disastermesh;

import java.util.List;

/**
 * Transport abstraction layer for offline disaster communication.
 * Provides a clean interface separating higher-layer message routing and handover logic
 * from physical radio link technologies (Wi-Fi Direct, Bluetooth Low Energy, LoRa mesh, or Simulation).
 */
public interface OfflineMeshTransport {

    String getTransportType();

    boolean isRadioEnabled();

    boolean sendPacket(String sourceId, String nextHopId, String packetJson);

    List<String> discoverNeighbors(String localNodeId, double searchRange);

    double getLinkSignalQuality(String fromNodeId, String toNodeId);

    void registerIncomingPacketListener(IncomingPacketHandler handler);

    @FunctionalInterface
    interface IncomingPacketHandler {
        void handlePacket(String fromId, String toId, String packetJson);
    }
}
