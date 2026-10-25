package com.disastermesh;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline Bluetooth Mesh Emergency Messaging Engine & Web Console
 * Inspired by decentralized BLE mesh communicators (Bridgefy, Briar, BitChat).
 * Operates without cellular towers, 4G/5G, SIM cards, or central servers.
 */
public class WebApp {

    private static final int PORT = 8080;
    private static double RANGE = 36.0;

    private static SimulationConfig config;
    private static List<NetworkNode> nodes;
    private static Map<String, Double> drains;
    private static Map<String, Velocity> velocities;
    private static SimulationEngine engine;

    private static double lastCombinedRisk = 0.0;
    private static double lastBatteryRisk = 0.0;
    private static double lastMobilityRisk = 0.0;
    private static String lastEvent = "BLE_MESH_INITIALIZED";
    private static long nextMessageId = 101;
    private static ExperimentRunner.BenchmarkResult lastBenchmarkResult = null;

    private static final Map<String, String> DEVICE_NAMES = Map.of(
            "N1", "Phone 1",
            "N2", "Phone 2",
            "N3", "Phone 3",
            "N4", "Phone 4",
            "N5", "Phone 5",
            "N6", "Phone 6",
            "N7", "Phone 7",
            "N8", "Phone 8"
    );

    public static void main(String[] args) throws Exception {
        reset();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);

        server.createContext("/", WebApp::servePage);
        server.createContext("/api/state", WebApp::serveState);
        server.createContext("/api/simulation/start", WebApp::startSimulation);
        server.createContext("/api/simulation/pause", WebApp::pauseSimulation);
        server.createContext("/api/simulation/step", WebApp::handleStep);
        server.createContext("/api/simulation/reset", WebApp::handleReset);
        server.createContext("/api/tick", WebApp::handleStep);
        server.createContext("/api/reset", WebApp::handleReset);

        server.createContext("/api/messages", WebApp::handleMessages);
        server.createContext("/api/message", WebApp::handleSingleMessageLegacy);
        server.createContext("/api/events", WebApp::serveEvents);
        server.createContext("/api/config", WebApp::handleConfig);
        server.createContext("/api/strategy", WebApp::handleStrategy);
        server.createContext("/api/transport", WebApp::handleTransport);
        server.createContext("/api/pacing", WebApp::handlePacing);
        server.createContext("/api/demo", WebApp::handleDemo);
        server.createContext("/api/node/battery", WebApp::handleNodeBattery);

