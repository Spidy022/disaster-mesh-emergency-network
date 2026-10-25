package com.disastermesh;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs a time-stepped disaster mesh network simulation.
 * Integrates dynamic topology recalculation, battery/mobility prediction,
 * pluggable relay selection strategies, proactive handover, and
 * multi-hop emergency message lifecycle management.
 */
public class SimulationEngine {

    private final SimulationConfig config;
    private final List<NetworkNode> nodes;
    private final Map<String, Double> batteryDrainRates;
    private final Map<String, Velocity> velocities;

    private final BatteryPredictor batteryPredictor;
    private final MobilityPredictor mobilityPredictor;
    private final RiskCalculator riskCalculator;
    private final RelaySelector relaySelector;
    private final HandoverManager handoverManager;
    private final MeshRouter router;

    private final List<SimulationResult> results = new ArrayList<>();
    private final List<EmergencyMessage> messages = new CopyOnWriteArrayList<>();
    private final List<SimulationEvent> eventLog = new CopyOnWriteArrayList<>();
    private final List<SimulationEventListener> listeners = new CopyOnWriteArrayList<>();

    private int currentStep;
    private long nextEventId = 1;
    private Set<String> activeLinksSnapshot = new HashSet<>();
    private RelayStrategyType strategyType = RelayStrategyType.DUAL_TRAJECTORY_PREDICTIVE;
    private TransportMode transportMode = TransportMode.WIFI_DIRECT;
    private int ticksPerHop = 4; // Default 4 clicks in Normal mode, 2 in Fast mode

    public record MeshLink(String a, String b) {
        public boolean involves(String nodeId) {
            return a.equals(nodeId) || b.equals(nodeId);
        }
    }

    public SimulationEngine(
            SimulationConfig config,
            List<NetworkNode> nodes,
            Map<String, Double> batteryDrainRates,
            Map<String, Velocity> velocities,
            NetworkNode initialRelay) {

        if (config == null || nodes == null || batteryDrainRates == null || velocities == null || initialRelay == null) {
            throw new IllegalArgumentException("Simulation inputs cannot be null.");
        }

        if (nodes.isEmpty() || !nodes.contains(initialRelay)) {
            throw new IllegalArgumentException("Initial relay must be part of the network.");
        }

        this.config = config;
        this.nodes = new ArrayList<>(nodes);
        this.batteryDrainRates = new HashMap<>(batteryDrainRates);
        this.velocities = new HashMap<>(velocities);

        this.batteryPredictor = new BatteryPredictor(config.minimumRelayBattery());
        this.mobilityPredictor = new MobilityPredictor();
        this.riskCalculator = new RiskCalculator(
                config.batteryWeight(),
                config.mobilityWeight(),
                config.predictionHorizonMinutes()
        );
        this.relaySelector = new RelaySelector(
                riskCalculator,
                config.minimumRelayBattery(),
                config.communicationRange()
        );
        this.handoverManager = new HandoverManager(
                initialRelay,
                relaySelector,
                config.handoverRiskThreshold()
        );
        this.router = new MeshRouter(config.communicationRange());
        this.currentStep = 0;

        validateNodeParameters();
        updateActiveLinksSnapshot();
        publishEvent(SimulationEventType.INITIALIZED, initialRelay.getNodeId(), null, "Disaster mesh initialized with 8 nodes.", 0.0);
    }

    private void validateNodeParameters() {
        for (NetworkNode node : nodes) {
            Double drainRate = batteryDrainRates.get(node.getNodeId());
            if (drainRate == null || !Double.isFinite(drainRate) || drainRate < 0) {
                throw new IllegalArgumentException("Invalid battery drain rate for " + node.getNodeId());
            }

            if (!velocities.containsKey(node.getNodeId()) || velocities.get(node.getNodeId()) == null) {
                throw new IllegalArgumentException("Missing velocity for " + node.getNodeId());
            }
        }
    }

