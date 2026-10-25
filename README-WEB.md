# Disaster Mesh Emergency Messaging Web App

## How to Run locally
1. Ensure Java 17+ and Maven are installed.
2. Open Windows PowerShell in this folder (containing `pom.xml`).
3. Compile and launch:
   ```powershell
   mvn clean compile
   mvn exec:java
   ```
4. Open **`http://localhost:8080`** in any web browser.

## Features & Improvements
- **Tactical Emergency HUD**: Dark obsidian/slate interface inspired by military and NASA telemetry consoles; avoids cookie-cutter generic styling.
- **Dynamic Topology & RF Range Propagation**: Live SVG map with dynamic link calculation, active packet route highlighting, and 38m RF range coverage circles.
- **Dual-Trajectory Predictive Handover**: Real-time evaluation of battery depletion rate and relative mobility velocity vectors.
- **Pluggable Strategy Selector**: Toggle live between Proposed Dual-Trajectory, Shortest-Path Baseline, Battery-Only, and Mobility-Only algorithms.
- **Multi-Hop Packet Lifecycle**: Honest state machine (`CREATED -> QUEUED -> IN_TRANSIT -> REROUTING -> DELIVERED / FAILED`) with ACK confirmation and route repair.
- **Scientific Benchmark Runner**: Execute 100-step comparative experiments with 1-click CSV and JSON export for research evaluation.
- **Offline Radio Transport Abstraction**: Clean decoupling of simulated RF propagation from physical hardware specs (Wi-Fi Direct, BLE Mesh, LoRa).
