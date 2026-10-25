package com.disastermesh;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;


class AppTest {

    @Test
    void positionDistanceShouldBeCorrect() {
        Position first = new Position(10, 10);
        Position second = new Position(30, 10);

        assertEquals(20.0, first.distanceTo(second), 0.001);
    }

    @Test
    void nodeWithPositiveBatteryShouldBeActive() {
        NetworkNode node = new NetworkNode(
                "N1", 80, new Position(10, 10));

        assertTrue(node.isActive());
    }

    @Test
    void batteryPredictionShouldBeCorrect() {
        BatteryPredictor predictor = new BatteryPredictor(20);

        assertEquals(
                5.0,
                predictor.predictTimeToUnsuitable(45, 5),
                0.001);
    }

    @Test
    void batteryAtThresholdShouldReturnZero() {
        BatteryPredictor predictor = new BatteryPredictor(20);

        assertEquals(
                0.0,
                predictor.predictTimeToUnsuitable(20, 5),
                0.001);
    }

    @Test
    void zeroDrainShouldReturnInfinity() {
        BatteryPredictor predictor = new BatteryPredictor(20);

        assertEquals(
                Double.POSITIVE_INFINITY,
                predictor.predictTimeToUnsuitable(80, 0));
    }

    @Test
    void negativeDrainShouldBeRejected() {
        BatteryPredictor predictor = new BatteryPredictor(20);

        assertThrows(
                IllegalArgumentException.class,
                () -> predictor.predictTimeToUnsuitable(80, -1));
    }
    @Test
    void mobilityPredictorShouldCalculateDisconnectTime() {

        MobilityPredictor predictor = new MobilityPredictor();

        double time = predictor.predictTimeToDisconnect(
                new Position(10, 0),
                5,
                0,
                new Position(0, 0),
                50
        );

        assertEquals(8.0, time, 0.001);
    }

    @Test
    void stationaryNodeShouldNotDisconnect() {

        MobilityPredictor predictor = new MobilityPredictor();

        double time = predictor.predictTimeToDisconnect(
                new Position(10, 0),
                0,
                0,
                new Position(0, 0),
                50
        );

        assertEquals(Double.POSITIVE_INFINITY, time);
    }

    @Test
    void nodeOutsideRangeShouldReturnZero() {

        MobilityPredictor predictor = new MobilityPredictor();

        double time = predictor.predictTimeToDisconnect(
                new Position(60, 0),
                5,
                0,
                new Position(0, 0),
                50
        );

        assertEquals(0.0, time, 0.001);
    }
    @Test
    void riskCalculatorShouldCombineBatteryAndMobilityRisk() {

        RiskCalculator calculator =
                new RiskCalculator(0.5, 0.5, 10.0);

        double risk = calculator.calculateRisk(5.0, 2.0);

        assertEquals(0.65, risk, 0.001);
    }

    @Test
    void immediateUnsuitabilityShouldProduceMaximumRisk() {

        RiskCalculator calculator =
                new RiskCalculator(0.5, 0.5, 10.0);

        double risk = calculator.calculateRisk(0.0, 0.0);

        assertEquals(1.0, risk, 0.001);
    }

    @Test
    void infinitePredictionTimesShouldProduceZeroRisk() {

        RiskCalculator calculator =
                new RiskCalculator(0.5, 0.5, 10.0);

        double risk = calculator.calculateRisk(
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY
        );

        assertEquals(0.0, risk, 0.001);
    }