    /**
     * Executes one time-stepped simulation tick.
     */
    public synchronized SimulationResult step() {
        if (currentStep >= config.totalSteps()) {
            throw new IllegalStateException("Simulation has completed all configured steps.");
        }

        if (currentStep > 0) {
            updateNetwork();
            detectLinkChanges();
        }

        NetworkNode currentRelay = handoverManager.getCurrentRelay();
        String previousRelayId = currentRelay.getNodeId();

        Map<String, Double> timeToUnsuitable = new HashMap<>();
        Map<String, Double> timeToDisconnect = new HashMap<>();

        for (NetworkNode node : nodes) {
            double drainRate = batteryDrainRates.get(node.getNodeId());
            double batteryTime = batteryPredictor.predictTimeToUnsuitable(
                    node.getBatteryLevel(),
                    drainRate
            );
            timeToUnsuitable.put(node.getNodeId(), batteryTime / 60.0);

            Velocity velocity = velocities.get(node.getNodeId());
            Velocity relayVelocity = velocities.get(currentRelay.getNodeId());

            double disconnectTime;
            if (node.getNodeId().equals(currentRelay.getNodeId())) {
                disconnectTime = Double.POSITIVE_INFINITY;
            } else {
                disconnectTime = mobilityPredictor.predictTimeToDisconnect(
                        node.getPosition(),
                        velocity.x(),
                        velocity.y(),
                        currentRelay.getPosition(),
                        relayVelocity.x(),
                        relayVelocity.y(),
                        config.communicationRange()
                );
            }
            timeToDisconnect.put(node.getNodeId(), disconnectTime / 60.0);
        }

        double relayBatteryTime = timeToUnsuitable.get(currentRelay.getNodeId());
        double relayDisconnectTime = timeToDisconnect.get(currentRelay.getNodeId());

        double relayRisk = riskCalculator.calculateRisk(relayBatteryTime, relayDisconnectTime);
        if (currentRelay.getBatteryLevel() <= config.minimumRelayBattery()) {
            relayRisk = 1.0;
        }
        // If current relay has drifted out of communication range of source, risk is immediately 1.0 (disconnected)
        if (!router.areConnected(nodes.get(0), currentRelay)) {
            relayRisk = 1.0;
        }

        int activeMsgCount = (int) messages.stream().filter(m -> !m.isTerminal()).count();

        // Source node ingress position for relay reachability (e.g. Phone 1 or current packet holder)
        Position srcPos = nodes.get(0).getPosition();
        for (EmergencyMessage m : messages) {
            if (!m.isTerminal() && (m.getStatus() == MessageStatus.IN_TRANSIT || m.getStatus() == MessageStatus.REROUTING)) {
                NetworkNode curN = findNode(m.getCurrentNode());
                if (curN != null) {
                    srcPos = curN.getPosition();
                    break;
                }
            }
        }

        boolean handedOver = handoverManager.evaluateAndHandover(
                nodes,
                srcPos,
                nodes.get(nodes.size() - 1).getPosition(),
                timeToUnsuitable,
                timeToDisconnect,
                relayRisk,
                currentStep,
                currentStep * config.timeStepSeconds(),
                activeMsgCount
        );

        NetworkNode activeRelay = handoverManager.getCurrentRelay();

        if (handedOver) {
            publishEvent(
                    SimulationEventType.HANDOVER_OCCURRED,
                    previousRelayId,
                    activeRelay.getNodeId(),
                    String.format("Handover: %s -> %s (Relay risk: %.3f)", previousRelayId, activeRelay.getNodeId(), relayRisk),
                    relayRisk
            );
            rerouteActiveMessagesAfterHandover(previousRelayId, activeRelay.getNodeId());
        } else if (handoverManager.wasLastAttemptFailed()) {
            publishEvent(
                    SimulationEventType.HANDOVER_FAILED,
                    previousRelayId,
                    null,
                    "Warning: Relay risk exceeded threshold but no safe replacement is available.",
                    relayRisk
            );
        }

        // Advance multi-hop emergency messages
        advanceEmergencyMessages(activeRelay.getNodeId());

        String event = handedOver
                ? "HANDOVER"
                : handoverManager.wasLastAttemptFailed()
                ? "NO_SAFE_RELAY"
                : "MONITORING";

        SimulationResult result = new SimulationResult(
                currentStep,
                currentStep * config.timeStepSeconds(),
                activeRelay.getNodeId(),
                activeRelay.getBatteryLevel(),
                relayRisk,
                handoverManager.getHandoverCount(),
                event
        );

        results.add(result);
        printResult(result);
        currentStep++;

        return result;
    }

