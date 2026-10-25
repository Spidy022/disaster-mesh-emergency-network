# Predictive Disaster Mesh Network - Improved Version

## What changed
- Prediction times are normalized before risk calculation: battery and mobility predictions are calculated in seconds and converted to minutes for `RiskCalculator`.
- Mobility prediction supports relative motion between the candidate relay and the current relay.
- The simulation runs for 300 steps so repeated handovers can be observed.
- The default prediction horizon is 2 minutes, with a handover risk threshold of 0.40.
- Relay selection rejects candidates at or below the minimum battery threshold.
- Simulation results identify `NO_SAFE_RELAY` when risk is high but no eligible replacement exists.
- Added a unit test for relative-motion disconnect prediction.

## Run in Windows PowerShell
Open the project folder (the folder containing `pom.xml`) and run:

```powershell
mvn clean test
mvn exec:java
```

Expected behavior with the default sample scenario:
- N1 hands over to N2.
- Later, N2 hands over to N3.
- Toward the end, the simulation may report `NO_SAFE_RELAY` because the sample nodes have moved out of range or their batteries are unsuitable. This is an explicit warning, not a successful handover.

## Notes
The simulation uses constant node velocities and constant battery drain rates. It is a simplified simulation model, not a real Wi-Fi measurement. The current model does not include a separate destination/base-station node, so current-relay mobility risk is not calculated against a destination; relative mobility is used to score candidate relays against the current relay.