        server.createContext("/api/metrics", WebApp::serveMetrics);
        server.createContext("/api/benchmark/run", WebApp::runBenchmark);
        server.createContext("/api/benchmark/export", WebApp::exportBenchmark);

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("==================================================================");
        System.out.println(" OFFLINE BLUETOOTH MESH MESSENGER RUNNING (Bridgefy/Briar Model)");
        System.out.println(" Local Web Console: http://localhost:" + PORT);
        System.out.println(" Decentralized BLE P2P · Zero Cellular / 4G / 5G / Wi-Fi Required");
        System.out.println("==================================================================");
    }

    private static synchronized void reset() {
        config = SimulationConfig.defaultWebConfig();
        RANGE = 36.0;

        nodes = new ArrayList<>(List.of(
                new NetworkNode("N1", 96, new Position(0, 0)),
                new NetworkNode("N2", 84, new Position(28, 0)),
                new NetworkNode("N3", 68, new Position(56, 0)),
                new NetworkNode("N4", 88, new Position(84, 0)),
                new NetworkNode("N5", 78, new Position(24, 20)),
                new NetworkNode("N6", 52, new Position(52, 20)),
                new NetworkNode("N7", 38, new Position(80, 20)),
                new NetworkNode("N8", 92, new Position(108, 10))
        ));

        drains = new LinkedHashMap<>();
        velocities = new LinkedHashMap<>();
        // Fixed phone positions by default; phones only drift or drain when demo buttons are triggered
        double[] d = {0.15, 0.20, 0.18, 0.15, 0.22, 0.18, 0.16, 0.14};
        Velocity[] v = {
                new Velocity(0.00, 0.00), new Velocity(0.00, 0.00),
                new Velocity(0.00, 0.00), new Velocity(0.00, 0.00),
                new Velocity(0.00, 0.00), new Velocity(0.00, 0.00),
                new Velocity(0.00, 0.00), new Velocity(0.00, 0.00)
        };
        for (int i = 0; i < nodes.size(); i++) {
            drains.put(nodes.get(i).getNodeId(), d[i]);
            velocities.put(nodes.get(i).getNodeId(), v[i]);
        }

        // Initialize with N2 as relay between N1 and destination N8
        engine = new SimulationEngine(config, nodes, drains, velocities, nodes.get(1));
        engine.setTransportMode(TransportMode.BLUETOOTH_MESH);
        engine.setTicksPerHop(1); // Fast 1-click hop progression by default for snappy faculty demos!
        RANGE = 36.0;

        lastCombinedRisk = 0.0;
        lastBatteryRisk = 0.0;
        lastMobilityRisk = 0.0;
        lastEvent = "BLE_MESH_READY";
        nextMessageId = 101;
    }

    private static void servePage(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath();
        if (!path.equals("/") && !path.equals("/index.html")) {
            respond(x, 404, "text/plain", "Not found");
            return;
        }
        try (InputStream in = WebApp.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                respond(x, 500, "text/plain", "Error: /web/index.html not found in classpath.");
                return;
            }
            respond(x, 200, "text/html; charset=utf-8", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void serveState(HttpExchange x) throws IOException {
        if (!method(x, "GET")) return;
        respond(x, 200, "application/json; charset=utf-8", stateJson());
    }

    private static void startSimulation(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        respond(x, 200, "application/json; charset=utf-8", "{\"status\":\"RUNNING\"}");
    }

    private static void pauseSimulation(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        respond(x, 200, "application/json; charset=utf-8", "{\"status\":\"PAUSED\"}");
    }

    private static void handleStep(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        synchronized (WebApp.class) {
            if (engine.getCurrentStep() < config.totalSteps()) {
                SimulationResult res = quietStep();
                lastCombinedRisk = res.relayRisk();
                lastEvent = res.event();
                calculateDetailedRisks();
            }
        }
        respond(x, 200, "application/json; charset=utf-8", stateJson());
    }

    private static void handleReset(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        synchronized (WebApp.class) {
            reset();
        }
        respond(x, 200, "application/json; charset=utf-8", stateJson());
    }

    private static void handleDemo(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String action = field(body, "action", "drain");

        synchronized (WebApp.class) {
            NetworkNode relay = engine.getHandoverManager().getCurrentRelay();
            if (action.equalsIgnoreCase("drain")) {
                relay.updateBattery(18.0); // Drop to 18% (critical low battery)
                lastEvent = "FACULTY_DEMO: Battery drained to 18% on " + relay.getNodeId() + " -> Triggered Proactive Handover.";
                quietStep();
                calculateDetailedRisks();
            } else if (action.equalsIgnoreCase("drift")) {
                Position cur = relay.getPosition();
                relay.updatePosition(new Position(cur.x() + 35.0, cur.y() + 30.0));
                lastEvent = "FACULTY_DEMO: " + relay.getNodeId() + " drifted 45m out of range -> Triggered Route Repair & Handover.";
                quietStep();
                calculateDetailedRisks();
            }
        }
        respond(x, 200, "application/json; charset=utf-8", stateJson());
    }

    private static void handleNodeBattery(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String action = field(body, "action", "");
        String nodeId = field(body, "nodeId", "");
        String batteryStr = field(body, "battery", "");

        synchronized (WebApp.class) {
            if (action.equalsIgnoreCase("randomize")) {
                double[] randomLevels = {94.0, 48.0, 62.0, 85.0, 72.0, 39.0, 55.0, 91.0};
                for (int i = 0; i < nodes.size(); i++) {
                    nodes.get(i).updateBattery(randomLevels[i % randomLevels.length]);
                }
                lastEvent = "BATTERY_AUTO_EDIT: Randomized distinct battery levels applied across all phones.";
            } else if (action.equalsIgnoreCase("reset")) {
                double[] defaultLevels = {96.0, 84.0, 68.0, 88.0, 78.0, 52.0, 38.0, 92.0};
                for (int i = 0; i < nodes.size(); i++) {
                    nodes.get(i).updateBattery(defaultLevels[i % defaultLevels.length]);
                }
                lastEvent = "BATTERY_RESET: Restored default distinct battery levels.";
            } else if (!nodeId.isBlank() && !batteryStr.isBlank()) {
                try {
                    double bVal = Math.max(5.0, Math.min(100.0, Double.parseDouble(batteryStr)));
                    NetworkNode target = engine.findNode(nodeId);
                    if (target != null) {
                        target.updateBattery(bVal);
                        lastEvent = "MANUAL_EDIT: Set " + DEVICE_NAMES.getOrDefault(nodeId, nodeId) + " battery to " + Math.round(bVal) + "%.";
                    }
                } catch (Exception ignored) {}
            }
            calculateDetailedRisks();
        }
        respond(x, 200, "application/json; charset=utf-8", stateJson());
    }

    private static SimulationResult quietStep() {
        java.io.PrintStream old = System.out;
        try {
            System.setOut(new java.io.PrintStream(OutputStream.nullOutputStream()));
            return engine.step();
        } finally {
            System.setOut(old);
        }
    }

    private static void calculateDetailedRisks() {
        NetworkNode relay = engine.getHandoverManager().getCurrentRelay();
        Double drain = drains.get(relay.getNodeId());
        double drainVal = drain != null ? drain : 0.5;
        double battTimeSec = engine.getBatteryPredictor().predictTimeToUnsuitable(relay.getBatteryLevel(), drainVal);
        double battTimeMin = battTimeSec / 60.0;

        double horizon = config.predictionHorizonMinutes();
        if (battTimeMin == Double.POSITIVE_INFINITY) {
            lastBatteryRisk = 0.0;
        } else if (battTimeMin <= 0.0) {
            lastBatteryRisk = 1.0;
        } else {
            lastBatteryRisk = Math.max(0.0, Math.min(1.0, 1.0 - (battTimeMin / horizon)));
        }

        if (relay.getBatteryLevel() <= config.minimumRelayBattery()) {
            lastBatteryRisk = 1.0;
            lastCombinedRisk = 1.0;
        }

        if (!engine.getRouter().areConnected(engine.getNodes().get(0), relay)) {
            lastMobilityRisk = 1.0;
            lastCombinedRisk = 1.0;
        }

        if (lastCombinedRisk >= 0 && config.batteryWeight() > 0) {
            lastMobilityRisk = Math.max(0.0, Math.min(1.0, (lastCombinedRisk - (config.batteryWeight() * lastBatteryRisk)) / Math.max(0.01, config.mobilityWeight())));
        }
    }

    private static void handleMessages(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            respond(x, 200, "application/json; charset=utf-8", messagesJson());
        } else if (x.getRequestMethod().equalsIgnoreCase("POST")) {
            createMessage(x);
        } else {
            respond(x, 405, "text/plain", "Method not allowed");
        }
    }

    private static void handleSingleMessageLegacy(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        createMessage(x);
    }

    private static void createMessage(HttpExchange x) throws IOException {
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String sender = field(body, "sender", "N1");
        String dest = field(body, "destination", "N8");
        String type = field(body, "type", "General SOS");
        String priorityStr = field(body, "priority", "Critical");
        String text = field(body, "text", "");
        boolean isBroadcast = field(body, "broadcast", "false").equalsIgnoreCase("true")
                || dest.equalsIgnoreCase("ALL_SURROUNDING");

        synchronized (WebApp.class) {
            NetworkNode sNode = engine.findNode(sender);
            if (sNode == null) {
                respond(x, 400, "application/json", "{\"error\":\"Invalid sender node.\"}");
                return;
            }

            if (!isBroadcast) {
                NetworkNode dNode = engine.findNode(dest);
                if (dNode == null || sender.equals(dest)) {
                    respond(x, 400, "application/json", "{\"error\":\"Choose different, valid sender and destination nodes.\"}");
                    return;
                }
            }

            if (text.isBlank()) {
                respond(x, 400, "application/json", "{\"error\":\"Enter a message to send over Bluetooth mesh.\"}");
                return;
            }

            EmergencyCategory category = EmergencyCategory.fromString(type);
            EmergencyPriority priority = EmergencyPriority.fromString(priorityStr);

            if (isBroadcast) {
                engine.createAndSubmitBroadcastMessage(
                        nextMessageId++,
                        sender,
                        text,
                        category,
                        priority
                );
            } else {
                engine.createAndSubmitMessage(
                        nextMessageId++,
                        sender,
                        dest,
                        text,
                        category,
                        priority
                );
            }
        }
        respond(x, 200, "application/json; charset=utf-8", stateJson());
    }

    private static void handleTransport(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("POST")) {
            String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String modeStr = field(body, "mode", "BLUETOOTH_MESH");
            synchronized (WebApp.class) {
                TransportMode mode = TransportMode.fromString(modeStr);
                engine.setTransportMode(mode);
                RANGE = mode.getRangeUnits();
            }
            respond(x, 200, "application/json; charset=utf-8", "{\"mode\":\"" + engine.getTransportMode().name() + "\",\"range\":" + RANGE + "}");
        } else {
            respond(x, 200, "application/json; charset=utf-8", "{\"mode\":\"" + engine.getTransportMode().name() + "\",\"range\":" + RANGE + "}");
        }
    }

    private static void handlePacing(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String mode = field(body, "pacing", "fast");
        synchronized (WebApp.class) {
            // Fast mode = 1 tick per hop (immediate next hop per click!)
            int ticks = mode.equalsIgnoreCase("fast") ? 1 : 3;
            engine.setTicksPerHop(ticks);
        }
        respond(x, 200, "application/json; charset=utf-8", "{\"pacing\":\"" + (engine.getTicksPerHop() == 1 ? "fast" : "normal") + "\",\"ticksPerHop\":" + engine.getTicksPerHop() + "}");
    }

    private static void serveEvents(HttpExchange x) throws IOException {
        if (!method(x, "GET")) return;
        StringBuilder sb = new StringBuilder("[");
        List<SimulationEvent> events = engine.getEventLog();
        for (int i = 0; i < events.size(); i++) {
            SimulationEvent e = events.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                    .append("\"id\":").append(e.eventId()).append(",")
                    .append("\"step\":").append(e.step()).append(",")
                    .append("\"time\":").append(String.format(Locale.US, "%.1f", e.timestampSeconds())).append(",")
                    .append("\"type\":\"").append(esc(e.type().name())).append("\",")
                    .append("\"source\":\"").append(esc(e.sourceNodeId())).append("\",")
                    .append("\"target\":\"").append(esc(e.targetNodeId())).append("\",")
                    .append("\"desc\":\"").append(esc(e.description())).append("\",")
                    .append("\"risk\":").append(String.format(Locale.US, "%.3f", e.riskScore()))
                    .append("}");
        }
        sb.append("]");
        respond(x, 200, "application/json; charset=utf-8", sb.toString());
    }

    private static void handleConfig(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equalsIgnoreCase("GET")) {
            String json = String.format(Locale.US,
                    "{\"totalSteps\":%d,\"timeStep\":%.1f,\"range\":%.1f,\"minBattery\":%.1f,\"handoverThreshold\":%.2f,\"batteryWeight\":%.2f,\"mobilityWeight\":%.2f,\"horizon\":%.1f}",
                    config.totalSteps(), config.timeStepSeconds(), config.communicationRange(), config.minimumRelayBattery(),
                    config.handoverRiskThreshold(), config.batteryWeight(), config.mobilityWeight(), config.predictionHorizonMinutes());
            respond(x, 200, "application/json; charset=utf-8", json);
        } else if (x.getRequestMethod().equalsIgnoreCase("POST")) {
            String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                double range = Double.parseDouble(field(body, "range", String.valueOf(config.communicationRange())));
                double threshold = Double.parseDouble(field(body, "handoverThreshold", String.valueOf(config.handoverRiskThreshold())));
                double bWeight = Double.parseDouble(field(body, "batteryWeight", String.valueOf(config.batteryWeight())));
                double mWeight = 1.0 - bWeight;
                double horizon = Double.parseDouble(field(body, "horizon", String.valueOf(config.predictionHorizonMinutes())));

                synchronized (WebApp.class) {
                    config = new SimulationConfig(config.totalSteps(), config.timeStepSeconds(), range, config.minimumRelayBattery(), threshold, bWeight, mWeight, horizon);
                    RANGE = range;
                    engine.getRouter().setCommunicationRange(range);
                }
                respond(x, 200, "application/json; charset=utf-8", "{\"status\":\"CONFIG_UPDATED\"}");
            } catch (Exception e) {
                respond(x, 400, "application/json", "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else {
            respond(x, 405, "text/plain", "Method not allowed");
        }
    }

    private static void handleStrategy(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String stratName = field(body, "strategy", "DUAL_TRAJECTORY_PREDICTIVE");
        synchronized (WebApp.class) {
            RelayStrategyType type = RelayStrategyType.fromString(stratName);
            engine.setStrategy(type);
            quietStep();
            lastEvent = "ALGORITHM_SWITCH: Active strategy set to " + type.getTitle();
            calculateDetailedRisks();
        }
        respond(x, 200, "application/json; charset=utf-8", stateJson());
    }

    private static void serveMetrics(HttpExchange x) throws IOException {
        if (!method(x, "GET")) return;
        respond(x, 200, "application/json; charset=utf-8", calculateMetricsJson());
    }

    private static void runBenchmark(HttpExchange x) throws IOException {
        if (!method(x, "POST")) return;
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int steps = 100;
        try {
            steps = Integer.parseInt(field(body, "steps", "100"));
        } catch (Exception ignored) {}

        lastBenchmarkResult = ExperimentRunner.runBenchmark(steps);
        respond(x, 200, "application/json; charset=utf-8", lastBenchmarkResult.toJson());
    }

    private static void exportBenchmark(HttpExchange x) throws IOException {
        if (!method(x, "GET")) return;
        if (lastBenchmarkResult == null) {
            lastBenchmarkResult = ExperimentRunner.runBenchmark(80);
        }
        String format = x.getRequestURI().getQuery();
        if (format != null && format.contains("format=csv")) {
            x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"disaster_mesh_benchmark.csv\"");
            respond(x, 200, "text/csv; charset=utf-8", lastBenchmarkResult.toCsv());
        } else {
            respond(x, 200, "application/json; charset=utf-8", lastBenchmarkResult.toJson());
        }
    }

    private static String calculateMetricsJson() {
        List<EmergencyMessage> all = engine.getAllMessages();
        int total = all.size();
        long delivered = all.stream().filter(m -> m.getStatus() == MessageStatus.DELIVERED).count();
        long failed = all.stream().filter(m -> m.getStatus() == MessageStatus.FAILED).count();
        long inTransit = all.stream().filter(m -> m.getStatus() == MessageStatus.IN_TRANSIT || m.getStatus() == MessageStatus.REROUTING).count();

        double pdr = total > 0 ? (double) delivered / total : 1.0;
        double avgDelayHops = delivered > 0
                ? all.stream().filter(m -> m.getStatus() == MessageStatus.DELIVERED).mapToInt(EmergencyMessage::getTotalHopsTraversed).average().orElse(1.0)
                : 0.0;

        return String.format(Locale.US,
                "{\"totalMessages\":%d,\"delivered\":%d,\"failed\":%d,\"inTransit\":%d,\"pdr\":%.3f,\"avgDelayHops\":%.2f,\"handovers\":%d,\"strategy\":\"%s\"}",
                total, delivered, failed, inTransit, pdr, avgDelayHops, engine.getHandoverManager().getHandoverCount(), esc(engine.getStrategyType().getTitle()));
    }

    private static String stateJson() {
        NetworkNode relay = engine.getHandoverManager().getCurrentRelay();
        List<NetworkNode> nodeList = engine.getNodes();
        List<SimulationEngine.MeshLink> links = engine.getActiveLinks();
        TransportMode tMode = engine.getTransportMode();

        StringBuilder b = new StringBuilder("{");
        b.append("\"step\":").append(engine.getCurrentStep()).append(",");
        b.append("\"timeSeconds\":").append(String.format(Locale.US, "%.1f", engine.getCurrentStep() * config.timeStepSeconds())).append(",");
        b.append("\"range\":").append(RANGE).append(",");
        b.append("\"relay\":\"").append(esc(relay.getNodeId())).append("\",");
        b.append("\"relayName\":\"").append(esc(DEVICE_NAMES.getOrDefault(relay.getNodeId(), relay.getNodeId()))).append("\",");
        b.append("\"relayBattery\":").append(String.format(Locale.US, "%.2f", relay.getBatteryLevel())).append(",");
        b.append("\"risk\":").append(String.format(Locale.US, "%.3f", lastCombinedRisk)).append(",");
        b.append("\"batteryRisk\":").append(String.format(Locale.US, "%.3f", lastBatteryRisk)).append(",");
        b.append("\"mobilityRisk\":").append(String.format(Locale.US, "%.3f", lastMobilityRisk)).append(",");
        b.append("\"riskThreshold\":").append(String.format(Locale.US, "%.2f", config.handoverRiskThreshold())).append(",");
        b.append("\"handovers\":").append(engine.getHandoverManager().getHandoverCount()).append(",");
        b.append("\"event\":\"").append(esc(lastEvent)).append("\",");
        b.append("\"strategyKey\":\"").append(engine.getStrategyType().name()).append("\",");
        b.append("\"strategy\":\"").append(esc(engine.getStrategyType().getTitle())).append("\",");
        b.append("\"ticksPerHop\":").append(engine.getTicksPerHop()).append(",");

        // Transport Mode
        b.append("\"transport\":{")
                .append("\"key\":\"").append(tMode.name()).append("\",")
                .append("\"label\":\"").append(esc(tMode.getLabel())).append("\",")
                .append("\"range\":").append(tMode.getRangeUnits()).append(",")
                .append("\"drainMultiplier\":").append(tMode.getDrainMultiplier()).append(",")
                .append("\"bandwidth\":").append(tMode.getBandwidthMbps())
                .append("},");

        // Nodes with device labels
        b.append("\"nodes\":[");
        for (int i = 0; i < nodeList.size(); i++) {
            NetworkNode n = nodeList.get(i);
            Double drain = drains.get(n.getNodeId());
            Velocity v = velocities.get(n.getNodeId());
            if (i > 0) b.append(',');
            b.append("{\"id\":\"").append(n.getNodeId()).append("\",")
                    .append("\"name\":\"").append(esc(DEVICE_NAMES.getOrDefault(n.getNodeId(), n.getNodeId()))).append("\",")
                    .append("\"x\":").append(String.format(Locale.US, "%.2f", n.getPosition().x())).append(",")
                    .append("\"y\":").append(String.format(Locale.US, "%.2f", n.getPosition().y())).append(",")
                    .append("\"battery\":").append(String.format(Locale.US, "%.2f", n.getBatteryLevel())).append(",")
                    .append("\"drain\":").append(String.format(Locale.US, "%.2f", (drain != null ? drain : 0.0) * tMode.getDrainMultiplier())).append(",")
                    .append("\"vx\":").append(String.format(Locale.US, "%.2f", v != null ? v.x() : 0.0)).append(",")
                    .append("\"vy\":").append(String.format(Locale.US, "%.2f", v != null ? v.y() : 0.0)).append(",")
                    .append("\"active\":").append(n.isActive()).append(",")
                    .append("\"isRelay\":").append(n.getNodeId().equals(relay.getNodeId()))
                    .append("}");
        }
        b.append("],");

        // Dynamic Links
        b.append("\"links\":[");
        for (int i = 0; i < links.size(); i++) {
            SimulationEngine.MeshLink l = links.get(i);
            NetworkNode na = engine.findNode(l.a());
            NetworkNode nb = engine.findNode(l.b());
            double dist = (na != null && nb != null) ? na.getPosition().distanceTo(nb.getPosition()) : 0.0;
            double quality = Math.max(0.0, 1.0 - (dist / RANGE));
            int rssi = (int) (-40 - (dist / RANGE) * 50); // e.g. -45 dBm to -90 dBm
            if (i > 0) b.append(',');
            b.append("{\"a\":\"").append(l.a()).append("\",")
                    .append("\"b\":\"").append(l.b()).append("\",")
                    .append("\"dist\":").append(String.format(Locale.US, "%.1f", dist)).append(",")
                    .append("\"rssi\":").append(rssi).append(",")
                    .append("\"quality\":").append(String.format(Locale.US, "%.2f", quality))
                    .append("}");
        }
        b.append("],");

        // Messages
        b.append("\"messages\":").append(messagesJson()).append(",");

        // Recent Handovers
        b.append("\"recentHandovers\":[");
        List<HandoverRecord> hList = engine.getHandoverManager().getHandoverHistory();
        int hCount = Math.min(8, hList.size());
        for (int i = 0; i < hCount; i++) {
            HandoverRecord hr = hList.get(hList.size() - 1 - i);
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(hr.handoverId()).append(",")
                    .append("\"step\":").append(hr.step()).append(",")
                    .append("\"time\":").append(String.format(Locale.US, "%.1f", hr.timestampSeconds())).append(",")
                    .append("\"prev\":\"").append(esc(hr.previousRelayId())).append("\",")
                    .append("\"next\":\"").append(esc(hr.replacementRelayId())).append("\",")
                    .append("\"trigger\":\"").append(esc(hr.triggerReason())).append("\",")
                    .append("\"risk\":").append(String.format(Locale.US, "%.3f", hr.combinedRisk())).append(",")
                    .append("\"success\":").append(hr.successful())
                    .append("}");
        }
        b.append("],");

        // Metrics Summary
        b.append("\"metrics\":").append(calculateMetricsJson());

        b.append("}");
        return b.toString();
    }

    private static String messagesJson() {
        StringBuilder b = new StringBuilder("[");
        List<EmergencyMessage> msgList = engine.getAllMessages();
        for (int i = 0; i < msgList.size(); i++) {
            EmergencyMessage m = msgList.get(i);
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(m.getMessageId()).append(",")
                    .append("\"sender\":\"").append(esc(m.getSenderId())).append("\",")
                    .append("\"senderName\":\"").append(esc(DEVICE_NAMES.getOrDefault(m.getSenderId(), m.getSenderId()))).append("\",")
                    .append("\"destination\":\"").append(esc(m.getDestinationId())).append("\",")
                    .append("\"destName\":\"").append(esc(DEVICE_NAMES.getOrDefault(m.getDestinationId(), m.getDestinationId()))).append("\",")
                    .append("\"type\":\"").append(esc(m.getCategory().getLabel())).append("\",")
                    .append("\"priority\":\"").append(esc(m.getPriority().name())).append("\",")
                    .append("\"text\":\"").append(esc(m.getContent())).append("\",")
                    .append("\"status\":\"").append(esc(m.getStatus().name())).append("\",")
                    .append("\"isBroadcast\":").append(m.isBroadcast()).append(",")
                    .append("\"current\":\"").append(esc(m.getCurrentNode())).append("\",")
                    .append("\"currentName\":\"").append(esc(DEVICE_NAMES.getOrDefault(m.getCurrentNode(), m.getCurrentNode()))).append("\",")
                    .append("\"currentHop\":").append(m.getCurrentHop()).append(",")
                    .append("\"totalHops\":").append(m.getTotalHopsTraversed()).append(",")
                    .append("\"broadcastRecipients\":[");
            int bi = 0;
            for (String rn : m.getBroadcastReachedNodes()) {
                if (bi > 0) b.append(',');
                b.append('\"').append(esc(rn)).append('\"');
                bi++;
            }
            b.append("],\"route\":[");
            List<String> r = m.getCurrentRoute();
            for (int j = 0; j < r.size(); j++) {
                if (j > 0) b.append(',');
                b.append('\"').append(esc(r.get(j))).append('\"');
            }
            b.append("],\"note\":\"").append(esc(m.getNote())).append("\",")
                    .append("\"failureReason\":\"").append(esc(m.getFailureReason())).append("\"")
                    .append("}");
        }
        b.append("]");
        return b.toString();
    }

    private static boolean method(HttpExchange x, String expected) throws IOException {
        if (!x.getRequestMethod().equalsIgnoreCase(expected)) {
            respond(x, 405, "text/plain", "Method not allowed");
            return false;
        }
        return true;
    }

    private static String field(String json, String key, String fallback) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        Matcher boolMatcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false|[0-9.]+)").matcher(json);
        if (boolMatcher.find()) {
            return boolMatcher.group(1);
        }
        return fallback;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void respond(HttpExchange x, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        x.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        x.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        x.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        x.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = x.getResponseBody()) {
            out.write(bytes);
        }
    }
}