    private void updateNetwork() {
        double dt = config.timeStepSeconds();

        for (NetworkNode node : nodes) {
            double drainRate = batteryDrainRates.get(node.getNodeId());
            double newBattery = Math.max(0.0, node.getBatteryLevel() - drainRate * dt);
            node.updateBattery(newBattery);

            if (newBattery <= 0.0 && node.isActive()) {
                publishEvent(SimulationEventType.NODE_OFFLINE, node.getNodeId(), null, "Node battery exhausted. Node offline.", 1.0);
            } else if (newBattery <= config.minimumRelayBattery() && newBattery + drainRate * dt > config.minimumRelayBattery()) {
                publishEvent(SimulationEventType.NODE_BATTERY_LOW, node.getNodeId(), null, "Node reached critical battery threshold.", 0.9);
            }

            Velocity velocity = velocities.get(node.getNodeId());
            Position oldPosition = node.getPosition();
            Position newPosition = new Position(
                    oldPosition.x() + velocity.x() * dt,
                    oldPosition.y() + velocity.y() * dt
            );
            node.updatePosition(newPosition);
        }
    }

    private void detectLinkChanges() {
        Set<String> currentLinks = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                NetworkNode a = nodes.get(i);
                NetworkNode b = nodes.get(j);
                if (router.areConnected(a, b)) {
                    String linkKey = a.getNodeId() + "<->" + b.getNodeId();
                    currentLinks.add(linkKey);
                    if (!activeLinksSnapshot.contains(linkKey)) {
                        publishEvent(SimulationEventType.LINK_ESTABLISHED, a.getNodeId(), b.getNodeId(), "Communication link established: " + linkKey, 0.0);
                    }
                }
            }
        }

        for (String oldLink : activeLinksSnapshot) {
            if (!currentLinks.contains(oldLink)) {
                String[] parts = oldLink.split("<->");
                publishEvent(SimulationEventType.LINK_BROKEN, parts[0], parts[1], "Communication link broken: " + oldLink, 0.8);
            }
        }
        activeLinksSnapshot = currentLinks;
    }

    private void updateActiveLinksSnapshot() {
        activeLinksSnapshot.clear();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (router.areConnected(nodes.get(i), nodes.get(j))) {
                    activeLinksSnapshot.add(nodes.get(i).getNodeId() + "<->" + nodes.get(j).getNodeId());
                }
            }
        }
    }

    /**
     * Advances emergency message states along simulated multi-hop mesh paths
     * or propagates 360-degree natural disaster broadcast floods.
     */
    private void advanceEmergencyMessages(String currentRelayId) {
        List<EmergencyMessage> sorted = new ArrayList<>(messages);
        Collections.sort(sorted);

        for (EmergencyMessage msg : sorted) {
            if (msg.isTerminal()) continue;

            // Handle 360-degree Natural Disaster Broadcast Flood
            if (msg.isBroadcast()) {
                if (msg.getStatus() == MessageStatus.CREATED || msg.getStatus() == MessageStatus.QUEUED) {
                    msg.startTransit(List.of(msg.getSenderId()));
                    msg.addBroadcastRecipient(msg.getSenderId());
                    publishEvent(SimulationEventType.MESSAGE_FORWARDED, msg.getSenderId(), "ALL_SURROUNDING",
                            "🚨 360° LIFE-SAFETY SOS FLOOD INITIATED: Radiating outward to entire crowd without battery restrictions.", 0.0);
                }

                // Multi-hop crowd flood: every reached node propagates to all physical in-range neighbors
                Set<String> currentlyReached = new HashSet<>(msg.getBroadcastReachedNodes());
                Set<String> newlyReached = new HashSet<>();
                for (String reachedId : currentlyReached) {
                    List<String> inRange = router.discoverDirectNeighbors(reachedId, nodes, true);
                    newlyReached.addAll(inRange);
                }
                for (String nid : newlyReached) {
                    msg.addBroadcastRecipient(nid);
                }

                msg.advanceHopProgress();

                // If all phones reached or after 3 broadcast hops, flood is complete across the crowd
                if (msg.getBroadcastReachedNodes().size() >= nodes.size() || (currentStep - msg.getCreatedStep()) >= 3) {
                    for (NetworkNode n : nodes) {
                        msg.addBroadcastRecipient(n.getNodeId());
                    }
                    msg.deliver(currentStep * config.timeStepSeconds());
                    publishEvent(SimulationEventType.MESSAGE_DELIVERED, msg.getSenderId(), "ALL_SURROUNDING",
                            "360° SOS Flood confirmed delivered to ALL " + msg.getBroadcastReachedNodes().size() + " phones in mesh crowd.", 0.0);
                }
                continue;
            }

            // Handle Unicast Directed Message
            if (msg.getStatus() == MessageStatus.CREATED || msg.getStatus() == MessageStatus.QUEUED) {
                List<String> route = router.findStrategyPath(msg.getSenderId(), msg.getDestinationId(), strategyType, currentRelayId, nodes);
                if (route.isEmpty()) {
                    route = router.findShortestPath(msg.getSenderId(), msg.getDestinationId(), nodes);
                }
                if (route.isEmpty()) {
                    msg.fail("No connected route available to destination.");
                    publishEvent(SimulationEventType.MESSAGE_FAILED, msg.getSenderId(), msg.getDestinationId(),
                            "Message #" + msg.getMessageId() + " failed: No route.", 1.0);
                } else {
                    msg.setTicksPerHop(this.ticksPerHop);
                    msg.startTransit(route);
                    publishEvent(SimulationEventType.MESSAGE_FORWARDED, msg.getSenderId(), route.get(1),
                            "Message #" + msg.getMessageId() + " route (" + strategyType.name() + "): " + String.join("->", route), 0.0);
                }
            }

            if (msg.getStatus() == MessageStatus.IN_TRANSIT || msg.getStatus() == MessageStatus.REROUTING) {
                List<String> route = msg.getCurrentRoute();
                int currentHop = msg.getCurrentHop();

                if (currentHop >= route.size() - 1) {
                    if (msg.getCurrentNode().equals(msg.getDestinationId())) {
                        msg.deliver(currentStep * config.timeStepSeconds());
                        publishEvent(SimulationEventType.MESSAGE_DELIVERED, msg.getCurrentNode(), msg.getDestinationId(),
                                "Message #" + msg.getMessageId() + " ACK received by " + msg.getDestinationId(), 0.0);
                    } else {
                        msg.fail("Route ended before reaching destination.");
                    }
                    continue;
                }

                String currentNodeId = route.get(currentHop);
                String nextNodeId = route.get(currentHop + 1);
                NetworkNode currentN = findNode(currentNodeId);
                NetworkNode nextN = findNode(nextNodeId);

                // Pacing check: requires ticksPerHop (2 in Fast, 4 in Normal) to traverse the hop
                boolean hopArrivalReady = msg.advanceHopProgress();
                if (!hopArrivalReady) {
                    // Packet still actively propagating along the RF wireless link
                    msg.setNote(String.format("Propagating %s -> %s (%d%% of RF link traversed)",
                            currentNodeId, nextNodeId, (int) (msg.getHopProgressRatio() * 100)));
                    continue;
                }

                // Packet reached hop arrival threshold
                if (router.areConnected(currentN, nextN)) {
                    msg.advanceHop(nextNodeId);
                    if (msg.getStatus() == MessageStatus.DELIVERED) {
                        msg.setDeliveredAtSeconds(currentStep * config.timeStepSeconds());
                        publishEvent(SimulationEventType.MESSAGE_DELIVERED, currentNodeId, nextNodeId,
                                "Message #" + msg.getMessageId() + " delivered to destination " + nextNodeId, 0.0);
                    } else {
                        publishEvent(SimulationEventType.MESSAGE_FORWARDED, currentNodeId, nextNodeId,
                                "Message #" + msg.getMessageId() + " arrived at hop " + nextNodeId + "; proceeding.", 0.0);
                    }
                } else {
                    // Link broken! Attempt route repair
                    Optional<List<String>> repaired = router.repairRoute(currentNodeId, msg.getDestinationId(), currentRelayId, nodes);
                    if (repaired.isPresent() && repaired.get().size() >= 2) {
                        List<String> newRoute = repaired.get();
                        msg.recordHandover(nextNodeId, newRoute.get(1), newRoute);
                        publishEvent(SimulationEventType.MESSAGE_REROUTED, currentNodeId, newRoute.get(1),
                                "Route repair for message #" + msg.getMessageId() + ": " + String.join("->", newRoute), 0.5);
                    } else {
                        msg.fail("Link to " + nextNodeId + " broke and no alternate path exists.");
                        publishEvent(SimulationEventType.MESSAGE_FAILED, currentNodeId, msg.getDestinationId(),
                                "Message #" + msg.getMessageId() + " dropped: Network partition.", 1.0);
                    }
                }
            }
        }
    }

    private void rerouteActiveMessagesAfterHandover(String oldRelayId, String newRelayId) {
        for (EmergencyMessage msg : messages) {
            if (!msg.isTerminal() && msg.getCurrentRoute().contains(oldRelayId)) {
                Optional<List<String>> repaired = router.repairRoute(msg.getCurrentNode(), msg.getDestinationId(), newRelayId, nodes);
                if (repaired.isPresent()) {
                    msg.recordHandover(oldRelayId, newRelayId, repaired.get());
                    publishEvent(SimulationEventType.MESSAGE_REROUTED, msg.getCurrentNode(), newRelayId, "Proactive reroute for message #" + msg.getMessageId() + " via new relay " + newRelayId, 0.2);
                }
            }
        }
    }

    public synchronized EmergencyMessage submitMessage(EmergencyMessage msg) {
        if (msg == null) throw new IllegalArgumentException("Message cannot be null.");
        messages.add(0, msg);
        msg.queue();
        publishEvent(SimulationEventType.MESSAGE_QUEUED, msg.getSenderId(), msg.getDestinationId(), "Emergency message #" + msg.getMessageId() + " (" + msg.getPriority() + ") queued.", 0.0);
        return msg;
    }

    public synchronized EmergencyMessage createAndSubmitMessage(
            long messageId,
            String senderId,
            String destinationId,
            String text,
            EmergencyCategory category,
            EmergencyPriority priority) {

        EmergencyMessage msg = new EmergencyMessage(
                messageId,
                senderId,
                destinationId,
                text,
                category,
                priority,
                currentStep
        );
        msg.setTicksPerHop(this.ticksPerHop);
        submitMessage(msg);

        String currentRelayId = handoverManager.getCurrentRelay().getNodeId();
        List<String> route = router.findStrategyPath(senderId, destinationId, strategyType, currentRelayId, nodes);
        if (route.isEmpty()) {
            route = router.findShortestPath(senderId, destinationId, nodes);
        }
        if (!route.isEmpty()) {
            msg.startTransit(route);
        }
        return msg;
    }

    public synchronized EmergencyMessage createAndSubmitBroadcastMessage(
            long messageId,
            String senderId,
            String text,
            EmergencyCategory category,
            EmergencyPriority priority) {

        EmergencyMessage msg = new EmergencyMessage(
                messageId,
                senderId,
                "ALL_SURROUNDING",
                text,
                category,
                priority,
                currentStep,
                true
        );
        msg.setTicksPerHop(this.ticksPerHop);
        return submitMessage(msg);
    }

    public void setTransportMode(TransportMode mode) {
        if (mode != null) {
            this.transportMode = mode;
            this.router.setCommunicationRange(mode.getRangeUnits());
            updateActiveLinksSnapshot();
            publishEvent(SimulationEventType.STEP_COMPLETED, null, null,
                    "Wireless transport switched to " + mode.getLabel() + " (Range: " + mode.getRangeUnits() + "m)", 0.0);
        }
    }

    public TransportMode getTransportMode() {
        return transportMode;
    }

    public void setTicksPerHop(int ticks) {
        this.ticksPerHop = Math.max(1, ticks);
        for (EmergencyMessage msg : messages) {
            msg.setTicksPerHop(this.ticksPerHop);
        }
    }

    public int getTicksPerHop() {
        return ticksPerHop;
    }

    public void setStrategy(RelayStrategyType type) {
        this.strategyType = type != null ? type : RelayStrategyType.DUAL_TRAJECTORY_PREDICTIVE;
        RelaySelectionStrategy strategy = RelayStrategyFactory.createStrategy(this.strategyType, this.riskCalculator);
        this.relaySelector.setStrategy(strategy);
    }

    public RelayStrategyType getStrategyType() {
        return strategyType;
    }

    public List<EmergencyMessage> getAllMessages() {
        return Collections.unmodifiableList(messages);
    }

    public Optional<EmergencyMessage> getMessage(long id) {
        return messages.stream().filter(m -> m.getMessageId() == id).findFirst();
    }

    public List<SimulationEvent> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    public void addListener(SimulationEventListener listener) {
        if (listener != null) listeners.add(listener);
    }

    private void publishEvent(SimulationEventType type, String src, String tgt, String desc, double risk) {
        SimulationEvent event = new SimulationEvent(
                nextEventId++,
                currentStep,
                currentStep * config.timeStepSeconds(),
                type,
                src,
                tgt,
                desc,
                risk
        );
        eventLog.add(0, event);
        while (eventLog.size() > 500) {
            eventLog.remove(eventLog.size() - 1);
        }
        for (SimulationEventListener l : listeners) {
            try {
                l.onEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    public List<MeshLink> getActiveLinks() {
        List<MeshLink> links = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (router.areConnected(nodes.get(i), nodes.get(j))) {
                    links.add(new MeshLink(nodes.get(i).getNodeId(), nodes.get(j).getNodeId()));
                }
            }
        }
        return links;
    }

    public NetworkNode findNode(String id) {
        for (NetworkNode n : nodes) {
            if (n.getNodeId().equals(id)) return n;
        }
        return null;
    }

    public List<NetworkNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public MeshRouter getRouter() {
        return router;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    private void printResult(SimulationResult result) {
        System.out.printf(
                "Step: %3d | Time: %6.1f s | Relay: %s | Battery: %6.2f%% | Risk: %.3f | Handovers: %d | Event: %s%n",
                result.step(),
                result.simulationTimeSeconds(),
                result.currentRelayId(),
                result.relayBattery(),
                result.relayRisk(),
                result.handoverCount(),
                result.event()
        );
    }

    public List<SimulationResult> run() {
        while (currentStep < config.totalSteps()) {
            step();
        }
        return List.copyOf(results);
    }

    public List<SimulationResult> getResults() {
        return List.copyOf(results);
    }

    public HandoverManager getHandoverManager() {
        return handoverManager;
    }

    public RelaySelector getRelaySelector() {
        return relaySelector;
    }

    public RiskCalculator getRiskCalculator() {
        return riskCalculator;
    }

    public BatteryPredictor getBatteryPredictor() {
        return batteryPredictor;
    }

    public MobilityPredictor getMobilityPredictor() {
        return mobilityPredictor;
    }
}