    @Test
    void invalidRiskWeightsShouldBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new RiskCalculator(0.8, 0.8, 10.0)
        );
    }
    @Test
    void relaySelectorShouldChooseLowestRiskCandidate() {

        RiskCalculator calculator =
                new RiskCalculator(0.5, 0.5, 10.0);

        RelaySelector selector =
                new RelaySelector(calculator, 20.0, 50.0);

        NetworkNode currentRelay = new NetworkNode(
                "N1", 80, new Position(0, 0));

        NetworkNode riskyNode = new NetworkNode(
                "N2", 60, new Position(10, 0));

        NetworkNode safeNode = new NetworkNode(
                "N3", 80, new Position(20, 0));

        List<NetworkNode> candidates =
                List.of(currentRelay, riskyNode, safeNode);

        Map<String, Double> batteryTimes = Map.of(
                "N2", 2.0,
                "N3", 8.0
        );

        Map<String, Double> disconnectTimes = Map.of(
                "N2", 1.0,
                "N3", 9.0
        );

        Optional<NetworkNode> selected = selector.selectRelay(
                candidates,
                "N1",
                new Position(0, 0),
                batteryTimes,
                disconnectTimes
        );

        assertTrue(selected.isPresent());
        assertEquals("N3", selected.get().getNodeId());
    }

    @Test
    void relaySelectorShouldReturnEmptyWhenNoCandidateQualifies() {

        RiskCalculator calculator =
                new RiskCalculator(0.5, 0.5, 10.0);

        RelaySelector selector =
                new RelaySelector(calculator, 20.0, 30.0);

        NetworkNode lowBatteryNode = new NetworkNode(
                "N2", 10, new Position(10, 0));

        Optional<NetworkNode> selected = selector.selectRelay(
                List.of(lowBatteryNode),
                "N1",
                new Position(0, 0),
                Map.of("N2", 5.0),
                Map.of("N2", 5.0)
        );

        assertTrue(selected.isEmpty());
    }
    @Test
    void handoverShouldSwitchToSaferRelay() {

        RiskCalculator calculator =
                new RiskCalculator(0.5, 0.5, 10.0);

        RelaySelector selector =
                new RelaySelector(calculator, 20.0, 50.0);

        NetworkNode currentRelay =
                new NetworkNode("N1", 80, new Position(0, 0));

        NetworkNode saferRelay =
                new NetworkNode("N2", 85, new Position(10, 0));

        HandoverManager manager =
                new HandoverManager(currentRelay, selector, 0.70);

        boolean changed = manager.evaluateAndHandover(
                List.of(currentRelay, saferRelay),
                new Position(0, 0),
                Map.of("N2", 8.0),
                Map.of("N2", 9.0),
                0.85
        );

        assertTrue(changed);
        assertEquals("N2", manager.getCurrentRelay().getNodeId());
        assertEquals(1, manager.getHandoverCount());
        }

    @Test
    void handoverShouldNotOccurWhenRiskIsLow() {

        RiskCalculator calculator =
                new RiskCalculator(0.5, 0.5, 10.0);

        RelaySelector selector =
                new RelaySelector(calculator, 20.0, 50.0);

        NetworkNode currentRelay =
                new NetworkNode("N1", 80, new Position(0, 0));

        HandoverManager manager =
                new HandoverManager(currentRelay, selector, 0.70);

        boolean changed = manager.evaluateAndHandover(
                List.of(currentRelay),
                new Position(0, 0),
                Map.of(),
                Map.of(),
                0.20
        );

        assertFalse(changed);
        assertEquals("N1", manager.getCurrentRelay().getNodeId());
        assertEquals(0, manager.getHandoverCount());
        }

    @Test
    void mobilityPredictorShouldUseRelativeMotionOfBothNodes() {
        MobilityPredictor predictor = new MobilityPredictor();

        double time = predictor.predictTimeToDisconnect(
                new Position(10, 0),
                5, 0,
                new Position(0, 0),
                2, 0,
                50
        );

        assertEquals(40.0 / 3.0, time, 0.001);
    }

    @Test
    void meshRouterShouldDiscoverShortestMultiHopRoute() {
        MeshRouter router = new MeshRouter(35.0);
        NetworkNode a = new NetworkNode("A", 100, new Position(0, 0));
        NetworkNode b = new NetworkNode("B", 100, new Position(30, 0));
        NetworkNode c = new NetworkNode("C", 100, new Position(60, 0));

        List<NetworkNode> nodes = List.of(a, b, c);

        assertTrue(router.areConnected(a, b));
        assertTrue(router.areConnected(b, c));
        assertFalse(router.areConnected(a, c)); // 60 > 35, cannot communicate directly

        List<String> path = router.findShortestPath("A", "C", nodes);
        assertEquals(List.of("A", "B", "C"), path);
    }

    @Test
    void emergencyMessagePrioritySchedulingShouldOrderCriticalFirst() {
        EmergencyMessage normal = new EmergencyMessage(1, "N1", "N8", "Normal flood report", EmergencyCategory.SUPPLY_REQUEST, EmergencyPriority.NORMAL, 0);
        EmergencyMessage critical = new EmergencyMessage(2, "N2", "N8", "Trapped in building collapse", EmergencyCategory.RESCUE, EmergencyPriority.CRITICAL, 0);
        EmergencyMessage high = new EmergencyMessage(3, "N3", "N8", "Spreading wildfire", EmergencyCategory.FIRE, EmergencyPriority.HIGH, 0);

        List<EmergencyMessage> list = new java.util.ArrayList<>(List.of(normal, critical, high));
        java.util.Collections.sort(list);

        assertEquals(2, list.get(0).getMessageId()); // CRITICAL first
        assertEquals(3, list.get(1).getMessageId()); // HIGH second
        assertEquals(1, list.get(2).getMessageId()); // NORMAL third
    }

    @Test
    void messageIdentityAndPayloadMustBePreservedAcrossHandovers() {
        EmergencyMessage msg = new EmergencyMessage(105, "N1", "N8", "Urgent insulin medication needed", EmergencyCategory.MEDICAL, EmergencyPriority.HIGH, 0);
        msg.startTransit(List.of("N1", "N2", "N3", "N8"));

        assertEquals(MessageStatus.IN_TRANSIT, msg.getStatus());
        assertEquals("N1", msg.getCurrentNode());

        // Perform handover from N2 to N5
        msg.recordHandover("N2", "N5", List.of("N1", "N5", "N6", "N8"));

        // Verify identity and payload remain strictly untouched
        assertEquals(105, msg.getMessageId());
        assertEquals("N1", msg.getSenderId());
        assertEquals("N8", msg.getDestinationId());
        assertEquals("Urgent insulin medication needed", msg.getContent());
        assertEquals(MessageStatus.REROUTING, msg.getStatus());
        assertEquals(1, msg.getHandoverHistory().size());
        assertEquals(2, msg.getRouteHistory().size());
    }

    @Test
    void strategiesShouldProduceDifferentSelectionsBasedOnObjective() {
        RiskCalculator riskCalc = new RiskCalculator(0.5, 0.5, 10.0);
        Position source = new Position(0, 0);
        Position dest = new Position(100, 0);

        // Candidate 1: High battery (95%), but moving away fast (disconnects in 1 min)
        NetworkNode candidateFastMove = new NetworkNode("C1", 95, new Position(25, 0));
        // Candidate 2: Medium battery (70%), highly stable (disconnects in 20 mins)
        NetworkNode candidateStable = new NetworkNode("C2", 70, new Position(20, 0));

        List<NetworkNode> candidates = List.of(candidateFastMove, candidateStable);
        Map<String, Double> batteryTimes = Map.of("C1", 15.0, "C2", 10.0);
        Map<String, Double> disconnectTimes = Map.of("C1", 1.0, "C2", 20.0);

        // Battery-only selects C1
        RelaySelectionStrategy batteryOnly = new BatteryOnlyStrategy();
        Optional<NetworkNode> bPick = batteryOnly.selectRelay(candidates, "N0", source, dest, batteryTimes, disconnectTimes, 30.0, 20.0);
        assertTrue(bPick.isPresent());
        assertEquals("C1", bPick.get().getNodeId());

        // Mobility-only selects C2
        RelaySelectionStrategy mobilityOnly = new MobilityOnlyStrategy();
        Optional<NetworkNode> mPick = mobilityOnly.selectRelay(candidates, "N0", source, dest, batteryTimes, disconnectTimes, 30.0, 20.0);
        assertTrue(mPick.isPresent());
        assertEquals("C2", mPick.get().getNodeId());

        // Dual-trajectory considers joint risk
        RelaySelectionStrategy dual = new DualRiskPredictiveStrategy(riskCalc);
        Optional<NetworkNode> dPick = dual.selectRelay(candidates, "N0", source, dest, batteryTimes, disconnectTimes, 30.0, 20.0);
        assertTrue(dPick.isPresent());
        assertEquals("C2", dPick.get().getNodeId()); // C2 chosen because C1 has acute disconnection risk
    }

    @Test
    void inTransitMessageReroutingAndDeliveryIntegrationTest() {
        SimulationConfig config = new SimulationConfig(20, 1.0, 35.0, 20.0, 0.40, 0.50, 0.50, 2.0);

        NetworkNode n1 = new NetworkNode("N1", 90, new Position(0, 0));
        NetworkNode n2 = new NetworkNode("N2", 85, new Position(30, 0));
        NetworkNode n3 = new NetworkNode("N3", 80, new Position(60, 0));

        List<NetworkNode> nodes = List.of(n1, n2, n3);
        Map<String, Double> drains = Map.of("N1", 0.1, "N2", 0.1, "N3", 0.1);
        Map<String, Velocity> velocities = Map.of(
                "N1", new Velocity(0, 0),
                "N2", new Velocity(0, 0),
                "N3", new Velocity(0, 0)
        );

        SimulationEngine engine = new SimulationEngine(config, nodes, drains, velocities, n1);
        engine.setTicksPerHop(1); // 1 tick per hop for rapid verification
        EmergencyMessage msg = engine.createAndSubmitMessage(
                501, "N1", "N3", "Injured survivor at post", EmergencyCategory.MEDICAL, EmergencyPriority.CRITICAL
        );

        // Step 1: Forwarding starts (N1 -> N2)
        engine.step();
        assertEquals(MessageStatus.IN_TRANSIT, msg.getStatus());

        // Step 2: Advances hop (reached N3 - destination!)
        engine.step();
        assertEquals(MessageStatus.DELIVERED, msg.getStatus());
        assertEquals("N3", msg.getCurrentNode());
        assertTrue(msg.getDeliveredAtSeconds() != null);
    }

    @Test
    void partitionedNetworkMustHonestlyReportFailureWithoutFalseDelivery() {
        SimulationConfig config = new SimulationConfig(10, 1.0, 30.0, 20.0, 0.40, 0.50, 0.50, 2.0);

        // Isolated node n3 is 100 units away from n1 and n2 (total network partition)
        NetworkNode n1 = new NetworkNode("N1", 90, new Position(0, 0));
        NetworkNode n2 = new NetworkNode("N2", 85, new Position(20, 0));
        NetworkNode n3 = new NetworkNode("N3", 80, new Position(100, 0));

        SimulationEngine engine = new SimulationEngine(
                config,
                List.of(n1, n2, n3),
                Map.of("N1", 0.1, "N2", 0.1, "N3", 0.1),
                Map.of("N1", new Velocity(0, 0), "N2", new Velocity(0, 0), "N3", new Velocity(0, 0)),
                n1
        );

        EmergencyMessage msg = engine.createAndSubmitMessage(
                999, "N1", "N3", "SOS partitioned", EmergencyCategory.GENERAL_SOS, EmergencyPriority.CRITICAL
        );

        engine.step();

        // System must report FAILED honestly; it must NEVER falsely report DELIVERED
        assertEquals(MessageStatus.FAILED, msg.getStatus());
        assertTrue(msg.getFailureReason().contains("No connected route"));
        assertTrue(msg.getDeliveredAtSeconds() == null);
    }

    @Test
    void scientificBenchmarkSuiteExecutesAndExportsMetrics() {
        ExperimentRunner.BenchmarkResult benchmark = ExperimentRunner.runBenchmark(25);
        assertEquals(4, benchmark.comparativeMetrics().size());

        String csv = benchmark.toCsv();
        assertTrue(csv.contains("Dual-Trajectory"));
        assertTrue(csv.contains("Shortest-Path"));
        assertTrue(csv.contains("PDR"));

        String json = benchmark.toJson();
        assertTrue(json.contains("\"comparativeMetrics\""));
        assertTrue(json.contains("\"strategy\""));
    }

    @Test
    void naturalDisaster360DegreeBroadcastFloodReachesAllNearbyNodesIgnoringBattery() {
        SimulationConfig config = new SimulationConfig(10, 1.0, 45.0, 20.0, 0.40, 0.50, 0.50, 2.0);

        // Center node N1, surround by N2 (high battery) and N3 (critically low battery 5%)
        NetworkNode n1 = new NetworkNode("N1", 90, new Position(0, 0));
        NetworkNode n2 = new NetworkNode("N2", 85, new Position(30, 0));
        NetworkNode n3 = new NetworkNode("N3", 5, new Position(0, 30)); // Low battery!

        SimulationEngine engine = new SimulationEngine(
                config,
                List.of(n1, n2, n3),
                Map.of("N1", 0.1, "N2", 0.1, "N3", 0.1),
                Map.of("N1", new Velocity(0, 0), "N2", new Velocity(0, 0), "N3", new Velocity(0, 0)),
                n1
        );

        // Submit 360-degree Life-Safety Disaster Broadcast
        EmergencyMessage broadcast = engine.createAndSubmitBroadcastMessage(
                777, "N1", "EARTHQUAKE TSUNAMI WARNING - EVACUATE TO HIGH GROUND",
                EmergencyCategory.GENERAL_SOS, EmergencyPriority.CRITICAL
        );

        assertTrue(broadcast.isBroadcast());

        // Step simulation to radiate the broadcast wave
        engine.step();

        // Broadcast must reach ALL nearby nodes including N3 despite its 5% battery
        assertTrue(broadcast.getBroadcastReachedNodes().contains("N2"));
        assertTrue(broadcast.getBroadcastReachedNodes().contains("N3"));
        assertEquals(MessageStatus.DELIVERED, broadcast.getStatus());
    }
}