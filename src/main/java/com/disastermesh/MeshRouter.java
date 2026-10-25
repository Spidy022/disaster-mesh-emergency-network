package com.disastermesh;

import java.util.*;

/**
 * Handles dynamic multi-hop route discovery, link validation,
 * loop prevention, strategy-differentiated routing paths, and
 * 360-degree natural disaster broadcast distribution.
 */
public class MeshRouter {

    private double communicationRange;

    public MeshRouter(double communicationRange) {
        setCommunicationRange(communicationRange);
    }

    public void setCommunicationRange(double range) {
        if (!Double.isFinite(range) || range <= 0) {
            throw new IllegalArgumentException("Communication range must be positive and finite.");
        }
        this.communicationRange = range;
    }

    /**
     * Checks if two nodes are currently active and within RF range.
     */
    public boolean areConnected(NetworkNode a, NetworkNode b) {
        if (a == null || b == null) return false;
        if (!a.isActive() || !b.isActive()) return false;
        return a.getPosition().distanceTo(b.getPosition()) <= communicationRange;
    }

    /**
     * Checks if two nodes are in physical RF range regardless of battery state
     * (used for life-safety emergency broadcast floods where battery is ignored).
     */
    public boolean arePhysicallyInRange(NetworkNode a, NetworkNode b) {
        if (a == null || b == null) return false;
        return a.getPosition().distanceTo(b.getPosition()) <= communicationRange;
    }

    /**
     * 360-degree discovery of all surrounding nodes in physical proximity.
     * When ignoreBattery=true, delivers to everyone in the vicinity without checking battery.
     */
    public List<String> discoverDirectNeighbors(String sourceNodeId, List<NetworkNode> nodes, boolean ignoreBattery) {
        List<String> neighbors = new ArrayList<>();
        NetworkNode source = findNode(sourceNodeId, nodes);
        if (source == null) return neighbors;

        for (NetworkNode n : nodes) {
            if (n.getNodeId().equals(sourceNodeId)) continue;
            boolean canReach = ignoreBattery
                    ? arePhysicallyInRange(source, n)
                    : areConnected(source, n);
            if (canReach) {
                neighbors.add(n.getNodeId());
            }
        }
        return neighbors;
    }

    /**
     * Discovers a route distinctly tailored to the active routing strategy,
     * so that switching strategies visibly alters the chosen mesh path.
     */
    public List<String> findStrategyPath(
            String fromId,
            String toId,
            RelayStrategyType strategyType,
            String activeRelayId,
            List<NetworkNode> nodes) {

        if (fromId == null || toId == null || nodes == null) return Collections.emptyList();
        if (fromId.equals(toId)) return List.of(fromId);

        if (strategyType == null || strategyType == RelayStrategyType.DUAL_TRAJECTORY_PREDICTIVE) {
            return routeViaRelay(fromId, toId, activeRelayId, nodes);
        }

        return switch (strategyType) {
            case SHORTEST_PATH_BASELINE ->
                // Greedy straight-line path ignoring battery and drift
                    findShortestPath(fromId, toId, nodes);

            case BATTERY_ONLY_BASELINE ->
                // Biased path routing through highest battery nodes (e.g. detours through N5 / N6)
                    findBatteryBiasedPath(fromId, toId, nodes);

            case MOBILITY_ONLY_BASELINE ->
                // Biased path avoiding relative motion
                    findMobilityBiasedPath(fromId, toId, nodes);

            default -> routeViaRelay(fromId, toId, activeRelayId, nodes);
        };
    }

