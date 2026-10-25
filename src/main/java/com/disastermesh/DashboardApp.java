package com.disastermesh;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Desktop dashboard for the predictive disaster mesh simulation. */
public class DashboardApp {
    private final SimulationConfig config = SimulationConfig.defaultConfig();
    private List<NetworkNode> nodes;
    private SimulationEngine engine;
    private NetworkNode initialRelay;
    private Timer timer;

    private final JLabel relayValue = new JLabel("N1", SwingConstants.CENTER);
    private final JLabel batteryValue = new JLabel("90.0%", SwingConstants.CENTER);
    private final JLabel riskValue = new JLabel("0.000", SwingConstants.CENTER);
    private final JLabel handoverValue = new JLabel("0", SwingConstants.CENTER);
    private final JLabel statusValue = new JLabel("Ready", SwingConstants.CENTER);
    private final JLabel stepValue = new JLabel("Step 0 / " + config.totalSteps(), SwingConstants.CENTER);
    private final NetworkPanel networkPanel = new NetworkPanel();
    private final RiskPanel riskPanel = new RiskPanel();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Node", "Battery", "X", "Y", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable nodeTable = new JTable(tableModel);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DashboardApp().show());
    }

    private void show() {
        createSimulation();
        JFrame frame = new JFrame("Dual-Trajectory Predictive Relay Handover | Disaster Mesh");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1050, 700));
        frame.setLayout(new BorderLayout(12, 12));
        frame.getContentPane().setBackground(new Color(15, 23, 42));

        JLabel title = new JLabel("DISASTER MESH NETWORK DASHBOARD", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 23));
        title.setBorder(BorderFactory.createEmptyBorder(16, 10, 8, 10));
        frame.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 12, 12));
        center.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setOpaque(false);
        left.add(sectionTitle("LIVE NETWORK TOPOLOGY"), BorderLayout.NORTH);
        left.add(networkPanel, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setOpaque(false);
        right.add(sectionTitle("NODE STATUS"), BorderLayout.NORTH);
        nodeTable.setRowHeight(28);
        nodeTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        nodeTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        right.add(new JScrollPane(nodeTable), BorderLayout.CENTER);
        right.add(riskPanel, BorderLayout.SOUTH);
        center.add(left);
        center.add(right);
        frame.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setOpaque(false);
        JPanel metrics = new JPanel(new GridLayout(1, 5, 8, 8));
        metrics.setOpaque(false);
        metrics.add(metricCard("CURRENT RELAY", relayValue));
        metrics.add(metricCard("RELAY BATTERY", batteryValue));
        metrics.add(metricCard("CURRENT RISK", riskValue));
        metrics.add(metricCard("HANDOVERS", handoverValue));
        metrics.add(metricCard("STATUS", statusValue));
        bottom.add(metrics, BorderLayout.NORTH);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        JButton startButton = button("▶ Start");
        JButton pauseButton = button("⏸ Pause");
        JButton stepButton = button("Step +1");
        JButton resetButton = button("↻ Reset");
        startButton.addActionListener(e -> start());
        pauseButton.addActionListener(e -> pause());
        stepButton.addActionListener(e -> advanceOneStep());
        resetButton.addActionListener(e -> reset());
        controls.add(startButton);
        controls.add(pauseButton);
        controls.add(stepButton);
        controls.add(resetButton);
        controls.add(stepValue);
        bottom.add(controls, BorderLayout.SOUTH);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        frame.add(bottom, BorderLayout.SOUTH);

        timer = new Timer(500, e -> advanceOneStep());
        refreshDashboard(null);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(147, 197, 253));
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        return label;
    }

    private JPanel metricCard(String title, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(new Color(30, 41, 59));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                BorderFactory.createEmptyBorder(10, 8, 10, 8)));
        JLabel heading = new JLabel(title, SwingConstants.CENTER);
        heading.setForeground(new Color(148, 163, 184));
        heading.setFont(new Font("Segoe UI", Font.BOLD, 11));
        value.setForeground(Color.WHITE);
        value.setFont(new Font("Segoe UI", Font.BOLD, 20));
        card.add(heading, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setFocusPainted(false);
        return button;
    }

    private void createSimulation() {
        NetworkNode n1 = new NetworkNode("N1", 90, new Position(0, 0));
        NetworkNode n2 = new NetworkNode("N2", 85, new Position(20, 0));
        NetworkNode n3 = new NetworkNode("N3", 80, new Position(30, 10));
        NetworkNode n4 = new NetworkNode("N4", 75, new Position(15, 20));
        nodes = new ArrayList<>(List.of(n1, n2, n3, n4));
        initialRelay = n1;
        Map<String, Double> drains = Map.of("N1", 1.0, "N2", 0.3, "N3", 0.2, "N4", 0.4);
        Map<String, Velocity> velocities = Map.of(
                "N1", new Velocity(0.2, 0.0),
                "N2", new Velocity(0.1, 0.0),
                "N3", new Velocity(-0.2, 0.0),
                "N4", new Velocity(0.0, -0.1));
        engine = new SimulationEngine(config, nodes, drains, velocities, initialRelay);
    }

    private void start() {
        if (engine.getResults().size() >= config.totalSteps()) {
            JOptionPane.showMessageDialog(null, "Simulation is complete. Press Reset to run it again.");
            return;
        }
        timer.start();
        statusValue.setText("RUNNING");
    }

    private void pause() {
        if (timer != null) timer.stop();
        statusValue.setText("PAUSED");
    }

    private void advanceOneStep() {
        if (engine.getResults().size() >= config.totalSteps()) {
            pause();
            statusValue.setText("COMPLETED");
            return;
        }
        SimulationResult result = engine.step();
        refreshDashboard(result);
        if (engine.getResults().size() >= config.totalSteps()) {
            pause();
            statusValue.setText("COMPLETED");
        }
    }

    private void reset() {
        pause();
        createSimulation();
        tableModel.setRowCount(0);
        riskPanel.clear();
        refreshDashboard(null);
        statusValue.setText("READY");
    }

    private void refreshDashboard(SimulationResult latest) {
        NetworkNode relay = engine.getHandoverManager().getCurrentRelay();
        relayValue.setText(relay.getNodeId());
        batteryValue.setText(String.format("%.1f%%", relay.getBatteryLevel()));
        handoverValue.setText(String.valueOf(engine.getHandoverManager().getHandoverCount()));
        if (latest != null) {
            riskValue.setText(String.format("%.3f", latest.relayRisk()));
            stepValue.setText("Step " + (latest.step() + 1) + " / " + config.totalSteps());
            if ("HANDOVER".equals(latest.event())) statusValue.setText("HANDOVER!");
            else if ("NO_SAFE_RELAY".equals(latest.event())) statusValue.setText("NO SAFE RELAY");
            else if (!"RUNNING".equals(statusValue.getText())) statusValue.setText("MONITORING");
            riskPanel.addPoint(latest.relayRisk(), latest.step());
            if ("HANDOVER".equals(latest.event()) || "NO_SAFE_RELAY".equals(latest.event())) {
                tableModel.insertRow(0, new Object[]{latest.currentRelayId(),
                        String.format("%.1f%%", latest.relayBattery()), "—", "—", latest.event()});
                while (tableModel.getRowCount() > 8) tableModel.removeRow(tableModel.getRowCount() - 1);
            }
        } else {
            riskValue.setText("0.000");
            stepValue.setText("Step 0 / " + config.totalSteps());
        }
        networkPanel.repaint();
        refreshNodeTable();
    }

    private void refreshNodeTable() {
        // Rebuild live node rows without discarding the separate event history (shown in the console).
        nodeTable.setModel(new DefaultTableModel(new Object[]{"Node", "Battery", "X", "Y", "Status"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        DefaultTableModel model = (DefaultTableModel) nodeTable.getModel();
        String relayId = engine.getHandoverManager().getCurrentRelay().getNodeId();
        for (NetworkNode node : nodes) {
            String status = node.getNodeId().equals(relayId) ? "ACTIVE RELAY" : node.isActive() ? "AVAILABLE" : "OFFLINE";
            model.addRow(new Object[]{node.getNodeId(), String.format("%.1f%%", node.getBatteryLevel()),
                    String.format("%.1f", node.getPosition().x()), String.format("%.1f", node.getPosition().y()), status});
        }
    }

    private class NetworkPanel extends JPanel {
        NetworkPanel() {
            setPreferredSize(new Dimension(480, 350));
            setBackground(new Color(15, 23, 42));
            setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            double minX = -15, maxX = 65, minY = -20, maxY = 35;
            java.util.function.Function<Position, int[]> point = p -> new int[]{
                    (int) (35 + (p.x() - minX) / (maxX - minX) * (w - 70)),
                    (int) (25 + (maxY - p.y()) / (maxY - minY) * (h - 50))};
            g.setColor(new Color(30, 41, 59));
            for (int x = 35; x < w - 20; x += 30) g.drawLine(x, 15, x, h - 15);
            for (int y = 20; y < h - 10; y += 30) g.drawLine(15, y, w - 15, y);

            NetworkNode relay = engine.getHandoverManager().getCurrentRelay();
            int[] rp = point.apply(relay.getPosition());
            int range = (int) (config.communicationRange() / (maxX - minX) * (w - 70));
            g.setColor(new Color(34, 197, 94, 35));
            g.fillOval(rp[0] - range, rp[1] - range, range * 2, range * 2);
            g.setColor(new Color(34, 197, 94, 150));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 5f}, 0f));
            g.drawOval(rp[0] - range, rp[1] - range, range * 2, range * 2);
            g.setStroke(new BasicStroke(1.5f));

            for (NetworkNode node : nodes) {
                int[] p = point.apply(node.getPosition());
                if (!node.getNodeId().equals(relay.getNodeId())) {
                    double d = node.getPosition().distanceTo(relay.getPosition());
                    g.setColor(d <= config.communicationRange() ? new Color(71, 85, 105) : new Color(239, 68, 68));
                    g.drawLine(rp[0], rp[1], p[0], p[1]);
                }
            }
            for (NetworkNode node : nodes) {
                int[] p = point.apply(node.getPosition());
                boolean isRelay = node.getNodeId().equals(relay.getNodeId());
                Color fill = !node.isActive() ? new Color(100, 116, 139)
                        : isRelay ? new Color(34, 197, 94)
                        : node.getBatteryLevel() <= config.minimumRelayBattery() ? new Color(239, 68, 68)
                        : new Color(59, 130, 246);
                g.setColor(fill);
                g.fillOval(p[0] - 15, p[1] - 15, 30, 30);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g.drawString(node.getNodeId(), p[0] - 8, p[1] + 4);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g.drawString(String.format("%.0f%%", node.getBatteryLevel()), p[0] - 17, p[1] + 30);
            }
            g.setColor(new Color(148, 163, 184));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g.drawString("Green = active relay | Blue = candidate | Red = low battery/out of range", 12, h - 8);
            g.dispose();
        }
    }

    private class RiskPanel extends JPanel {
        private final List<Double> values = new ArrayList<>();
        RiskPanel() {
            setPreferredSize(new Dimension(450, 150));
            setBackground(new Color(30, 41, 59));
            setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)),
                    "RELAY RISK HISTORY", 0, 0, new Font("Segoe UI", Font.BOLD, 12), new Color(147, 197, 253)));
        }
        void addPoint(double value, int step) { values.add(Math.max(0, Math.min(1, value))); repaint(); }
        void clear() { values.clear(); repaint(); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 35, right = getWidth() - 15, top = 25, bottom = getHeight() - 25;
            g.setColor(new Color(71, 85, 105));
            g.drawLine(left, bottom, right, bottom);
            g.drawLine(left, top, left, bottom);
            g.setColor(new Color(148, 163, 184));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g.drawString("1.0", 7, top + 4); g.drawString("0.0", 7, bottom + 4);
            int thresholdY = bottom - (int) (0.4 * (bottom - top));
            g.setColor(new Color(245, 158, 11));
            g.drawLine(left, thresholdY, right, thresholdY);
            g.drawString("threshold 0.40", Math.max(left + 5, right - 90), thresholdY - 4);
            if (values.size() > 1) {
                g.setColor(new Color(56, 189, 248));
                g.setStroke(new BasicStroke(2f));
                for (int i = 1; i < values.size(); i++) {
                    int x1 = left + (i - 1) * (right - left) / Math.max(1, values.size() - 1);
                    int x2 = left + i * (right - left) / Math.max(1, values.size() - 1);
                    int y1 = bottom - (int) (values.get(i - 1) * (bottom - top));
                    int y2 = bottom - (int) (values.get(i) * (bottom - top));
                    g.drawLine(x1, y1, x2, y2);
                }
            }
            g.dispose();
        }
    }
}
