package com.disastermesh;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory simulated RF propagation transport for unit testing and local simulation.
 * Calculates link viability based on node coordinates and RF range threshold.
 */
public class SimulatedMeshTransport implements OfflineMeshTransport {

    private final MeshRouter router;
    private final List<NetworkNode> nodes;
    private IncomingPacketHandler handler;

    public SimulatedMeshTransport(MeshRouter router, List<NetworkNode> nodes) {
        this.router = router;
        this.nodes = nodes;
    }

    @Override
    public String getTransportType() {
        return "SIMULATED_RADIO_PROPAGATION";
    }

    @Override
    public boolean isRadioEnabled() {
        return false; // Honest indicator: this is simulated, not real hardware radio
    }

    @Override
    public boolean sendPacket(String sourceId, String nextHopId, String packetJson) {
        NetworkNode src = findNode(sourceId);
        NetworkNode dst = findNode(nextHopId);
        if (src == null || dst == null || !router.areConnected(src, dst)) {
            return false;
        }
        if (handler != null) {
            handler.handlePacket(sourceId, nextHopId, packetJson);
        }
        return true;
    }

    @Override
    public List<String> discoverNeighbors(String localNodeId, double searchRange) {
        List<String> neighbors = new ArrayList<>();
        NetworkNode local = findNode(localNodeId);
        if (local == null || !local.isActive()) return neighbors;

        for (NetworkNode n : nodes) {
            if (!n.getNodeId().equals(localNodeId) && n.isActive()
                    && local.getPosition().distanceTo(n.getPosition()) <= searchRange) {
                neighbors.add(n.getNodeId());
            }
        }
        return neighbors;
    }

    @Override
    public double getLinkSignalQuality(String fromNodeId, String toNodeId) {
        NetworkNode a = findNode(fromNodeId);
        NetworkNode b = findNode(toNodeId);
        if (a == null || b == null || !a.isActive() || !b.isActive()) return 0.0;
        double dist = a.getPosition().distanceTo(b.getPosition());
        if (dist > router.getCommunicationRange()) return 0.0;
        // Signal quality decays with distance
        return Math.max(0.0, 1.0 - (dist / router.getCommunicationRange()));
    }

    @Override
    public void registerIncomingPacketListener(IncomingPacketHandler handler) {
        this.handler = handler;
    }

    private NetworkNode findNode(String id) {
        for (NetworkNode n : nodes) {
            if (n.getNodeId().equals(id)) return n;
        }
        return null;
    }
}