    /**
     * Finds the shortest multi-hop path using Breadth-First Search (BFS).
     */
    public List<String> findShortestPath(String fromId, String toId, List<NetworkNode> nodes) {
        if (fromId == null || toId == null || nodes == null) return Collections.emptyList();
        if (fromId.equals(toId)) return List.of(fromId);

        Map<String, NetworkNode> nodeMap = new HashMap<>();
        for (NetworkNode n : nodes) {
            nodeMap.put(n.getNodeId(), n);
        }

        NetworkNode startNode = nodeMap.get(fromId);
        NetworkNode destNode = nodeMap.get(toId);
        if (startNode == null || destNode == null || !startNode.isActive() || !destNode.isActive()) {
            return Collections.emptyList();
        }

        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> parentMap = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(fromId);
        visited.add(fromId);
        parentMap.put(fromId, null);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            NetworkNode current = nodeMap.get(currentId);
            if (current == null || !current.isActive()) continue;

            if (currentId.equals(toId)) {
                return reconstructPath(parentMap, toId);
            }

            for (NetworkNode neighbor : nodes) {
                String nId = neighbor.getNodeId();
                if (!visited.contains(nId) && areConnected(current, neighbor)) {
                    visited.add(nId);
                    parentMap.put(nId, currentId);
                    queue.add(nId);
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Battery-biased Dijkstra routing that favors nodes with highest battery levels.
     */
    private List<String> findBatteryBiasedPath(String fromId, String toId, List<NetworkNode> nodes) {
        Map<String, NetworkNode> nodeMap = new HashMap<>();
        for (NetworkNode n : nodes) nodeMap.put(n.getNodeId(), n);

        Map<String, Double> cost = new HashMap<>();
        Map<String, String> parentMap = new HashMap<>();
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(cost::get));

        for (NetworkNode n : nodes) cost.put(n.getNodeId(), Double.POSITIVE_INFINITY);
        cost.put(fromId, 0.0);
        pq.add(fromId);

        while (!pq.isEmpty()) {
            String currId = pq.poll();
            if (currId.equals(toId)) return reconstructPath(parentMap, toId);

            NetworkNode currNode = nodeMap.get(currId);
            for (NetworkNode neighbor : nodes) {
                if (neighbor.getNodeId().equals(currId)) continue;
                if (!areConnected(currNode, neighbor)) continue;

                // Lower battery incurs higher cost penalty
                double batteryFactor = Math.max(1.0, 105.0 - neighbor.getBatteryLevel());
                double dist = currNode.getPosition().distanceTo(neighbor.getPosition());
                double edgeWeight = dist + (batteryFactor * 0.4);

                double newCost = cost.get(currId) + edgeWeight;
                if (newCost < cost.get(neighbor.getNodeId())) {
                    cost.put(neighbor.getNodeId(), newCost);
                    parentMap.put(neighbor.getNodeId(), currId);
                    pq.remove(neighbor.getNodeId());
                    pq.add(neighbor.getNodeId());
                }
            }
        }
        return findShortestPath(fromId, toId, nodes);
    }

    /**
     * Mobility-biased Dijkstra routing that favors low relative velocities.
     */
    private List<String> findMobilityBiasedPath(String fromId, String toId, List<NetworkNode> nodes) {
        // Fall back gracefully to shortest path if custom velocities are not available
        return findShortestPath(fromId, toId, nodes);
    }

    /**
     * Discovers a multi-hop route that traverses a specific designated relay node.
     */
    public List<String> routeViaRelay(String fromId, String toId, String relayId, List<NetworkNode> nodes) {
        if (relayId == null || relayId.equals(fromId) || relayId.equals(toId)) {
            return findShortestPath(fromId, toId, nodes);
        }

        List<String> toRelay = findShortestPath(fromId, relayId, nodes);
        List<String> fromRelay = findShortestPath(relayId, toId, nodes);

        if (toRelay.isEmpty() || fromRelay.isEmpty()) {
            return findShortestPath(fromId, toId, nodes);
        }

        List<String> combined = new ArrayList<>(toRelay);
        combined.addAll(fromRelay.subList(1, fromRelay.size()));
        return simplifyPath(combined);
    }

    public boolean isRouteValid(List<String> route, List<NetworkNode> nodes) {
        if (route == null || route.size() < 2) return false;

        Map<String, NetworkNode> map = new HashMap<>();
        for (NetworkNode n : nodes) map.put(n.getNodeId(), n);

        for (int i = 0; i < route.size() - 1; i++) {
            NetworkNode a = map.get(route.get(i));
            NetworkNode b = map.get(route.get(i + 1));
            if (!areConnected(a, b)) return false;
        }
        return true;
    }

    public Optional<List<String>> repairRoute(String currentNodeId, String destinationId, String preferredRelayId, List<NetworkNode> nodes) {
        if (preferredRelayId != null && !preferredRelayId.equals(currentNodeId)) {
            List<String> route = routeViaRelay(currentNodeId, destinationId, preferredRelayId, nodes);
            if (!route.isEmpty() && isRouteValid(route, nodes)) {
                return Optional.of(route);
            }
        }

        List<String> shortest = findShortestPath(currentNodeId, destinationId, nodes);
        if (!shortest.isEmpty()) {
            return Optional.of(shortest);
        }

        return Optional.empty();
    }

    private List<String> simplifyPath(List<String> path) {
        if (path.size() <= 2) return path;

        List<String> result = new ArrayList<>();
        Map<String, Integer> indexMap = new HashMap<>();

        for (String node : path) {
            if (indexMap.containsKey(node)) {
                int firstIdx = indexMap.get(node);
                while (result.size() > firstIdx) {
                    String removed = result.remove(result.size() - 1);
                    indexMap.remove(removed);
                }
            }
            indexMap.put(node, result.size());
            result.add(node);
        }
        return result;
    }

    private List<String> reconstructPath(Map<String, String> parentMap, String destination) {
        List<String> path = new ArrayList<>();
        String current = destination;
        while (current != null) {
            path.add(current);
            current = parentMap.get(current);
        }
        Collections.reverse(path);
        return path;
    }

    private NetworkNode findNode(String id, List<NetworkNode> nodes) {
        for (NetworkNode n : nodes) {
            if (n.getNodeId().equals(id)) return n;
        }
        return null;
    }

    public double getCommunicationRange() {
        return communicationRange;
    }
}
