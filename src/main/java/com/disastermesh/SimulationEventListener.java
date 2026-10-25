package com.disastermesh;

@FunctionalInterface
public interface SimulationEventListener {
    void onEvent(SimulationEvent event);
}
