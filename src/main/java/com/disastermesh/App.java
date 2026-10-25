package com.disastermesh;

import java.util.List;
import java.util.Map;

public class App {

    public static void main(String[] args) {

        System.out.println("==================================================");
        System.out.println(" DUAL-TRAJECTORY PREDICTIVE RELAY HANDOVER");
        System.out.println(" DISASTER MESH NETWORK SIMULATION");
        System.out.println("==================================================");

        SimulationConfig config =
                SimulationConfig.defaultConfig();

        NetworkNode n1 = new NetworkNode(
                "N1", 90, new Position(0, 0));

        NetworkNode n2 = new NetworkNode(
                "N2", 85, new Position(20, 0));

        NetworkNode n3 = new NetworkNode(
                "N3", 80, new Position(30, 10));

        NetworkNode n4 = new NetworkNode(
                "N4", 75, new Position(15, 20));

        List<NetworkNode> nodes =
                List.of(n1, n2, n3, n4);

        Map<String, Double> drainRates = Map.of(
                "N1", 1.0,
                "N2", 0.3,
                "N3", 0.2,
                "N4", 0.4
        );

        Map<String, Velocity> velocities = Map.of(
                "N1", new Velocity(0.2, 0.0),
                "N2", new Velocity(0.1, 0.0),
                "N3", new Velocity(-0.2, 0.0),
                "N4", new Velocity(0.0, -0.1)
        );

        SimulationEngine engine = new SimulationEngine(
                config,
                nodes,
                drainRates,
                velocities,
                n1
        );

        System.out.println();
        System.out.println("Starting simulation...");
        System.out.println();

        List<SimulationResult> results = engine.run();

        System.out.println();
        System.out.println("==================================================");
        System.out.println("SIMULATION SUMMARY");
        System.out.println("==================================================");

        System.out.println(
                "Simulation steps: " + results.size());

        System.out.println(
                "Total handovers: "
                        + engine.getHandoverManager().getHandoverCount());

        System.out.println(
                "Final relay: "
                        + engine.getHandoverManager()
                                .getCurrentRelay().getNodeId());

        System.out.println("Simulation completed.");
    }
}