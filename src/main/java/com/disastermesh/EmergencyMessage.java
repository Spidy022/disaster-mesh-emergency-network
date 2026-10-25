package com.disastermesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an emergency message propagating through the disaster mesh network.
 * Tracks full route history, hops, handovers, status transitions, pacing progress,
 * and 360-degree all-direction natural disaster broadcast flood distribution.
 */
public class EmergencyMessage implements Comparable<EmergencyMessage> {

    private final long messageId;
    private final String senderId;
    private final String destinationId;
    private final String content;
    private final EmergencyCategory category;
    private final EmergencyPriority priority;
    private final long createdAtMillis;
    private final int createdStep;
    private final boolean broadcast;

    private MessageStatus status;
    private String currentNode;
    private int currentHop;
    private List<String> currentRoute;
    private final List<List<String>> routeHistory;
    private final List<String> handoverHistory;
    private final Set<String> broadcastReachedNodes;
    private int totalHopsTraversed;
    private Double deliveredAtSeconds;
    private String failureReason;
    private String note;

    // Pacing progress: ticks required per hop (4 in Normal, 2 in Fast)
    private int ticksPerHop = 4;
    private int ticksInCurrentHop = 0;

    public EmergencyMessage(
            long messageId,
            String senderId,
            String destinationId,
            String content,
            EmergencyCategory category,
            EmergencyPriority priority,
            int createdStep) {

        this(messageId, senderId, destinationId, content, category, priority, createdStep, false);
    }

    public EmergencyMessage(
            long messageId,
            String senderId,
            String destinationId,
            String content,
            EmergencyCategory category,
            EmergencyPriority priority,
            int createdStep,
            boolean broadcast) {

        if (senderId == null || senderId.isBlank()) {
            throw new IllegalArgumentException("Sender ID cannot be empty.");
        }
        if (!broadcast && (destinationId == null || destinationId.isBlank())) {
            throw new IllegalArgumentException("Destination ID cannot be empty for unicast.");
        }
        if (!broadcast && senderId.equals(destinationId)) {
            throw new IllegalArgumentException("Sender and destination must be distinct.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty.");
        }

        this.messageId = messageId;
        this.senderId = senderId;
        this.destinationId = broadcast ? "ALL_SURROUNDING" : destinationId;
        this.content = content.trim();
        this.category = category != null ? category : EmergencyCategory.GENERAL_SOS;
        this.priority = priority != null ? priority : EmergencyPriority.NORMAL;
        this.createdAtMillis = System.currentTimeMillis();
        this.createdStep = createdStep;
        this.broadcast = broadcast;

        this.status = MessageStatus.CREATED;
        this.currentNode = senderId;
        this.currentHop = 0;
        this.currentRoute = new ArrayList<>();
        this.routeHistory = new ArrayList<>();
        this.handoverHistory = new ArrayList<>();
        this.broadcastReachedNodes = new HashSet<>();
        if (broadcast) {
            this.broadcastReachedNodes.add(senderId);
        }
        this.totalHopsTraversed = 0;
        this.deliveredAtSeconds = null;
        this.failureReason = null;
        this.note = broadcast
                ? "360° Life-Safety Disaster Broadcast Initiated at " + senderId
                : "Emergency payload created at " + senderId;
    }

    public void queue() {
        this.status = MessageStatus.QUEUED;
        this.note = "Queued in outgoing radio buffer at " + currentNode;
    }

    public void startTransit(List<String> route) {
        if (route == null || route.isEmpty()) {
            fail("No route available from " + senderId + " to " + destinationId);
            return;
        }
        this.currentRoute = new ArrayList<>(route);
        this.routeHistory.add(new ArrayList<>(route));
        this.currentHop = 0;
        this.currentNode = route.get(0);
        this.status = MessageStatus.IN_TRANSIT;
        this.ticksInCurrentHop = 0;
        this.note = "Route selected (" + String.join(" -> ", route) + "). Radio transmission initiating.";
    }

    /**
     * Increments the transmission progress within the current hop.
     * Returns true when the required ticksPerHop (e.g., 2 in Fast, 4 in Normal)
     * are reached and the packet is ready to step into the next node.
     */
    public boolean advanceHopProgress() {
        ticksInCurrentHop++;
        if (ticksInCurrentHop >= ticksPerHop) {
            ticksInCurrentHop = 0;
            return true;
        }
        return false;
    }

    public double getHopProgressRatio() {
        if (ticksPerHop <= 0) return 1.0;
        return Math.min(1.0, (double) ticksInCurrentHop / ticksPerHop);
    }

    public void advanceHop(String nextHopNode) {
        this.currentHop++;
        this.currentNode = nextHopNode;
        this.totalHopsTraversed++;
        this.ticksInCurrentHop = 0;

        if (this.currentNode.equals(this.destinationId)) {
            this.status = MessageStatus.DELIVERED;
            this.note = "Destination node " + destinationId + " acknowledged payload.";
        } else {
            this.status = MessageStatus.IN_TRANSIT;
            this.note = "Arrived at node " + nextHopNode + " (Hop " + currentHop + " of " + (currentRoute.size() - 1) + "). Next transit starting.";
        }
    }

    public void recordHandover(String oldRelay, String newRelay, List<String> reroutedPath) {
        String event = "Handover: " + oldRelay + " -> " + newRelay;
        this.handoverHistory.add(event);

        if (reroutedPath != null && !reroutedPath.isEmpty()) {
            this.currentRoute = new ArrayList<>(reroutedPath);
            this.routeHistory.add(new ArrayList<>(reroutedPath));
            this.currentHop = 0;
            this.ticksInCurrentHop = 0;
            this.status = MessageStatus.REROUTING;
            this.note = "Rerouted via new relay " + newRelay + " (" + String.join(" -> ", reroutedPath) + ")";
        }
    }

    public void addBroadcastRecipient(String nodeId) {
        this.broadcastReachedNodes.add(nodeId);
    }

    public Set<String> getBroadcastReachedNodes() {
        return Collections.unmodifiableSet(broadcastReachedNodes);
    }

    public void fail(String reason) {
        this.status = MessageStatus.FAILED;
        this.failureReason = reason;
        this.note = "Delivery failed: " + reason;
    }

    public void deliver(double simTimeSeconds) {
        this.status = MessageStatus.DELIVERED;
        this.deliveredAtSeconds = simTimeSeconds;
        this.note = broadcast
                ? "Disaster broadcast delivered to " + broadcastReachedNodes.size() + " surrounding mesh nodes."
                : "Destination " + destinationId + " acknowledged emergency payload.";
    }

    public boolean isTerminal() {
        return status == MessageStatus.DELIVERED || status == MessageStatus.FAILED;
    }

    @Override
    public int compareTo(EmergencyMessage other) {
        int pCompare = Integer.compare(this.priority.getLevel(), other.priority.getLevel());
        if (pCompare != 0) return pCompare;
        return Long.compare(this.messageId, other.messageId);
    }

    // Getters and Setters
    public long getMessageId() { return messageId; }
    public String getSenderId() { return senderId; }
    public String getDestinationId() { return destinationId; }
    public String getContent() { return content; }
    public EmergencyCategory getCategory() { return category; }
    public EmergencyPriority getPriority() { return priority; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public int getCreatedStep() { return createdStep; }
    public boolean isBroadcast() { return broadcast; }
    public MessageStatus getStatus() { return status; }
    public String getCurrentNode() { return currentNode; }
    public int getCurrentHop() { return currentHop; }
    public List<String> getCurrentRoute() { return Collections.unmodifiableList(currentRoute); }
    public List<List<String>> getRouteHistory() { return Collections.unmodifiableList(routeHistory); }
    public List<String> getHandoverHistory() { return Collections.unmodifiableList(handoverHistory); }
    public int getTotalHopsTraversed() { return totalHopsTraversed; }
    public Double getDeliveredAtSeconds() { return deliveredAtSeconds; }
    public String getFailureReason() { return failureReason; }
    public String getNote() { return note; }
    public int getTicksPerHop() { return ticksPerHop; }
    public int getTicksInCurrentHop() { return ticksInCurrentHop; }

    public void setTicksPerHop(int ticks) { this.ticksPerHop = Math.max(1, ticks); }
    public void setNote(String note) { this.note = note; }
    public void setStatus(MessageStatus status) { this.status = status; }
    public void setFailureReason(String reason) { this.failureReason = reason; }
    public void setDeliveredAtSeconds(Double s) { this.deliveredAtSeconds = s; }
}
